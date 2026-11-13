package com.mentoria.triagem.conversations.internal;

import com.mentoria.triagem.conversations.ConversationStateException;
import com.mentoria.triagem.conversations.ConversationStatus;
import com.mentoria.triagem.conversations.TurnAuthor;
import com.mentoria.triagem.conversations.TurnState;
import com.mentoria.triagem.conversations.TurnView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agregado da conversa. Concentra as invariantes da Spec 003: turnos imutaveis e ordenados,
 * limite de turnos, autoria exclusiva do solicitante e a regra central de que nada e
 * encerrado como resolvido sem ato do autor (BR3-005, FR3-013).
 */
public class Conversation {

  private final String id;
  private final String workspaceId;
  private final String authorSubject;
  private final Instant createdAt;
  private final int maxAssistantTurns;
  private final List<ConversationTurn> turns = new ArrayList<>();
  private final List<ConversationTurn> pendingTurns = new ArrayList<>();

  private ConversationStatus status = ConversationStatus.ACTIVE;
  private long version = 1;
  private long persistedVersion;
  private int assistantTurnCount;
  private int nextSequence = 1;
  private Instant lastAuthorMessageAt;
  private String escalatedRequestId;
  private String escalationReason;
  private Instant closedInactiveAt;

  public Conversation(
      String id,
      String workspaceId,
      String authorSubject,
      int maxAssistantTurns,
      Instant createdAt) {
    this.id = id;
    this.workspaceId = workspaceId;
    this.authorSubject = authorSubject;
    this.maxAssistantTurns = maxAssistantTurns;
    this.createdAt = createdAt;
    this.lastAuthorMessageAt = createdAt;
  }

  /** Reconstroi a conversa a partir do banco, sem gerar turno novo. */
  public static Conversation rehydrate(
      String id,
      String workspaceId,
      String authorSubject,
      int maxAssistantTurns,
      Instant createdAt,
      ConversationStatus status,
      long version,
      int assistantTurnCount,
      int nextSequence,
      Instant lastAuthorMessageAt,
      String escalatedRequestId,
      String escalationReason,
      Instant closedInactiveAt,
      List<ConversationTurn> loadedTurns) {
    Conversation conversation =
        new Conversation(id, workspaceId, authorSubject, maxAssistantTurns, createdAt);
    conversation.status = status;
    conversation.version = version;
    conversation.persistedVersion = version;
    conversation.assistantTurnCount = assistantTurnCount;
    conversation.nextSequence = nextSequence;
    conversation.lastAuthorMessageAt = lastAuthorMessageAt;
    conversation.escalatedRequestId = escalatedRequestId;
    conversation.escalationReason = escalationReason;
    conversation.closedInactiveAt = closedInactiveAt;
    conversation.turns.addAll(loadedTurns);
    return conversation;
  }

  // --- apoio a persistencia ---------------------------------------------------------------

  public boolean isNew() {
    return persistedVersion == 0;
  }

  public long persistedVersion() {
    return persistedVersion;
  }

  public List<ConversationTurn> pendingTurns() {
    return List.copyOf(pendingTurns);
  }

  /** Chamado pelo repositorio depois de gravar. */
  public void markPersisted() {
    pendingTurns.clear();
    persistedVersion = version;
  }

  public int nextSequence() {
    return nextSequence;
  }

  public void requireVersion(long expectedVersion) {
    if (expectedVersion != version) {
      throw new ConversationStateException(
          "VERSION_CONFLICT",
          "versao esperada " + expectedVersion + " difere da atual " + version);
    }
  }

  /** Somente o autor escreve na conversa (BR3-002). */
  public void requireAuthor(String subject) {
    if (!authorSubject.equals(subject)) {
      throw new com.mentoria.triagem.conversations.ConversationNotFoundException(
          "conversa nao encontrada");
    }
  }

  private void requireWritable() {
    if (status.isTerminal() || status == ConversationStatus.CLOSED_INACTIVE) {
      throw new ConversationStateException(
          "CONVERSATION_NOT_ACTIVE", "conversa em estado " + status);
    }
  }

