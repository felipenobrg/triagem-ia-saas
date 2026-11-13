package com.mentoria.triagem.requests;

import com.mentoria.triagem.identity.MembershipService;
import com.mentoria.triagem.messaging.EventPublisher;
import com.mentoria.triagem.messaging.EventTypes;
import com.mentoria.triagem.messaging.OutboxEvent;
import com.mentoria.triagem.identity.Role;
import com.mentoria.triagem.requests.internal.RequestStore;
import com.mentoria.triagem.requests.internal.ServiceRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API publica do modulo requests. Toda operacao revalida membership ativo (BR-001) e
 * nenhuma decisao de IA chega aqui sem passar pelas invariantes do agregado (BR-004).
 */
@Service
public class RequestService {

  private final RequestStore store;
  private final MembershipService memberships;
  private final EventPublisher publisher;
  private final Clock clock;

  public RequestService(
      RequestStore store,
      MembershipService memberships,
      EventPublisher publisher,
      Clock clock) {
    this.store = store;
    this.memberships = memberships;
    this.publisher = publisher;
    this.clock = clock;
  }

  /**
   * Grava a intencao de pedir a analise inicial. Roda dentro da transacao de negocio: ou a
   * solicitacao e o evento existem, ou nenhum dos dois (FR-018).
   */
  private void requestTriage(ServiceRequest request) {
    publisher.publish(
        OutboxEvent.of(
            EventTypes.TRIAGE_REQUESTED,
            request.workspaceId(),
            request.id(),
            Map.of("requestId", request.id()),
            now()));
  }

  /** Criacao pelo requester (FR-005). A chave idempotente e escopada por subject + workspace. */
  @Transactional
  public RequestView create(
      String workspaceId, String subject, String text, String idempotencyKey) {
    memberships.requireMember(workspaceId, subject);
    String scopedKey = scopedKey(workspaceId, subject, "requests.create", idempotencyKey);
    int bodyHash = text.hashCode();
    Optional<RequestStore.IdempotencyRecord> previous = store.findIdempotency(scopedKey);
    if (previous.isPresent()) {
      if (previous.get().bodyHash() != bodyHash) {
        throw new IdempotencyConflictException("mesma chave com corpo diferente");
      }
      return view(require(workspaceId, previous.get().requestId()));
    }

    ServiceRequest request =
        new ServiceRequest(
            UUID.randomUUID().toString(),
            workspaceId,
            subject,
            text,
            RequestSource.WEB,
            null,
            now());
    store.save(request);
    store.rememberIdempotency(scopedKey, request.id(), bodyHash);
    requestTriage(request);
    return view(request);
  }

  /**
   * Criacao a partir de uma conversa escalada (FR3-014). O chamado nasce em NEEDS_REVIEW:
   * nunca em OPEN (BR3-008). Chamado apenas pelo modulo conversations.
   */
  @Transactional
  public RequestView createFromConversation(
      String workspaceId,
      String requesterSubject,
      String conversationId,
      String originalText,
      ProblemContext context) {
    Optional<String> existing = store.findActiveRequestForConversation(workspaceId, conversationId);
    if (existing.isPresent()) {
      return view(require(workspaceId, existing.get()));
    }

    ServiceRequest request =
        new ServiceRequest(
            UUID.randomUUID().toString(),
            workspaceId,
            requesterSubject,
            originalText,
            RequestSource.CONVERSATION,
            conversationId,
            now());
    request.scheduleTriage(now());
    Suggestion suggestion =
        context.generated()
            ? new Suggestion(
                context.suggestedCategory(),
                context.suggestedUrgency(),
                context.summary(),
                "CONVERSATION_JUDGE",
                now())
            : null;
    request.routeToHumanReview(suggestion, context.escalationReason(), now());
    request.attachProblemContext(context);
    store.save(request);
    return view(request);
  }

  /** Leitura autorizada: requester so le o que e dele (FR-003). */
  public RequestView get(String workspaceId, String subject, String requestId) {
    var membership = memberships.requireMember(workspaceId, subject);
    ServiceRequest request = require(workspaceId, requestId);
    if (!membership.role().isAgentOrAdmin() && !request.requesterSubject().equals(subject)) {
      throw new RequestNotFoundException("solicitacao nao encontrada");
    }
    return view(request);
  }

  public List<RequestView> list(String workspaceId, String subject, RequestStatus statusFilter) {
    var membership = memberships.requireMember(workspaceId, subject);
    return store.findByWorkspace(workspaceId).stream()
        .filter(
            request ->
                membership.role().isAgentOrAdmin() || request.requesterSubject().equals(subject))
        .filter(request -> statusFilter == null || request.status() == statusFilter)
        .map(this::view)
        .toList();
  }

  /** Revisao humana da sugestao (FR-013). */
  @Transactional
  public RequestView review(
      String workspaceId,
      String subject,
      String requestId,
      long expectedVersion,
      ReviewDecision decision,
      Category category,
      Urgency urgency) {
    memberships.requireAgentOrAdmin(workspaceId, subject);
    ServiceRequest request = require(workspaceId, requestId);
    request.requireVersion(expectedVersion);
    switch (decision) {
      case APPROVE -> {
        Suggestion suggestion = request.suggestion();
        if (suggestion == null) {
          throw new InvalidTransitionException("sem sugestao para aprovar");
        }
        request.approve(subject, suggestion.category(), suggestion.urgency(), now());
      }
      case APPROVE_EDITED -> {
        requireClassification(category, urgency);
        request.approve(subject, category, urgency, now());
      }
      case REJECT -> request.rejectSuggestion(subject, now());
    }
    store.save(request);
    return view(request);
  }

