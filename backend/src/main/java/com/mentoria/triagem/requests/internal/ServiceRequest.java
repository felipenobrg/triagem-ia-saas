package com.mentoria.triagem.requests.internal;

import com.mentoria.triagem.requests.Category;
import com.mentoria.triagem.requests.HistoryEntry;
import com.mentoria.triagem.requests.InvalidTransitionException;
import com.mentoria.triagem.requests.ProblemContext;
import com.mentoria.triagem.requests.RequestSource;
import com.mentoria.triagem.requests.RequestStatus;
import com.mentoria.triagem.requests.Suggestion;
import com.mentoria.triagem.requests.Urgency;
import com.mentoria.triagem.requests.VersionConflictException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agregado de solicitacao. Concentra as invariantes da Spec 001: texto original imutavel,
 * tabela de transicoes, versao otimista e historico que nunca e apagado.
 *
 * <p>Interno ao modulo requests: outros modulos usam RequestService. O agregado tambem
 * controla o que ainda precisa ser gravado ({@link #pendingHistory()},
 * {@link #pendingClarifications()}) e qual versao esta no banco ({@link #persistedVersion()}),
 * para que o repositorio faca UPDATE condicional por versao.
 */
public class ServiceRequest {

  private static final Map<RequestStatus, Set<RequestStatus>> ALLOWED = allowedTransitions();

  private final String id;
  private final String workspaceId;
  private final String requesterSubject;
  private final String originalText;
  private final RequestSource source;
  private final String conversationId;
  private final Instant createdAt;
  private final List<HistoryEntry> history = new ArrayList<>();
  private final List<HistoryEntry> pendingHistory = new ArrayList<>();
  private final List<String> clarifications = new ArrayList<>();
  private final List<String> pendingClarifications = new ArrayList<>();

  private RequestStatus status = RequestStatus.RECEIVED;
  private long version = 1;
  private long persistedVersion;
  private Suggestion suggestion;
  private Category category;
  private Urgency urgency;
  private String pendingQuestion;
  private String humanRoutingReason;
  private ProblemContext problemContext;
  private boolean clarificationRoundUsed;

  public ServiceRequest(
      String id,
      String workspaceId,
      String requesterSubject,
      String originalText,
      RequestSource source,
      String conversationId,
      Instant createdAt) {
    if (originalText == null || originalText.isBlank()) {
      throw new IllegalArgumentException("texto original obrigatorio");
    }
    this.id = id;
    this.workspaceId = workspaceId;
    this.requesterSubject = requesterSubject;
    this.originalText = originalText;
    this.source = source;
    this.conversationId = conversationId;
    this.createdAt = createdAt;
    record("system", "CREATED", null, RequestStatus.RECEIVED, null, null, createdAt);
  }

  private ServiceRequest(
      String id,
      String workspaceId,
      String requesterSubject,
      String originalText,
      RequestSource source,
      String conversationId,
      Instant createdAt,
      RequestStatus status,
      long version) {
    this.id = id;
    this.workspaceId = workspaceId;
    this.requesterSubject = requesterSubject;
    this.originalText = originalText;
    this.source = source;
    this.conversationId = conversationId;
    this.createdAt = createdAt;
    this.status = status;
    this.version = version;
    this.persistedVersion = version;
  }

  /** Reconstroi o agregado a partir do banco, sem gerar historico novo. */
  public static ServiceRequest rehydrate(
      String id,
      String workspaceId,
      String requesterSubject,
      String originalText,
      RequestSource source,
      String conversationId,
      Instant createdAt,
      RequestStatus status,
      long version,
      Category category,
      Urgency urgency,
      Suggestion suggestion,
      String pendingQuestion,
      String humanRoutingReason,
      boolean clarificationRoundUsed,
      ProblemContext problemContext,
      List<String> loadedClarifications,
      List<HistoryEntry> loadedHistory) {
    ServiceRequest request =
        new ServiceRequest(
            id,
            workspaceId,
            requesterSubject,
            originalText,
            source,
            conversationId,
            createdAt,
            status,
            version);
    request.category = category;
    request.urgency = urgency;
    request.suggestion = suggestion;
    request.pendingQuestion = pendingQuestion;
    request.humanRoutingReason = humanRoutingReason;
    request.clarificationRoundUsed = clarificationRoundUsed;
    request.problemContext = problemContext;
    request.clarifications.addAll(loadedClarifications);
    request.history.addAll(loadedHistory);
    return request;
  }

  private static Map<RequestStatus, Set<RequestStatus>> allowedTransitions() {
    Map<RequestStatus, Set<RequestStatus>> map = new EnumMap<>(RequestStatus.class);
    map.put(RequestStatus.RECEIVED, EnumSet.of(RequestStatus.TRIAGE_PENDING, RequestStatus.OPEN));
    map.put(
        RequestStatus.TRIAGE_PENDING,
        EnumSet.of(
            RequestStatus.NEEDS_REVIEW, RequestStatus.NEEDS_CLARIFICATION, RequestStatus.OPEN));
    map.put(
        RequestStatus.NEEDS_CLARIFICATION,
        EnumSet.of(RequestStatus.TRIAGE_PENDING, RequestStatus.NEEDS_REVIEW, RequestStatus.OPEN));
    map.put(RequestStatus.NEEDS_REVIEW, EnumSet.of(RequestStatus.OPEN, RequestStatus.RECEIVED));
    map.put(RequestStatus.OPEN, EnumSet.of(RequestStatus.IN_PROGRESS));
    map.put(RequestStatus.IN_PROGRESS, EnumSet.of(RequestStatus.OPEN, RequestStatus.RESOLVED));
    map.put(RequestStatus.RESOLVED, EnumSet.noneOf(RequestStatus.class));
    return Collections.unmodifiableMap(map);
  }

  public void requireVersion(long expectedVersion) {
    if (expectedVersion != version) {
      throw new VersionConflictException(
          "versao esperada " + expectedVersion + " difere da atual " + version);
    }
  }

  private void transitionTo(
      RequestStatus target, String actor, String action, String reason, Instant at) {
    Set<RequestStatus> allowed = ALLOWED.getOrDefault(status, Set.of());
    if (!allowed.contains(target)) {
      throw new InvalidTransitionException("transicao invalida: " + status + " -> " + target);
    }
    RequestStatus from = status;
    status = target;
    version++;
    record(actor, action, from, target, reason, null, at);
  }

  private void record(
      String actor,
      String action,
      RequestStatus from,
      RequestStatus to,
      String reason,
      String correlationId,
      Instant at) {
    HistoryEntry entry =
        new HistoryEntry(actor, action, from, to, version, reason, correlationId, at);
    history.add(entry);
    pendingHistory.add(entry);
  }

  /** Sistema agenda a triagem automatica (FR-008). */
  public void scheduleTriage(Instant at) {
    transitionTo(RequestStatus.TRIAGE_PENDING, "system", "TRIAGE_SCHEDULED", null, at);
  }

  /** IA encaminha ao humano, com ou sem sugestao (FR-011, FR-024). */
  public void routeToHumanReview(Suggestion newSuggestion, String reasonCode, Instant at) {
    this.suggestion = newSuggestion;
    this.humanRoutingReason = reasonCode;
    this.pendingQuestion = null;
    transitionTo(RequestStatus.NEEDS_REVIEW, "system", "ROUTED_TO_HUMAN_REVIEW", reasonCode, at);
  }

  /** IA pede complemento; uma unica rodada automatica (FR-011, BR-010). */
  public void requestClarification(String question, Instant at) {
    if (clarificationRoundUsed) {
      throw new InvalidTransitionException("limite de uma rodada de complemento atingido");
    }
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("pergunta obrigatoria");
    }
    this.pendingQuestion = question;
    this.clarificationRoundUsed = true;
    transitionTo(RequestStatus.NEEDS_CLARIFICATION, "system", "CLARIFICATION_REQUESTED", null, at);
  }

  /** Requester complementa; texto vai para registro proprio, nao sobrescreve a origem (FR-006). */
  public void addClarification(String text, Instant at) {
    if (status != RequestStatus.NEEDS_CLARIFICATION) {
      throw new InvalidTransitionException("complemento permitido apenas em NEEDS_CLARIFICATION");
    }
    clarifications.add(text);
    pendingClarifications.add(text);
    this.pendingQuestion = null;
    transitionTo(RequestStatus.TRIAGE_PENDING, requesterSubject, "CLARIFICATION_ADDED", null, at);
  }

  /** Requester pede pessoa agente sem complementar (FR-023). */
  public void requestHumanReview(Instant at) {
    this.humanRoutingReason = "REQUESTER_REQUESTED_HUMAN";
    transitionTo(RequestStatus.NEEDS_REVIEW, requesterSubject, "HUMAN_REVIEW_REQUESTED", null, at);
  }

  /** Decisao humana sobre a sugestao (FR-013). */
  public void approve(String actor, Category decidedCategory, Urgency decidedUrgency, Instant at) {
    if (status != RequestStatus.NEEDS_REVIEW) {
      throw new InvalidTransitionException("aprovacao exige NEEDS_REVIEW");
    }
    if (suggestion == null) {
      throw new InvalidTransitionException("sem sugestao para aprovar: use classificacao manual");
    }
    this.category = decidedCategory;
    this.urgency = decidedUrgency;
    transitionTo(RequestStatus.OPEN, actor, "SUGGESTION_APPROVED", null, at);
  }

  public void rejectSuggestion(String actor, Instant at) {
    if (status != RequestStatus.NEEDS_REVIEW) {
      throw new InvalidTransitionException("rejeicao exige NEEDS_REVIEW");
    }
    transitionTo(RequestStatus.RECEIVED, actor, "SUGGESTION_REJECTED", null, at);
  }

  /** Classificacao manual; fora de NEEDS_REVIEW exige motivo de bypass (FR-009). */
  public void classifyManually(
      String actor, Category decidedCategory, Urgency decidedUrgency, String reason, Instant at) {
    if (status != RequestStatus.NEEDS_REVIEW && (reason == null || reason.isBlank())) {
      throw new IllegalArgumentException("bypass da analise da IA exige motivo");
    }
    this.category = decidedCategory;
    this.urgency = decidedUrgency;
    transitionTo(RequestStatus.OPEN, actor, "MANUALLY_CLASSIFIED", reason, at);
  }

  public void transitionOperational(String actor, RequestStatus target, String reason, Instant at) {
    boolean needsReason = status == RequestStatus.IN_PROGRESS && target == RequestStatus.OPEN;
    if (needsReason && (reason == null || reason.isBlank())) {
      throw new IllegalArgumentException("retorno a OPEN exige motivo");
    }
    transitionTo(target, actor, "STATUS_TRANSITION", reason, at);
  }

  public void attachProblemContext(ProblemContext context) {
    this.problemContext = context;
  }

  // --- apoio a persistencia ---------------------------------------------------------------

  public boolean isNew() {
    return persistedVersion == 0;
  }

  public long persistedVersion() {
    return persistedVersion;
  }

  public List<HistoryEntry> pendingHistory() {
    return List.copyOf(pendingHistory);
  }

  public List<String> pendingClarifications() {
    return List.copyOf(pendingClarifications);
  }

  /** Chamado pelo repositorio depois de gravar: o banco passa a conter a versao atual. */
  public void markPersisted() {
    pendingHistory.clear();
    pendingClarifications.clear();
    persistedVersion = version;
  }

  public int clarificationCount() {
    return clarifications.size();
  }

  // --- leitura ----------------------------------------------------------------------------

  public String id() {
    return id;
  }

  public String workspaceId() {
    return workspaceId;
  }

  public String requesterSubject() {
    return requesterSubject;
  }

  public String originalText() {
    return originalText;
  }

  public RequestSource source() {
    return source;
  }

  public String conversationId() {
    return conversationId;
  }

  public RequestStatus status() {
    return status;
  }

  public long version() {
    return version;
  }

  public Suggestion suggestion() {
    return suggestion;
  }

  public Category category() {
    return category;
  }

  public Urgency urgency() {
    return urgency;
  }

  public String pendingQuestion() {
    return pendingQuestion;
  }

  public String humanRoutingReason() {
    return humanRoutingReason;
  }

  public ProblemContext problemContext() {
    return problemContext;
  }

  public boolean clarificationRoundUsed() {
    return clarificationRoundUsed;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public List<String> clarifications() {
    return List.copyOf(clarifications);
  }

  public List<HistoryEntry> history() {
    return List.copyOf(history);
  }
}