  public ConversationTurn addAuthorTurn(String text, Instant at) {
    requireWritable();
    ConversationTurn turn =
        new ConversationTurn(nextSequence++, TurnAuthor.REQUESTER, text, TurnState.GENERATED, at);
    turns.add(turn);
    pendingTurns.add(turn);
    lastAuthorMessageAt = at;
    if (status == ConversationStatus.AWAITING_CONFIRMATION) {
      // Mensagem nova em vez de confirmacao equivale a seguir conversando (FR3-005).
      status = ConversationStatus.ACTIVE;
    }
    version++;
    return turn;
  }

  public ConversationTurn addAssistantTurn(String text, TurnState state, Instant at) {
    ConversationTurn turn =
        new ConversationTurn(nextSequence++, TurnAuthor.ASSISTANT, text, state, at);
    turns.add(turn);
    pendingTurns.add(turn);
    if (state == TurnState.GENERATED) {
      assistantTurnCount++;
    }
    version++;
    return turn;
  }

  /** Veredito LIKELY_RESOLVED valido apenas abre a pergunta de confirmacao (FR3-005). */
  public void awaitConfirmation() {
    requireWritable();
    status = ConversationStatus.AWAITING_CONFIRMATION;
    version++;
  }

  /** Unico caminho para "resolvido": ato do autor (BR3-005). */
  public void confirmResolved() {
    if (status != ConversationStatus.AWAITING_CONFIRMATION) {
      throw new ConversationStateException(
          "CONFIRMATION_NOT_PENDING", "nao ha confirmacao pendente");
    }
    status = ConversationStatus.RESOLVED_CONFIRMED;
    version++;
  }

  public void backToActive() {
    if (status != ConversationStatus.AWAITING_CONFIRMATION) {
      throw new ConversationStateException(
          "CONFIRMATION_NOT_PENDING", "nao ha confirmacao pendente");
    }
    status = ConversationStatus.ACTIVE;
    version++;
  }

  public void markEscalated(String requestId, String reason) {
    if (status == ConversationStatus.ESCALATED) {
      throw new ConversationStateException("ALREADY_ESCALATED", "conversa ja escalada");
    }
    if (status == ConversationStatus.RESOLVED_CONFIRMED) {
      throw new ConversationStateException(
          "CONVERSATION_NOT_ACTIVE", "conversa confirmada como resolvida pelo autor");
    }
    status = ConversationStatus.ESCALATED;
    escalatedRequestId = requestId;
    escalationReason = reason;
    version++;
  }

  /** Encerramento por inatividade: reversivel e nunca marcado como resolvido (FR3-006). */
  public void closeInactive(Instant at) {
    requireWritable();
    status = ConversationStatus.CLOSED_INACTIVE;
    closedInactiveAt = at;
    version++;
  }

  public boolean turnLimitReached() {
    return assistantTurnCount >= maxAssistantTurns;
  }

  public int turnsRemaining() {
    return Math.max(0, maxAssistantTurns - assistantTurnCount);
  }

  public int lastAssistantSequence() {
    for (int i = turns.size() - 1; i >= 0; i--) {
      if (turns.get(i).author() == TurnAuthor.ASSISTANT) {
        return turns.get(i).sequence();
      }
    }
    return -1;
  }

  public String id() {
    return id;
  }

  public String workspaceId() {
    return workspaceId;
  }

  public String authorSubject() {
    return authorSubject;
  }

  public ConversationStatus status() {
    return status;
  }

  public long version() {
    return version;
  }

  public int maxAssistantTurns() {
    return maxAssistantTurns;
  }

  public int assistantTurnCount() {
    return assistantTurnCount;
  }

  public String escalatedRequestId() {
    return escalatedRequestId;
  }

  public String escalationReason() {
    return escalationReason;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant lastAuthorMessageAt() {
    return lastAuthorMessageAt;
  }

  public Instant closedInactiveAt() {
    return closedInactiveAt;
  }

  public String firstAuthorMessage() {
    return turns.stream()
        .filter(turn -> turn.author() == TurnAuthor.REQUESTER)
        .map(ConversationTurn::text)
        .findFirst()
        .orElse("");
  }

  public List<TurnView> turnViews() {
    return turns.stream()
        .map(turn -> new TurnView(turn.sequence(), turn.author(), turn.text(), turn.state(), turn.at()))
        .toList();
  }
}
