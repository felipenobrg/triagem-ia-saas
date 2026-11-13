package com.mentoria.triagem.requests.internal;

import com.mentoria.triagem.requests.Category;
import com.mentoria.triagem.requests.HistoryEntry;
import com.mentoria.triagem.requests.ProblemContext;
import com.mentoria.triagem.requests.RequestSource;
import com.mentoria.triagem.requests.RequestStatus;
import com.mentoria.triagem.requests.Suggestion;
import com.mentoria.triagem.requests.Urgency;
import com.mentoria.triagem.requests.VersionConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Persistencia do modulo requests em PostgreSQL. */
@Component
public class JdbcRequestStore implements RequestStore {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcRequestStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(ServiceRequest request) {
    if (request.isNew()) {
      insert(request);
    } else {
      update(request);
    }
    insertPendingClarifications(request);
    insertPendingHistory(request);
    request.markPersisted();
  }

  private void insert(ServiceRequest request) {
    jdbc.update(
        """
        INSERT INTO service_request (
          id, workspace_id, requester_subject, original_text, source, conversation_id,
          status, version, category, urgency, suggestion_category, suggestion_urgency,
          suggestion_summary, suggestion_origin, suggestion_created_at, pending_question,
          human_routing_reason, clarification_round_used, created_at)
        VALUES (
          :id, :workspaceId, :requesterSubject, :originalText, :source, :conversationId,
          :status, :version, :category, :urgency, :suggestionCategory, :suggestionUrgency,
          :suggestionSummary, :suggestionOrigin, :suggestionCreatedAt, :pendingQuestion,
          :humanRoutingReason, :clarificationRoundUsed, :createdAt)
        """,
        parameters(request));
    insertProblemContext(request);
  }

  private void update(ServiceRequest request) {
    // UPDATE condicional por versao: e isto que faz duas escritas concorrentes nao vencerem
    // as duas (NFR-004). O texto original nunca aparece no SET (FR-006).
    int updated =
        jdbc.update(
            """
            UPDATE service_request SET
              status = :status,
              version = :version,
              category = :category,
              urgency = :urgency,
              suggestion_category = :suggestionCategory,
              suggestion_urgency = :suggestionUrgency,
              suggestion_summary = :suggestionSummary,
              suggestion_origin = :suggestionOrigin,
              suggestion_created_at = :suggestionCreatedAt,
              pending_question = :pendingQuestion,
              human_routing_reason = :humanRoutingReason,
              clarification_round_used = :clarificationRoundUsed
            WHERE id = :id AND workspace_id = :workspaceId AND version = :persistedVersion
            """,
            parameters(request).addValue("persistedVersion", request.persistedVersion()));
    if (updated == 0) {
      throw new VersionConflictException(
          "a solicitacao foi alterada por outra operacao: recarregue e tente de novo");
    }
  }

  private MapSqlParameterSource parameters(ServiceRequest request) {
    Suggestion suggestion = request.suggestion();
    return new MapSqlParameterSource()
        .addValue("id", UUID.fromString(request.id()))
        .addValue("workspaceId", request.workspaceId())
        .addValue("requesterSubject", request.requesterSubject())
        .addValue("originalText", request.originalText())
        .addValue("source", request.source().name())
        .addValue(
            "conversationId",
            request.conversationId() == null ? null : UUID.fromString(request.conversationId()))
        .addValue("status", request.status().name())
        .addValue("version", request.version())
        .addValue("category", name(request.category()))
        .addValue("urgency", name(request.urgency()))
        .addValue("suggestionCategory", suggestion == null ? null : name(suggestion.category()))
        .addValue("suggestionUrgency", suggestion == null ? null : name(suggestion.urgency()))
        .addValue("suggestionSummary", suggestion == null ? null : suggestion.summary())
        .addValue("suggestionOrigin", suggestion == null ? null : suggestion.origin())
        .addValue(
            "suggestionCreatedAt",
            suggestion == null ? null : Timestamp.from(suggestion.createdAt()))
        .addValue("pendingQuestion", request.pendingQuestion())
        .addValue("humanRoutingReason", request.humanRoutingReason())
        .addValue("clarificationRoundUsed", request.clarificationRoundUsed())
        .addValue("createdAt", Timestamp.from(request.createdAt()));
  }

