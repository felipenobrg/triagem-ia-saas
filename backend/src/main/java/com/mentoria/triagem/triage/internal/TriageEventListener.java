package com.mentoria.triagem.triage.internal;

import com.mentoria.triagem.messaging.EventTypes;
import com.mentoria.triagem.messaging.InboxGuard;
import com.mentoria.triagem.messaging.OutboxEvent;
import com.mentoria.triagem.triage.TriageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor da analise inicial (FR-020). A entrega e at-least-once: a inbox garante que o
 * efeito de dominio aconteca uma vez, e o ack so ocorre depois que o metodo retorna sem erro.
 *
 * <p>O evento traz apenas IDs: os dados autorizados sao reabertos pelo requestId, e o
 * workspaceId do evento e conferido pelo proprio servico (BR-005).
 */
@Component
public class TriageEventListener {

  private static final Logger log = LoggerFactory.getLogger(TriageEventListener.class);
  private static final String CONSUMER = "triage";

  private final TriageService triage;
  private final InboxGuard inbox;

  public TriageEventListener(TriageService triage, InboxGuard inbox) {
    this.triage = triage;
    this.inbox = inbox;
  }

  @RabbitListener(queues = EventTypes.QUEUE_TRIAGE)
  public void onTriageRequested(OutboxEvent event) {
    if (!EventTypes.TRIAGE_REQUESTED.equals(event.eventType()) || event.schemaVersion() != 1) {
      // Versao ou tipo desconhecido: nao tentar adivinhar o payload. Vai para a DLQ.
      throw new IllegalArgumentException(
          "evento nao suportado: " + event.eventType() + " v" + event.schemaVersion());
    }
    if (!inbox.firstDelivery(CONSUMER, event.eventId())) {
      log.debug("triage_event_duplicate eventId={}", event.eventId());
      return;
    }
    String requestId = event.payload().get("requestId");
    triage.runTriage(event.workspaceId(), requestId);
  }
}
