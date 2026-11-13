package com.mentoria.triagem.conversations.internal;

import com.mentoria.triagem.conversations.ConversationStateException;
import com.mentoria.triagem.conversations.ConversationStatus;
import com.mentoria.triagem.conversations.JudgeOutcome;
import com.mentoria.triagem.conversations.JudgeReasonCode;
import com.mentoria.triagem.conversations.TranscriptAccess;
import com.mentoria.triagem.conversations.TurnAuthor;
import com.mentoria.triagem.conversations.TurnState;
import com.mentoria.triagem.conversations.Verdict;
import com.mentoria.triagem.conversations.VerdictState;
import com.mentoria.triagem.requests.Category;
import com.mentoria.triagem.requests.Urgency;
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

/** Persistencia do modulo conversations em PostgreSQL. */
@Component
public class JdbcConversationStore implements ConversationStore {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcConversationStore(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(Conversation conversation) {
    if (conversation.isNew()) {
      jdbc.update(
          """
          INSERT INTO conversation (
            id, workspace_id, author_subject, status, version, max_assistant_turns,
            assistant_turn_count, next_sequence, last_author_message_at, escalated_request_id,
            escalation_reason, closed_inactive_at, created_at)
          VALUES (
            :id, :workspaceId, :authorSubject, :status, :version, :maxTurns,
            :assistantTurnCount, :nextSequence, :lastAuthorMessageAt, :escalatedRequestId,
            :escalationReason, :closedInactiveAt, :createdAt)
          """,
          parameters(conversation));
    } else {
      int updated =
          jdbc.update(
              """
              UPDATE conversation SET
                status = :status,
                version = :version,
                assistant_turn_count = :assistantTurnCount,
                next_sequence = :nextSequence,
                last_author_message_at = :lastAuthorMessageAt,
                escalated_request_id = :escalatedRequestId,
                escalation_reason = :escalationReason,
                closed_inactive_at = :closedInactiveAt
              WHERE id = :id AND workspace_id = :workspaceId AND version = :persistedVersion
              """,
              parameters(conversation)
                  .addValue("persistedVersion", conversation.persistedVersion()));
      if (updated == 0) {
        throw new ConversationStateException(
            "VERSION_CONFLICT", "a conversa foi alterada por outra operacao");
      }
    }
    insertPendingTurns(conversation);
    conversation.markPersisted();
  }

  private MapSqlParameterSource parameters(Conversation conversation) {
    return new MapSqlParameterSource()
        .addValue("id", UUID.fromString(conversation.id()))
        .addValue("workspaceId", conversation.workspaceId())
        .addValue("authorSubject", conversation.authorSubject())
        .addValue("status", conversation.status().name())
        .addValue("version", conversation.version())
        .addValue("maxTurns", conversation.maxAssistantTurns())
        .addValue("assistantTurnCount", conversation.assistantTurnCount())
        .addValue("nextSequence", conversation.nextSequence())
        .addValue("lastAuthorMessageAt", Timestamp.from(conversation.lastAuthorMessageAt()))
        .addValue(
            "escalatedRequestId",
            conversation.escalatedRequestId() == null
                ? null
                : UUID.fromString(conversation.escalatedRequestId()))
        .addValue("escalationReason", conversation.escalationReason())
        .addValue(
            "closedInactiveAt",
            conversation.closedInactiveAt() == null
                ? null
                : Timestamp.from(conversation.closedInactiveAt()))
        .addValue("createdAt", Timestamp.from(conversation.createdAt()));
  }

  /** Turnos sao somente inseridos: nunca UPDATE, nunca DELETE (BR3-003). */
  private void insertPendingTurns(Conversation conversation) {
    for (ConversationTurn turn : conversation.pendingTurns()) {
      jdbc.update(
          """
          INSERT INTO conversation_turn (
            conversation_id, workspace_id, sequence, author, text, state, occurred_at)
          VALUES (:conversationId, :workspaceId, :sequence, :author, :text, :state, :occurredAt)
          """,
          new MapSqlParameterSource()
              .addValue("conversationId", UUID.fromString(conversation.id()))
              .addValue("workspaceId", conversation.workspaceId())
              .addValue("sequence", turn.sequence())
              .addValue("author", turn.author().name())
              .addValue("text", turn.text())
              .addValue("state", turn.state().name())
              .addValue("occurredAt", Timestamp.from(turn.at())));
    }
  }

  @Override
  public Optional<Conversation> find(String workspaceId, String conversationId) {
    UUID id;
    try {
      id = UUID.fromString(conversationId);
    } catch (IllegalArgumentException invalid) {
      return Optional.empty();
    }
    return jdbc
        .query(
            "SELECT * FROM conversation WHERE id = :id AND workspace_id = :workspaceId",
            Map.of("id", id, "workspaceId", workspaceId),
            this::mapConversation)
        .stream()
        .findFirst();
  }

  @Override
  public List<Conversation> findByAuthor(String workspaceId, String authorSubject) {
    return jdbc.query(
        """
        SELECT * FROM conversation
        WHERE workspace_id = :workspaceId AND author_subject = :author
        ORDER BY created_at, id
        """,
        Map.of("workspaceId", workspaceId, "author", authorSubject),
        this::mapConversation);
  }

  @Override
  public long countActiveByAuthor(String workspaceId, String authorSubject) {
    Long count =
        jdbc.queryForObject(
            """
            SELECT count(*) FROM conversation
            WHERE workspace_id = :workspaceId AND author_subject = :author
              AND status IN ('ACTIVE', 'AWAITING_CONFIRMATION')
            """,
            Map.of("workspaceId", workspaceId, "author", authorSubject),
            Long.class);
    return count == null ? 0 : count;
  }

  @Override
  public List<Conversation> findInactive(Instant cutoff) {
    return jdbc.query(
        """
        SELECT * FROM conversation
        WHERE status IN ('ACTIVE', 'AWAITING_CONFIRMATION') AND last_author_message_at < :cutoff
        ORDER BY last_author_message_at
        """,
        Map.of("cutoff", Timestamp.from(cutoff)),
        this::mapConversation);
  }

  @Override
  public void recordVerdict(JudgeVerdictRecord verdict) {
    JudgeOutcome outcome = verdict.outcome();
    jdbc.update(
        """
        INSERT INTO judge_verdict (
          conversation_id, workspace_id, turn_sequence, verdict, reason_code, confidence,
          state, rejection_reason, occurred_at)
        VALUES (:conversationId, :workspaceId, :turnSequence, :verdict, :reasonCode, :confidence,
          :state, :rejectionReason, :occurredAt)
        """,
        new MapSqlParameterSource()
            .addValue("conversationId", UUID.fromString(verdict.conversationId()))
            .addValue("workspaceId", verdict.workspaceId())
            .addValue("turnSequence", verdict.turnSequence())
            .addValue("verdict", outcome == null ? null : outcome.verdict().name())
            .addValue("reasonCode", outcome == null ? null : outcome.reasonCode().name())
            .addValue("confidence", outcome == null ? null : outcome.confidence())
            .addValue("state", verdict.state().name())
            .addValue("rejectionReason", verdict.rejectionReason())
            .addValue("occurredAt", Timestamp.from(verdict.at())));
  }

  @Override
  public List<JudgeVerdictRecord> verdictsOf(String conversationId) {
    return jdbc.query(
        "SELECT * FROM judge_verdict WHERE conversation_id = :id ORDER BY id",
        Map.of("id", UUID.fromString(conversationId)),
        (rs, row) -> {
          String verdictName = rs.getString("verdict");
          JudgeOutcome outcome =
              verdictName == null
                  ? null
                  : new JudgeOutcome(
                      Verdict.valueOf(verdictName),
                      JudgeReasonCode.valueOf(rs.getString("reason_code")),
                      rs.getDouble("confidence"),
                      null,
                      (Category) null,
                      (Urgency) null);
          return new JudgeVerdictRecord(
              rs.getString("conversation_id"),
              rs.getString("workspace_id"),
              rs.getInt("turn_sequence"),
              outcome,
              VerdictState.valueOf(rs.getString("state")),
              rs.getString("rejection_reason"),
              rs.getTimestamp("occurred_at").toInstant());
        });
  }

  @Override
  public void recordAccess(String workspaceId, String conversationId, String actor, Instant at) {
    jdbc.update(
        """
        INSERT INTO transcript_access (conversation_id, workspace_id, actor, occurred_at)
        VALUES (:conversationId, :workspaceId, :actor, :occurredAt)
        """,
        new MapSqlParameterSource()
            .addValue("conversationId", UUID.fromString(conversationId))
            .addValue("workspaceId", workspaceId)
            .addValue("actor", actor)
            .addValue("occurredAt", Timestamp.from(at)));
  }

  @Override
  public List<TranscriptAccess> accessLog(String conversationId) {
    return jdbc.query(
        "SELECT * FROM transcript_access WHERE conversation_id = :id ORDER BY id",
        Map.of("id", UUID.fromString(conversationId)),
        (rs, row) ->
            new TranscriptAccess(
                rs.getString("conversation_id"),
                rs.getString("actor"),
                rs.getTimestamp("occurred_at").toInstant()));
  }

  @Override
  public Optional<String> findIdempotentConversation(String scopedKey) {
    return jdbc
        .queryForList(
            "SELECT resource_id FROM idempotency_key WHERE scoped_key = :key",
            Map.of("key", scopedKey),
            String.class)
        .stream()
        .findFirst();
  }

  @Override
  public void rememberIdempotency(String scopedKey, String conversationId) {
    jdbc.update(
        """
        INSERT INTO idempotency_key (scoped_key, body_hash, resource_id, created_at)
        VALUES (:key, '0', :resourceId, now())
        ON CONFLICT (scoped_key) DO NOTHING
        """,
        new MapSqlParameterSource().addValue("key", scopedKey).addValue("resourceId", conversationId));
  }

  // --- mapeamento -------------------------------------------------------------------------

  private Conversation mapConversation(ResultSet rs, int rowNum) throws SQLException {
    String id = rs.getString("id");
    List<ConversationTurn> turns =
        jdbc.query(
            "SELECT * FROM conversation_turn WHERE conversation_id = :id ORDER BY sequence",
            Map.of("id", UUID.fromString(id)),
            (turnRs, turnRow) ->
                new ConversationTurn(
                    turnRs.getInt("sequence"),
                    TurnAuthor.valueOf(turnRs.getString("author")),
                    turnRs.getString("text"),
                    TurnState.valueOf(turnRs.getString("state")),
                    turnRs.getTimestamp("occurred_at").toInstant()));

    return Conversation.rehydrate(
        id,
        rs.getString("workspace_id"),
        rs.getString("author_subject"),
        rs.getInt("max_assistant_turns"),
        rs.getTimestamp("created_at").toInstant(),
        ConversationStatus.valueOf(rs.getString("status")),
        rs.getLong("version"),
        rs.getInt("assistant_turn_count"),
        rs.getInt("next_sequence"),
        rs.getTimestamp("last_author_message_at").toInstant(),
        rs.getString("escalated_request_id"),
        rs.getString("escalation_reason"),
        instant(rs.getTimestamp("closed_inactive_at")),
        turns);
  }

  private static Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
