package com.mentoria.triagem.messaging;

/**
 * Porta de publicacao. A implementacao de producao grava na outbox dentro da transacao de
 * negocio (FR-018): o broker nunca participa da transacao.
 */
public interface EventPublisher {

  void publish(OutboxEvent event);
}