  /** Classificacao manual, com bypass explicito fora da fila humana (FR-009). */
  @Transactional
  public RequestView classifyManually(
      String workspaceId,
      String subject,
      String requestId,
      long expectedVersion,
      Category category,
      Urgency urgency,
      String reason) {
    memberships.requireAgentOrAdmin(workspaceId, subject);
    requireClassification(category, urgency);
    ServiceRequest request = require(workspaceId, requestId);
    request.requireVersion(expectedVersion);
    request.classifyManually(subject, category, urgency, reason, now());
    store.save(request);
    return view(request);
  }

  @Transactional
  public RequestView transition(
      String workspaceId,
      String subject,
      String requestId,
      long expectedVersion,
      RequestStatus target,
      String reason) {
    memberships.requireAgentOrAdmin(workspaceId, subject);
    ServiceRequest request = require(workspaceId, requestId);
    request.requireVersion(expectedVersion);
    request.transitionOperational(subject, target, reason, now());
    store.save(request);
    return view(request);
  }

  /** Complemento do requester autor, uma unica rodada automatica (FR-023, BR-010). */
  @Transactional
  public RequestView addClarification(
      String workspaceId, String subject, String requestId, long expectedVersion, String text) {
    memberships.requireMember(workspaceId, subject, Role.REQUESTER, Role.AGENT, Role.ADMIN);
    ServiceRequest request = require(workspaceId, requestId);
    if (!request.requesterSubject().equals(subject)) {
      throw new RequestNotFoundException("solicitacao nao encontrada");
    }
    request.requireVersion(expectedVersion);
    request.addClarification(text, now());
    store.save(request);
    requestTriage(request);
    return view(request);
  }

  /** Requester pede pessoa agente sem satisfazer a IA (FR-023, BR-010). */
  @Transactional
  public RequestView requestHumanReview(
      String workspaceId, String subject, String requestId, long expectedVersion) {
    memberships.requireMember(workspaceId, subject);
    ServiceRequest request = require(workspaceId, requestId);
    if (!request.requesterSubject().equals(subject)) {
      throw new RequestNotFoundException("solicitacao nao encontrada");
    }
    request.requireVersion(expectedVersion);
    request.requestHumanReview(now());
    store.save(request);
    return view(request);
  }

  // --- operacoes de sistema usadas pelo modulo triage -------------------------------------

  /**
   * Garante que existe uma tentativa de triagem agendada. Idempotente: um pedido que ja esta
   * em TRIAGE_PENDING (por exemplo depois de um complemento) nao transiciona de novo.
   */
  @Transactional
  public RequestView scheduleTriage(String workspaceId, String requestId) {
    ServiceRequest request = require(workspaceId, requestId);
    if (request.status() != RequestStatus.TRIAGE_PENDING) {
      request.scheduleTriage(now());
      store.save(request);
    }
    return view(request);
  }

  /**
   * Resultado validado da triagem. A decisao humana sempre vence: se a versao mudou e o
   * pedido saiu de TRIAGE_PENDING, o resultado tardio e descartado (cenario 13 da Spec 001).
   */
  @Transactional
  public Optional<RequestView> applyHumanReviewRoute(
      String workspaceId, String requestId, long attemptVersion, Suggestion suggestion, String reasonCode) {
    ServiceRequest request = require(workspaceId, requestId);
    if (request.status() != RequestStatus.TRIAGE_PENDING || request.version() != attemptVersion) {
      return Optional.empty();
    }
    request.routeToHumanReview(suggestion, reasonCode, now());
    store.save(request);
    return Optional.of(view(request));
  }

  @Transactional
  public Optional<RequestView> applyClarificationRoute(
      String workspaceId, String requestId, long attemptVersion, String question) {
    ServiceRequest request = require(workspaceId, requestId);
    if (request.status() != RequestStatus.TRIAGE_PENDING || request.version() != attemptVersion) {
      return Optional.empty();
    }
    if (request.clarificationRoundUsed()) {
      request.routeToHumanReview(null, "CLARIFICATION_LIMIT", now());
    } else {
      request.requestClarification(question, now());
    }
    store.save(request);
    return Optional.of(view(request));
  }

  public Optional<RequestView> findByConversation(String workspaceId, String conversationId) {
    return store
        .findActiveRequestForConversation(workspaceId, conversationId)
        .map(id -> view(require(workspaceId, id)));
  }

  // --- apoio ------------------------------------------------------------------------------

  private static void requireClassification(Category category, Urgency urgency) {
    if (category == null || urgency == null) {
      throw new IllegalArgumentException("categoria e urgencia obrigatorias");
    }
  }

  private ServiceRequest require(String workspaceId, String requestId) {
    return store
        .findById(workspaceId, requestId)
        .orElseThrow(() -> new RequestNotFoundException("solicitacao nao encontrada"));
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

  private Instant now() {
    return clock.instant();
  }

  private RequestView view(ServiceRequest request) {
    return new RequestView(
        request.id(),
        request.workspaceId(),
        request.requesterSubject(),
        request.originalText(),
        request.source(),
        request.conversationId(),
        request.status(),
        request.version(),
        request.suggestion(),
        request.category(),
        request.urgency(),
        request.pendingQuestion(),
        request.humanRoutingReason(),
        request.problemContext(),
        request.clarifications(),
        request.history(),
        request.createdAt());
  }
}
