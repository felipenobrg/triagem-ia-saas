package com.mentoria.triagem.conversations.internal;

import com.mentoria.triagem.conversations.JudgeOutcome;
import com.mentoria.triagem.conversations.TurnAuthor;
import com.mentoria.triagem.conversations.TurnState;
import com.mentoria.triagem.conversations.TurnView;
import com.mentoria.triagem.messaging.EventPublisher;
import com.mentoria.triagem.messaging.EventTypes;
import com.mentoria.triagem.messaging.OutboxEvent;
import com.mentoria.triagem.requests.ProblemContext;
import com.mentoria.triagem.requests.RequestService;
import com.mentoria.triagem.requests.RequestView;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fronteiras transacionais do modulo conversations.
 *
 * <p>Existe para manter as chamadas de rede (provedor de IA) <b>fora</b> da transacao e, ao
 * mesmo tempo, gravar estado e intencao de publicar evento <b>dentro</b> dela (FR-018).
 */
@Component
public class ConversationTransactions {

  private static final Logger log = LoggerFactory.getLogger(ConversationTransactions.class);

  private final ConversationStore store;
  private final RequestService requests;
  private final EventPublisher publisher;
  private final Clock clock;

  public ConversationTransactions(
      ConversationStore store, RequestService requests, EventPublisher publisher, Clock clock) {
    this.store = store;
    this.requests = requests;
    this.publisher = publisher;
    this.clock = clock;
  }

  /**
   * Grava o turno do assistente e, na mesma transacao, a intencao de pedir avaliacao ao JEV
   * (FR3-008). A avaliacao acontece depois, no consumidor: a conversa segue utilizavel
   * enquanto o veredito nao chega.
   */
  @Transactional
  public void appendAssistantTurn(Conversation conversation, String text, TurnState state) {
    conversation.addAssistantTurn(text, state, clock.instant());
    store.save(conversation);

    if (state == TurnState.GENERATED) {
      publisher.publish(
          OutboxEvent.of(
              EventTypes.CONVERSATION_EVALUATION_REQUESTED,
              conversation.workspaceId(),
              conversation.id(),
              Map.of(
                  "conversationId", conversation.id(),
                  "turnSequence", String.valueOf(conversation.lastAssistantSequence())),
              clock.instant()));
    }
  }

  /** Grava o turno do autor. */
  @Transactional
  public void appendAuthorTurn(Conversation conversation, String text) {
    conversation.addAuthorTurn(text, clock.instant());
    store.save(conversation);
  }

  /** Cria a conversa, o primeiro turno e a chave idempotente na mesma transacao (FR3-001). */
  @Transactional
  public void createConversation(Conversation conversation, String scopedKey, String firstMessage) {
    conversation.addAuthorTurn(firstMessage, clock.instant());
    store.save(conversation);
    store.rememberIdempotency(scopedKey, conversation.id());
  }

  @Transactional
  public void saveState(Conversation conversation) {
    store.save(conversation);
  }

  /**
   * Escalonamento: chamado, contexto do problema, vinculo, estado da conversa e evento em uma
   * unica transacao (FR3-014). Falha em qualquer parte nao cria chamado nem muda a conversa.
   */
  @Transactional
  public String escalate(Conversation conversation, String reason, JudgeOutcome outcome) {
    ProblemContext context = buildProblemContext(conversation, reason, outcome);
    RequestView request =
        requests.createFromConversation(
            conversation.workspaceId(),
            conversation.authorSubject(),
            conversation.id(),
            conversation.firstAuthorMessage(),
            context);

    conversation.markEscalated(request.requestId(), reason);
    store.save(conversation);

    // ConversationEscalated.v1 esta no contrato da Spec 003, mas nao e publicado nesta fatia:
    // nenhum consumidor existe ainda (notificacao e metrica estao fora de escopo). Publicar
    // sem binding deixaria a mensagem voltando pelo basic.return e acumulando na outbox.
    log.info(
        "conversation_escalated conversationId={} requestId={} reason={}",
        conversation.id(),
        request.requestId(),
        reason);
    return request.requestId();
  }

  @Transactional
  public void recordVerdict(JudgeVerdictRecord verdict) {
    store.recordVerdict(verdict);
  }

  /**
   * Monta o contexto do problema a partir da conversa. Sem resumo e sugestoes confiaveis, o
   * contexto vai marcado como nao gerado: o chamado entra sem sugestao e o tecnico le a
   * conversa (FR3-015).
   */
  private ProblemContext buildProblemContext(
      Conversation conversation, String reason, JudgeOutcome outcome) {
    List<TurnView> turns = conversation.turnViews();
    List<String> symptoms =
        turns.stream()
            .filter(turn -> turn.author() == TurnAuthor.REQUESTER)
            .map(TurnView::text)
            .toList();
    List<String> attempted =
        turns.stream()
            .filter(turn -> turn.author() == TurnAuthor.ASSISTANT)
            .filter(turn -> turn.state() == TurnState.GENERATED)
            .map(TurnView::text)
            .toList();

    if (outcome == null
        || outcome.summary() == null
        || outcome.suggestedCategory() == null
        || outcome.suggestedUrgency() == null) {
      return new ProblemContext(null, symptoms, attempted, null, null, reason, false);
    }
    return new ProblemContext(
        outcome.summary(),
        symptoms,
        attempted,
        outcome.suggestedCategory(),
        outcome.suggestedUrgency(),
        reason,
        true);
  }
}
