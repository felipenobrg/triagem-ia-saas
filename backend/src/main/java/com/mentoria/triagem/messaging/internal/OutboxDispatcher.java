package com.mentoria.triagem.messaging.internal;

import com.mentoria.triagem.messaging.EventTypes;
import com.mentoria.triagem.messaging.OutboxEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publica o que esta na outbox. Esta classe existe para que a transacao de negocio nunca
 * dependa do broker.
 *
 * <p>A mensagem so e marcada como publicada depois de publisher confirm <b>e</b> de nenhum
 * {@code basic.return} ter chegado para ela: com {@code mandatory=true}, mensagem sem binding
 * volta e precisa continuar pendente, mesmo tendo sido confirmada (FR-019, cenario 6).
 */
@Component
@ConditionalOnProperty(name = "app.messaging.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
  private static final int BATCH_SIZE = 50;
  private static final Duration LEASE = Duration.ofSeconds(30);
  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

  private final JdbcOutboxStore store;
  private final RabbitTemplate rabbitTemplate;
  private final Clock clock;
  private final Set<String> returnedEventIds = ConcurrentHashMap.newKeySet();

  public OutboxDispatcher(JdbcOutboxStore store, RabbitTemplate rabbitTemplate, Clock clock) {
    this.store = store;
    this.rabbitTemplate = rabbitTemplate;
    this.clock = clock;
    this.rabbitTemplate.setMandatory(true);
    this.rabbitTemplate.setReturnsCallback(this::onReturned);
  }

  private void onReturned(ReturnedMessage returned) {
    String eventId = returned.getMessage().getMessageProperties().getMessageId();
    log.error(
        "outbox_unroutable routingKey={} reason={} eventId={}",
        returned.getRoutingKey(),
        returned.getReplyText(),
        eventId);
    if (eventId == null) {
      return;
    }
    returnedEventIds.add(eventId);
    // O retorno manda, independente de chegar antes ou depois do confirm.
    store.markReturned(
        eventId,
        "mensagem retornada pelo broker: sem binding para " + returned.getRoutingKey(),
        clock.instant().plus(RETRY_DELAY));
  }

  @Scheduled(fixedDelayString = "${app.messaging.dispatch-interval-ms:500}")
  public void dispatchPending() {
    Instant now = clock.instant();
    List<OutboxEvent> batch = store.leasePending(BATCH_SIZE, LEASE, now);
    for (OutboxEvent event : batch) {
      publishOne(event);
    }
  }

  private void publishOne(OutboxEvent event) {
    CorrelationData correlation = new CorrelationData(event.eventId());
    try {
      rabbitTemplate.convertAndSend(
          EventTypes.EXCHANGE,
          event.routingKey(),
          event,
          message -> {
            message.getMessageProperties().setMessageId(event.eventId());
            message.getMessageProperties().setCorrelationId(event.correlationId());
            return message;
          },
          correlation);

      var confirm = correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      if (confirm == null || !confirm.isAck()) {
        store.markFailed(
            event.eventId(), "sem publisher confirm", clock.instant().plus(RETRY_DELAY));
        return;
      }
      if (returnedEventIds.remove(event.eventId())) {
        // O retorno chegou antes do confirm: markReturned ja reagendou o envio.
        return;
      }
      store.markPublished(event.eventId(), clock.instant());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      store.markFailed(event.eventId(), "interrompido", clock.instant().plus(RETRY_DELAY));
    } catch (Exception failure) {
      log.warn("outbox_publish_failed eventId={} erro={}", event.eventId(), failure.getMessage());
      store.markFailed(
          event.eventId(), String.valueOf(failure.getMessage()), clock.instant().plus(RETRY_DELAY));
    }
  }
}