  private void insertProblemContext(ServiceRequest request) {
    ProblemContext context = request.problemContext();
    if (context == null) {
      return;
    }
    jdbc.update(
        """
        INSERT INTO problem_context (
          request_id, workspace_id, summary, suggested_category, suggested_urgency,
          escalation_reason, generated)
        VALUES (:requestId, :workspaceId, :summary, :category, :urgency, :reason, :generated)
        """,
        new MapSqlParameterSource()
            .addValue("requestId", UUID.fromString(request.id()))
            .addValue("workspaceId", request.workspaceId())
            .addValue("summary", context.summary())
            .addValue("category", name(context.suggestedCategory()))
            .addValue("urgency", name(context.suggestedUrgency()))
            .addValue("reason", context.escalationReason())
            .addValue("generated", context.generated()));

    insertContextItems(request.id(), "SYMPTOM", context.symptoms());
    insertContextItems(request.id(), "ATTEMPT", context.attemptedSteps());
  }

  private void insertContextItems(String requestId, String kind, List<String> items) {
    for (int i = 0; i < items.size(); i++) {
      jdbc.update(
          "INSERT INTO problem_context_item (request_id, kind, position, text)"
              + " VALUES (:requestId, :kind, :position, :text)",
          new MapSqlParameterSource()
              .addValue("requestId", UUID.fromString(requestId))
              .addValue("kind", kind)
              .addValue("position", i)
              .addValue("text", items.get(i)));
    }
  }

  private void insertPendingClarifications(ServiceRequest request) {
    List<String> pending = request.pendingClarifications();
    if (pending.isEmpty()) {
      return;
    }
    int basePosition = request.clarificationCount() - pending.size();
    for (int i = 0; i < pending.size(); i++) {
      jdbc.update(
          """
          INSERT INTO request_clarification (request_id, workspace_id, position, text, created_at)
          VALUES (:requestId, :workspaceId, :position, :text, now())
          """,
          new MapSqlParameterSource()
              .addValue("requestId", UUID.fromString(request.id()))
              .addValue("workspaceId", request.workspaceId())
              .addValue("position", basePosition + i)
              .addValue("text", pending.get(i)));
    }
  }

  private void insertPendingHistory(ServiceRequest request) {
    for (HistoryEntry entry : request.pendingHistory()) {
      jdbc.update(
          """
          INSERT INTO request_history (
            request_id, workspace_id, actor, action, from_status, to_status, version, reason,
            correlation_id, occurred_at)
          VALUES (:requestId, :workspaceId, :actor, :action, :fromStatus, :toStatus, :version,
            :reason, :correlationId, :occurredAt)
          """,
          new MapSqlParameterSource()
              .addValue("requestId", UUID.fromString(request.id()))
              .addValue("workspaceId", request.workspaceId())
              .addValue("actor", entry.actor())
              .addValue("action", entry.action())
              .addValue("fromStatus", entry.fromStatus() == null ? null : entry.fromStatus().name())
              .addValue("toStatus", entry.toStatus() == null ? null : entry.toStatus().name())
              .addValue("version", entry.version())
              .addValue("reason", entry.reason())
              .addValue("correlationId", entry.correlationId())
              .addValue("occurredAt", Timestamp.from(entry.at())));
    }
  }

