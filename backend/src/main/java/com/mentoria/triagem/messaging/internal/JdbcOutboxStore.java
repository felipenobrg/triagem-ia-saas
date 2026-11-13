package com.mentoria.triagem.messaging.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentoria.triagem.messaging.EventPublisher;
import com.mentoria.triagem.messaging.InboxGuard;
import com.mentoria.triagem.messaging.OutboxEvent;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Outbox e inbox em PostgreSQL.
 *
 * <p>{@link #publish(OutboxEvent)} apenas grava a intencao: roda dentro da transacao de
 * negocio, entao ou o pedido e o evento existem, ou nenhum dos dois (FR-018). Quem fala com
 * o broker e o {@link OutboxDispatcher}.
 */
@Component
public class JdbcOutboxStore implements EventPublisher, InboxGuard {

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public JdbcOutboxStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  @Override
  public void publish(OutboxEvent event) {
    try {
      jdbc.update(
          """
          INSERT INTO outbox_message (
            event_id, event_type, schema_version, workspace_id, aggregate_id, payload,
            correlation_id, occurred_at, next_attempt_at)
          VALUES (:eventId, :eventType, :schemaVersion, :workspaceId, :aggregateId, :payload,
            :correlationId, :occurredAt, :occurredAt)
          """,
          new MapSqlParameterSource()
              .addValue("eventId", UUID.fromString(event.eventId()))
              .addValue("eventType", event.eventType())
              .addValue("schemaVersion", event.schemaVersion())
              .addValue("workspaceId", event.workspaceId())
              .addValue("aggregateId", event.aggregateId())
              .addValue("payload", objectMapper.writeValueAsString(event.payload()))
              .addValue("correlationId", event.correlationId())
              .addValue("occurredAt", Timestamp.from(event.occurredAt())));
    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
      throw new IllegalStateException("payload de evento invalido", failure);
    }
  }

  @Override
  public boolean firstDelivery(String consumer, String eventId) {
    int inserted =
        jdbc.update(
            """
            INSERT INTO inbox_message (consumer, event_id, received_at)
            VALUES (:consumer, :eventId, now())
            ON CONFLICT (consumer, event_id) DO NOTHING
            """,
            new MapSqlParameterSource()
                .addValue("consumer", consumer)
                .addValue("eventId", UUID.fromString(eventId)));
    return inserted == 1;
  }

  /**
   * Reserva um lote de eventos pendentes com lease, para que duas instancias nao publiquem a
   * mesma mensagem ao mesmo tempo.
   */
  List<OutboxEvent> leasePending(int batchSize, Duration leaseDuration, Instant now) {
    return jdbc.query(
        """
        UPDATE outbox_message SET lease_until = :leaseUntil
        WHERE event_id IN (
          SELECT event_id FROM outbox_message
          WHERE published_at IS NULL
            AND next_attempt_at <= :now
            AND (lease_until IS NULL OR lease_until < :now)
          ORDER BY next_attempt_at
          LIMIT :batchSize
          FOR UPDATE SKIP LOCKED
        )
        RETURNING event_id, event_type, schema_version, workspace_id, aggregate_id, payload,
                  correlation_id, occurred_at
        """,
        new MapSqlParameterSource()
            .addValue("leaseUntil", Timestamp.from(now.plus(leaseDuration)))
            .addValue("now", Timestamp.from(now))
            .addValue("batchSize", batchSize),
        (rs, row) -> {
          try {
            Map<String, String> payload =
                objectMapper.readValue(
                    rs.getString("payload"), new TypeReference<Map<String, String>>() {});
            return new OutboxEvent(
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getInt("schema_version"),
                rs.getString("workspace_id"),
                rs.getString("aggregate_id"),
                payload,
                rs.getString("correlation_id"),
                rs.getTimestamp("occurred_at").toInstant());
          } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("payload de evento ilegivel", failure);
          }
        });
  }

  /** Marca como publicado apenas depois da confirmacao do broker, sem retorno (FR-019). */
  void markPublished(String eventId, Instant at) {
    jdbc.update(
        "UPDATE outbox_message SET published_at = :at, lease_until = NULL"
            + " WHERE event_id = :id AND published_at IS NULL",
        new MapSqlParameterSource()
            .addValue("at", Timestamp.from(at))
            .addValue("id", UUID.fromString(eventId)));
  }

  /**
   * Mensagem devolvida pelo broker ({@code basic.return}).
   *
   * <p>O retorno e autoritativo e independe de ordem: o RabbitMQ confirma mesmo uma mensagem
   * sem binding, e o retorno pode chegar depois do confirm. Por isso aqui o {@code published_at}
   * e limpo, e nao apenas ignorado — do contrario uma mensagem que voltou poderia ficar
   * marcada como publicada e desaparecer da operacao (FR-019).
   */
  void markReturned(String eventId, String reason, Instant nextAttemptAt) {
    jdbc.update(
        """
        UPDATE outbox_message
        SET published_at = NULL,
            attempts = attempts + 1,
            last_error = :reason,
            next_attempt_at = :nextAttemptAt,
            lease_until = NULL
        WHERE event_id = :id
        """,
        new MapSqlParameterSource()
            .addValue("reason", reason)
            .addValue("nextAttemptAt", Timestamp.from(nextAttemptAt))
            .addValue("id", UUID.fromString(eventId)));
  }

  /** Reagenda com atraso e registra o erro. Retry e limitado e classificado (FR-021). */
  void markFailed(String eventId, String error, Instant nextAttemptAt) {
    jdbc.update(
        """
        UPDATE outbox_message
        SET attempts = attempts + 1,
            last_error = :error,
            next_attempt_at = :nextAttemptAt,
            lease_until = NULL
        WHERE event_id = :id
        """,
        new MapSqlParameterSource()
            .addValue("error", error)
            .addValue("nextAttemptAt", Timestamp.from(nextAttemptAt))
            .addValue("id", UUID.fromString(eventId)));
  }

  /** Observabilidade minima da outbox (NFR-003). */
  public long pendingCount() {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_message WHERE published_at IS NULL", Map.of(), Long.class);
    return count == null ? 0 : count;
  }
}
