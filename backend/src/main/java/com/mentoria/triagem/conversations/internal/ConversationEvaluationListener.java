package com.mentoria.triagem.conversations.internal;

import com.mentoria.triagem.conversations.ConversationService;
import com.mentoria.triagem.messaging.EventTypes;
import com.mentoria.triagem.messaging.InboxGuard;
import com.mentoria.triagem.messaging.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor da avaliacao do JEV (FR3-008). E este consumidor que torna a avaliacao
 * assincrona: o usuario recebe a resposta do assistente sem esperar o julgamento.
 *
 * <p>O veredito so e aplicado se o turno avaliado ainda for o ultimo; caso contrario fica
 * OBSOLETE (FR3-011). A reentrega e tratada pela inbox (FR-020).
 */
@Component
public class ConversationEvaluationListener {

  private static final Logger log = LoggerFactory.getLogger(ConversationEvaluationListener.class);
  private static final String CONSUMER = "conversation-evaluation";

  private final ConversationService conversations;
  private final InboxGuard inbox;

  public ConversationEvaluationListener(ConversationService conversations, InboxGuard inbox) {
    this.conversations = conversations;
    this.inbox = inbox;
  }

  @RabbitListener(queues = EventTypes.QUEUE_CONVERSATION_EVALUATION)
  public void onEvaluationRequested(OutboxEvent event) {
    if (!EventTypes.CONVERSATION_EVALUATION_REQUESTED.equals(event.eventType())
        || event.schemaVersion() != 1) {
      throw new IllegalArgumentException(
          "evento nao suportado: " + event.eventType() + " v" + event.schemaVersion());
    }
    if (!inbox.firstDelivery(CONSUMER, event.eventId())) {
      log.debug("evaluation_event_duplicate eventId={}", event.eventId());
      return;
    }
    String conversationId = event.payload().get("conversationId");
    int turnSequence = Integer.parseInt(event.payload().get("turnSequence"));
    conversations.applyJudgeVerdict(event.workspaceId(), conversationId, turnSequence);
  }
}
