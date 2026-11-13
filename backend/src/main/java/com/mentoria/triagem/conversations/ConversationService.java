package com.mentoria.triagem.conversations;

import com.mentoria.triagem.conversations.internal.Conversation;
import com.mentoria.triagem.conversations.internal.ConversationStore;
import com.mentoria.triagem.conversations.internal.ConversationTransactions;
import com.mentoria.triagem.conversations.internal.JudgeVerdictRecord;
import com.mentoria.triagem.identity.MembershipService;
import com.mentoria.triagem.requests.RequestService;
import com.mentoria.triagem.requests.RequestView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * API publica do modulo conversations (Spec 003).
 *
 * <p>Regras centrais implementadas aqui: o autor e o unico que escreve e o unico que pode
 * declarar o problema resolvido; o JEV so pode continuar, propor confirmacao ou escalar; e
 * qualquer falha, limite ou duvida termina em escalonamento para uma pessoa.
 *
 * <p>A resposta do assistente e sincrona (o usuario espera por ela) e a avaliacao do JEV e
 * assincrona: o turno do assistente grava, na mesma transacao, a intencao de avaliar
 * ({@code ConversationEvaluationRequested.v1}), e o consumidor chama
 * {@link #applyJudgeVerdict(String, String, int)}.
 */
@Service
public class ConversationService {

  private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
  private static final int MAX_JUDGE_SUMMARY = 500;
  private static final String[] SECRET_WORDS = {
    "senha", "password", "token", "chave privada", "secret", "credencial"
  };

  private final ConversationStore store;
  private final MembershipService memberships;
  private final RequestService requests;
  private final ChatProvider chatProvider;
  private final ConversationJudge judge;
  private final ConversationLimits limits;
  private final ConversationTransactions tx;
  private final Clock clock;

  public ConversationService(
      ConversationStore store,
      MembershipService memberships,
      RequestService requests,
      ChatProvider chatProvider,
      ConversationJudge judge,
      ConversationLimits limits,
      ConversationTransactions tx,
      Clock clock) {
    this.store = store;
    this.memberships = memberships;
    this.requests = requests;
    this.chatProvider = chatProvider;
    this.judge = judge;
    this.limits = limits;
    this.tx = tx;
    this.clock = clock;
  }

  // --- comandos do autor ------------------------------------------------------------------

  /** Abre conversa com a primeira mensagem (FR3-001). */
  public ConversationView open(
      String workspaceId, String subject, String text, String idempotencyKey) {
    memberships.requireMember(workspaceId, subject);
    validateMessage(text);

    String scopedKey = scopedKey(workspaceId, subject, "conversations.open", idempotencyKey);
    Optional<String> existing = store.findIdempotentConversation(scopedKey);
    if (existing.isPresent()) {
      return view(require(workspaceId, existing.get()));
    }

    if (store.countActiveByAuthor(workspaceId, subject) >= limits.maxActiveConversations()) {
      throw new ConversationStateException(
          "ACTIVE_CONVERSATION_LIMIT", "limite de conversas ativas atingido");
    }

    Conversation conversation =
        new Conversation(
            UUID.randomUUID().toString(), workspaceId, subject, limits.maxAssistantTurns(), now());
    tx.createConversation(conversation, scopedKey, text.trim());

    generateAssistantTurn(conversation, text.trim());
    return view(conversation);
  }

  /** Envia mensagem (FR3-002). Em AWAITING_CONFIRMATION, equivale a seguir conversando. */
  public ConversationView sendMessage(
      String workspaceId, String subject, String conversationId, long expectedVersion, String text) {
    memberships.requireMember(workspaceId, subject);
    validateMessage(text);
    Conversation conversation = require(workspaceId, conversationId);
    conversation.requireAuthor(subject);
    conversation.requireVersion(expectedVersion);

    if (conversation.turnLimitReached()) {
      return escalate(conversation, EscalationReason.TURN_LIMIT_REACHED, null);
    }

    tx.appendAuthorTurn(conversation, text.trim());
    generateAssistantTurn(conversation, text.trim());
    return view(conversation);
  }

  /**
   * Pedido de pessoa humana. Prevalece sobre qualquer veredito e nao consulta o JEV
   * (FR3-004, BR3-006). Em CLOSED_INACTIVE, vale dentro da janela de reabertura (FR3-006).
   */
  public ConversationView requestHuman(
      String workspaceId, String subject, String conversationId, long expectedVersion, String reason) {
    var membership = memberships.requireMember(workspaceId, subject);
    Conversation conversation = require(workspaceId, conversationId);

    boolean isAuthor = conversation.authorSubject().equals(subject);
    if (!isAuthor) {
      if (!membership.role().isAgentOrAdmin()) {
        throw new ConversationNotFoundException("conversa nao encontrada");
      }
      if (reason == null || reason.isBlank()) {
        throw new IllegalArgumentException("escalonamento por agent/admin exige motivo");
      }
    }
    conversation.requireVersion(expectedVersion);

    if (conversation.status() == ConversationStatus.CLOSED_INACTIVE) {
      if (!isAuthor) {
        throw new ConversationStateException(
            "CONVERSATION_NOT_ACTIVE", "conversa encerrada por inatividade");
      }
      Instant deadline = conversation.closedInactiveAt().plus(Duration.ofDays(limits.reopenDays()));
      if (now().isAfter(deadline)) {
        throw new ConversationStateException(
            "REOPEN_WINDOW_EXPIRED", "janela de reabertura expirada");
      }
    }

    String escalationReason =
        isAuthor ? EscalationReason.REQUESTER_ASKED_HUMAN : EscalationReason.ADMIN_FORCED;
    return escalate(conversation, escalationReason, null);
  }

  /** Somente o autor confirma ou nega a resolucao (FR3-005, BR3-005). */
  public ConversationView confirmResolution(
      String workspaceId, String subject, String conversationId, long expectedVersion, boolean resolved) {
    memberships.requireMember(workspaceId, subject);
    Conversation conversation = require(workspaceId, conversationId);
    conversation.requireAuthor(subject);
    conversation.requireVersion(expectedVersion);

    if (resolved) {
      conversation.confirmResolved();
      tx.saveState(conversation);
      return view(conversation);
    }

    if (conversation.turnLimitReached()) {
      return escalate(conversation, EscalationReason.USER_REPORTED_STILL_BROKEN, null);
    }
    conversation.backToActive();
    tx.saveState(conversation);
    return view(conversation);
  }

  // --- leitura ----------------------------------------------------------------------------

  /** Detalhe da conversa: somente o autor (BR3-010). */
  public ConversationView get(String workspaceId, String subject, String conversationId) {
    memberships.requireMember(workspaceId, subject);
    Conversation conversation = require(workspaceId, conversationId);
    conversation.requireAuthor(subject);
    return view(conversation);
  }

  public List<ConversationView> listForAuthor(String workspaceId, String subject) {
    memberships.requireMember(workspaceId, subject);
    return store.findByAuthor(workspaceId, subject).stream().map(this::view).toList();
  }

  /**
   * Leitura pelo tecnico: apenas agent/admin, apenas pelo chamado vinculado e apenas depois
   * do escalonamento (FR3-018). Cada leitura e registrada.
   */
  public ConversationView readForRequest(String workspaceId, String subject, String requestId) {
    memberships.requireAgentOrAdmin(workspaceId, subject);
    RequestView request = requests.get(workspaceId, subject, requestId);
    if (request.conversationId() == null) {
      throw new ConversationNotFoundException("chamado sem conversa vinculada");
    }
    Conversation conversation = require(workspaceId, request.conversationId());
    if (conversation.status() != ConversationStatus.ESCALATED) {
      throw new ConversationNotFoundException("conversa nao encontrada");
    }
    store.recordAccess(workspaceId, conversation.id(), subject, now());
    log.info(
        "transcript_access conversationId={} requestId={} actor={}",
        conversation.id(),
        requestId,
        subject);
    return view(conversation);
  }

  /** Auditoria de acesso a transcricao (FR3-018). */
  public List<TranscriptAccess> accessLog(
      String workspaceId, String subject, String conversationId) {
    memberships.requireAgentOrAdmin(workspaceId, subject);
    require(workspaceId, conversationId);
    return store.accessLog(conversationId);
  }

  // --- avaliacao pelo JEV -----------------------------------------------------------------

  /**
   * Aplica o veredito do JEV ao turno indicado. Chamado pelo consumidor do evento.
   *
   * <p>Veredito de turno superado, ou de conversa que ja saiu da mao da IA, fica OBSOLETE e
   * nao altera estado (FR3-011). E isso que faz o pedido de humano do autor vencer uma
   * avaliacao que estava em andamento.
   */
  public ConversationView applyJudgeVerdict(
      String workspaceId, String conversationId, int turnSequence) {
    Conversation conversation = require(workspaceId, conversationId);

    if (conversation.status() != ConversationStatus.ACTIVE
        || conversation.lastAssistantSequence() != turnSequence) {
      tx.recordVerdict(
          new JudgeVerdictRecord(
              conversationId,
              workspaceId,
              turnSequence,
              null,
              VerdictState.OBSOLETE,
              "turno superado",
              now()));
      return view(conversation);
    }

    JudgeOutcome outcome;
    try {
      outcome = judge.evaluate(conversation.turnViews());
    } catch (RuntimeException failure) {
      tx.recordVerdict(
          new JudgeVerdictRecord(
              conversationId,
              workspaceId,
              turnSequence,
              null,
              VerdictState.FAILED,
              failure.getMessage(),
              now()));
      return escalate(conversation, EscalationReason.JUDGE_UNAVAILABLE, null);
    }

    String validationError = validateVerdict(outcome);
    if (validationError != null) {
      tx.recordVerdict(
          new JudgeVerdictRecord(
              conversationId,
              workspaceId,
              turnSequence,
              outcome,
              VerdictState.REJECTED_INVALID,
              validationError,
              now()));
      return escalate(conversation, EscalationReason.JUDGE_INVALID_OUTPUT, null);
    }

    tx.recordVerdict(
        new JudgeVerdictRecord(
            conversationId, workspaceId, turnSequence, outcome, VerdictState.APPLIED, null, now()));

    return switch (outcome.verdict()) {
      case ESCALATE -> escalate(conversation, EscalationReason.JUDGE_ESCALATED, outcome);
      case LIKELY_RESOLVED -> applyLikelyResolved(conversation, outcome);
      case CONTINUE -> continueOrEscalateOnLimit(conversation);
    };
  }

  /**
   * Confianca abaixo do limiar nao vale como resolucao: a duvida favorece a pessoa
   * (FR3-010). O veredito, por si, nunca encerra a conversa (FR3-013).
   */
  private ConversationView applyLikelyResolved(Conversation conversation, JudgeOutcome outcome) {
    if (outcome.confidence() < limits.resolvedConfidenceThreshold()) {
      return continueOrEscalateOnLimit(conversation);
    }
    conversation.awaitConfirmation();
    tx.saveState(conversation);
    return view(conversation);
  }

  private ConversationView continueOrEscalateOnLimit(Conversation conversation) {
    if (conversation.turnLimitReached()) {
      return escalate(conversation, EscalationReason.TURN_LIMIT_REACHED, null);
    }
    return view(conversation);
  }

  /** @return null quando a saida e valida; caso contrario, o motivo da rejeicao (FR3-009). */
  String validateVerdict(JudgeOutcome outcome) {
    if (outcome == null || outcome.verdict() == null || outcome.reasonCode() == null) {
      return "veredito ou motivo ausente";
    }
    if (outcome.confidence() < 0.0 || outcome.confidence() > 1.0) {
      return "confianca fora de [0,1]";
    }
    if (outcome.summary() != null && outcome.summary().length() > MAX_JUDGE_SUMMARY) {
      return "resumo acima do limite";
    }
    if (outcome.summary() != null && asksForSecret(outcome.summary())) {
      return "resumo pede credencial";
    }
    if (outcome.verdict() == Verdict.ESCALATE) {
      if (outcome.suggestedCategory() == null
          || outcome.suggestedUrgency() == null
          || outcome.summary() == null
          || outcome.summary().isBlank()) {
        return "escalonamento exige categoria, urgencia e resumo";
      }
    }
    return null;
  }

  // --- escalonamento ----------------------------------------------------------------------

  private ConversationView escalate(
      Conversation conversation, String reason, JudgeOutcome outcome) {
    if (conversation.status() == ConversationStatus.ESCALATED) {
      throw new ConversationStateException("ALREADY_ESCALATED", "conversa ja escalada");
    }
    tx.escalate(conversation, reason, outcome);
    return view(conversation);
  }

  // --- inatividade ------------------------------------------------------------------------

  /**
   * Encerra conversas inativas (FR3-006). Idempotente e reversivel: nada e marcado como
   * resolvido. Nenhum agendador chama este metodo ainda.
   */
  public int closeInactiveConversations() {
    Instant cutoff = now().minus(Duration.ofHours(limits.inactivityHours()));
    int closed = 0;
    for (Conversation conversation : store.findInactive(cutoff)) {
      conversation.closeInactive(now());
      tx.saveState(conversation);
      closed++;
    }
    return closed;
  }

  // --- apoio ------------------------------------------------------------------------------

  /**
   * Chama o provedor de conversa <b>fora</b> de transacao e grava o turno resultante. Saida
   * invalida, pedido de credencial, falha e timeout ambiguo tem tratamento distinto
   * (FR3-019, FR3-020).
   */
  private void generateAssistantTurn(Conversation conversation, String latestMessage) {
    TurnState state;
    String reply = null;
    try {
      String candidate = chatProvider.reply(conversation.turnViews(), latestMessage);
      if (candidate == null
          || candidate.isBlank()
          || candidate.length() > limits.maxMessageLength()
          || asksForSecret(candidate)) {
        state = TurnState.FAILED;
      } else {
        reply = candidate;
        state = TurnState.GENERATED;
      }
    } catch (ChatProviderException failure) {
      state = failure.outcomeUnknown() ? TurnState.OUTCOME_UNKNOWN : TurnState.FAILED;
    } catch (RuntimeException failure) {
      state = TurnState.FAILED;
    }
    tx.appendAssistantTurn(conversation, reply, state);
  }

  private void validateMessage(String text) {
    if (text == null || text.trim().isEmpty()) {
      throw new IllegalArgumentException("mensagem obrigatoria");
    }
    if (text.trim().length() > limits.maxMessageLength()) {
      throw new IllegalArgumentException(
          "mensagem acima do limite de " + limits.maxMessageLength() + " caracteres");
    }
  }

  private static boolean asksForSecret(String text) {
    String normalized = text.toLowerCase(Locale.ROOT);
    for (String word : SECRET_WORDS) {
      if (normalized.contains(word)) {
        return true;
      }
    }
    return false;
  }

  private static String scopedKey(
      String workspaceId, String subject, String operation, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key obrigatoria");
    }
    return workspaceId
        + "|"
        + subject
        + "|"
        + operation
        + "|"
        + Integer.toHexString(idempotencyKey.hashCode());
  }

  private Conversation require(String workspaceId, String conversationId) {
    return store
        .find(workspaceId, conversationId)
        .orElseThrow(() -> new ConversationNotFoundException("conversa nao encontrada"));
  }

  private Instant now() {
    return clock.instant();
  }

  private ConversationView view(Conversation conversation) {
    return new ConversationView(
        conversation.id(),
        conversation.workspaceId(),
        conversation.authorSubject(),
        conversation.status(),
        conversation.version(),
        conversation.assistantTurnCount(),
        conversation.turnsRemaining(),
        conversation.escalatedRequestId(),
        conversation.escalationReason(),
        conversation.status() == ConversationStatus.AWAITING_CONFIRMATION,
        conversation.turnViews(),
        conversation.createdAt(),
        conversation.lastAuthorMessageAt());
  }
}
