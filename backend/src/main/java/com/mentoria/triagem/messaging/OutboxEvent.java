package com.mentoria.triagem.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Evento interno. O payload carrega apenas IDs, sequencia e correlation: nunca texto de
 * solicitacao, de conversa ou prompt (BR-008, BR3-009).
 */
public record OutboxEvent(
    String eventId,
    String eventType,
    int schemaVersion,
    String workspaceId,
    String aggregateId,
    Map<String, String> payload,
    String correlationId,
    Instant occurredAt) {

  public static OutboxEvent of(
      String eventType,
      String workspaceId,
      String aggregateId,
      Map<String, String> payload,
      Instant occurredAt) {
    return new OutboxEvent(
        UUID.randomUUID().toString(),
        eventType,
        1,
        workspaceId,
        aggregateId,
        Map.copyOf(payload),
        UUID.randomUUID().toString(),
        occurredAt);
  }

  public String routingKey() {
    return eventType + ".v" + schemaVersion;
  }
}
