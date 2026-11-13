package com.mentoria.triagem.messaging.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentoria.triagem.messaging.EventTypes;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Topologia duravel do broker: exchange por tipo de evento, uma fila por consumidor e DLQ.
 *
 * <p>Routing key inclui a versao do schema ({@code TriageRequested.v1}). Versao desconhecida
 * nao tem binding, entao a mensagem volta pelo {@code basic.return} em vez de ser consumida
 * por um consumidor que tentaria adivinhar o payload (FR-021).
 */
@Configuration
@EnableScheduling
public class RabbitTopology {

  @Bean
  TopicExchange eventsExchange() {
    return new TopicExchange(EventTypes.EXCHANGE, true, false);
  }

  @Bean
  FanoutExchange deadLetterExchange() {
    return new FanoutExchange(EventTypes.DEAD_LETTER_EXCHANGE, true, false);
  }

  @Bean
  Queue deadLetterQueue() {
    return QueueBuilder.durable(EventTypes.QUEUE_DEAD_LETTER).build();
  }

  @Bean
  Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange());
  }

  @Bean
  Queue triageQueue() {
    return QueueBuilder.durable(EventTypes.QUEUE_TRIAGE)
        .withArguments(Map.of("x-dead-letter-exchange", EventTypes.DEAD_LETTER_EXCHANGE))
        .build();
  }

  @Bean
  Binding triageBinding() {
    return BindingBuilder.bind(triageQueue())
        .to(eventsExchange())
        .with(EventTypes.TRIAGE_REQUESTED + ".v1");
  }

  @Bean
  Queue conversationEvaluationQueue() {
    return QueueBuilder.durable(EventTypes.QUEUE_CONVERSATION_EVALUATION)
        .withArguments(Map.of("x-dead-letter-exchange", EventTypes.DEAD_LETTER_EXCHANGE))
        .build();
  }

  @Bean
  Binding conversationEvaluationBinding() {
    return BindingBuilder.bind(conversationEvaluationQueue())
        .to(eventsExchange())
        .with(EventTypes.CONVERSATION_EVALUATION_REQUESTED + ".v1");
  }

  @Bean
  MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
    return new Jackson2JsonMessageConverter(objectMapper);
  }
}
