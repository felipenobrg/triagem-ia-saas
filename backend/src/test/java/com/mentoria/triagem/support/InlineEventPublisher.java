package com.mentoria.triagem.support;

import com.mentoria.triagem.messaging.EventPublisher;
import com.mentoria.triagem.messaging.EventTypes;
import com.mentoria.triagem.messaging.OutboxEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Publicador de teste. Registra tudo que foi publicado e entrega na hora apenas os eventos de
 * avaliacao do JEV, para que os testes de dominio exercitem a cadeia turno -> veredito sem
 * broker.
 *
 * <p>Eventos de triagem ficam apenas registrados: e assim que o sistema real se comporta
 * (quem cria a solicitacao nao espera a IA), e os testes de solicitacao disparam a triagem
 * explicitamente.
 */
public class InlineEventPublisher implements EventPublisher {

  private final List<OutboxEvent> published = new ArrayList<>();
  private Consumer<OutboxEvent> evaluationHandler = event -> {};

  public void onEvaluationRequested(Consumer<OutboxEvent> handler) {
    this.evaluationHandler = handler;
  }

  @Override
  public void publish(OutboxEvent event) {
    published.add(event);
    if (EventTypes.CONVERSATION_EVALUATION_REQUESTED.equals(event.eventType())) {
      evaluationHandler.accept(event);
    }
  }

  public List<OutboxEvent> published() {
    return List.copyOf(published);
  }

  public List<OutboxEvent> publishedOfType(String eventType) {
    return published.stream().filter(event -> event.eventType().equals(eventType)).toList();
  }
}