  @Override
  public Optional<ServiceRequest> findById(String workspaceId, String requestId) {
    UUID id;
    try {
      id = UUID.fromString(requestId);
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
    List<ServiceRequest> found =
        jdbc.query(
            "SELECT * FROM service_request WHERE id = :id AND workspace_id = :workspaceId",
            Map.of("id", id, "workspaceId", workspaceId),
            this::mapRequest);
    return found.stream().findFirst().map(this::loadChildren);
  }

  @Override
  public List<ServiceRequest> findByWorkspace(String workspaceId) {
    return jdbc
        .query(
            "SELECT * FROM service_request WHERE workspace_id = :workspaceId"
                + " ORDER BY created_at, id",
            Map.of("workspaceId", workspaceId),
            this::mapRequest)
        .stream()
        .map(this::loadChildren)
        .toList();
  }

  @Override
  public Optional<IdempotencyRecord> findIdempotency(String scopedKey) {
    return jdbc
        .query(
            "SELECT resource_id, body_hash FROM idempotency_key WHERE scoped_key = :key",
            Map.of("key", scopedKey),
            (rs, row) ->
                new IdempotencyRecord(rs.getString("resource_id"), Integer.parseInt(rs.getString("body_hash"))))
        .stream()
        .findFirst();
  }

  @Override
  public void rememberIdempotency(String scopedKey, String requestId, int bodyHash) {
    jdbc.update(
        """
        INSERT INTO idempotency_key (scoped_key, body_hash, resource_id, created_at)
        VALUES (:key, :bodyHash, :resourceId, now())
        ON CONFLICT (scoped_key) DO NOTHING
        """,
        new MapSqlParameterSource()
            .addValue("key", scopedKey)
            .addValue("bodyHash", String.valueOf(bodyHash))
            .addValue("resourceId", requestId));
  }

  @Override
  public Optional<String> findActiveRequestForConversation(
      String workspaceId, String conversationId) {
    return jdbc
        .queryForList(
            """
            SELECT id FROM service_request
            WHERE workspace_id = :workspaceId AND conversation_id = :conversationId
              AND status <> 'RESOLVED'
            """,
            Map.of("workspaceId", workspaceId, "conversationId", UUID.fromString(conversationId)),
            String.class)
        .stream()
        .findFirst();
  }

  // --- mapeamento -------------------------------------------------------------------------

  private ServiceRequest mapRequest(ResultSet rs, int rowNum) throws SQLException {
    Suggestion suggestion = null;
    if (rs.getString("suggestion_category") != null || rs.getString("suggestion_summary") != null) {
      suggestion =
          new Suggestion(
              enumValue(Category.class, rs.getString("suggestion_category")),
              enumValue(Urgency.class, rs.getString("suggestion_urgency")),
              rs.getString("suggestion_summary"),
              rs.getString("suggestion_origin"),
              instant(rs.getTimestamp("suggestion_created_at")));
    }
    return ServiceRequest.rehydrate(
        rs.getString("id"),
        rs.getString("workspace_id"),
        rs.getString("requester_subject"),
        rs.getString("original_text"),
        RequestSource.valueOf(rs.getString("source")),
        rs.getString("conversation_id"),
        instant(rs.getTimestamp("created_at")),
        RequestStatus.valueOf(rs.getString("status")),
        rs.getLong("version"),
        enumValue(Category.class, rs.getString("category")),
        enumValue(Urgency.class, rs.getString("urgency")),
        suggestion,
        rs.getString("pending_question"),
        rs.getString("human_routing_reason"),
        rs.getBoolean("clarification_round_used"),
        null,
        List.of(),
        List.of());
  }

  private ServiceRequest loadChildren(ServiceRequest shallow) {
    List<String> clarifications =
        jdbc.queryForList(
            "SELECT text FROM request_clarification WHERE request_id = :id ORDER BY position",
            Map.of("id", UUID.fromString(shallow.id())),
            String.class);

    List<HistoryEntry> history =
        jdbc.query(
            "SELECT * FROM request_history WHERE request_id = :id ORDER BY id",
            Map.of("id", UUID.fromString(shallow.id())),
            (rs, row) ->
                new HistoryEntry(
                    rs.getString("actor"),
                    rs.getString("action"),
                    enumValue(RequestStatus.class, rs.getString("from_status")),
                    enumValue(RequestStatus.class, rs.getString("to_status")),
                    rs.getLong("version"),
                    rs.getString("reason"),
                    rs.getString("correlation_id"),
                    instant(rs.getTimestamp("occurred_at"))));

    ProblemContext context = loadProblemContext(shallow.id());

    return ServiceRequest.rehydrate(
        shallow.id(),
        shallow.workspaceId(),
        shallow.requesterSubject(),
        shallow.originalText(),
        shallow.source(),
        shallow.conversationId(),
        shallow.createdAt(),
        shallow.status(),
        shallow.version(),
        shallow.category(),
        shallow.urgency(),
        shallow.suggestion(),
        shallow.pendingQuestion(),
        shallow.humanRoutingReason(),
        shallow.clarificationRoundUsed(),
        context,
        clarifications,
        history);
  }

  private ProblemContext loadProblemContext(String requestId) {
    UUID id = UUID.fromString(requestId);
    List<ProblemContext> rows =
        jdbc.query(
            "SELECT * FROM problem_context WHERE request_id = :id",
            Map.of("id", id),
            (rs, row) ->
                new ProblemContext(
                    rs.getString("summary"),
                    List.of(),
                    List.of(),
                    enumValue(Category.class, rs.getString("suggested_category")),
                    enumValue(Urgency.class, rs.getString("suggested_urgency")),
                    rs.getString("escalation_reason"),
                    rs.getBoolean("generated")));
    if (rows.isEmpty()) {
      return null;
    }
    ProblemContext base = rows.get(0);
    return new ProblemContext(
        base.summary(),
        contextItems(id, "SYMPTOM"),
        contextItems(id, "ATTEMPT"),
        base.suggestedCategory(),
        base.suggestedUrgency(),
        base.escalationReason(),
        base.generated());
  }

  private List<String> contextItems(UUID requestId, String kind) {
    return jdbc.queryForList(
        "SELECT text FROM problem_context_item WHERE request_id = :id AND kind = :kind"
            + " ORDER BY position",
        Map.of("id", requestId, "kind", kind),
        String.class);
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
    return value == null ? null : Enum.valueOf(type, value);
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }

  private static Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

}
