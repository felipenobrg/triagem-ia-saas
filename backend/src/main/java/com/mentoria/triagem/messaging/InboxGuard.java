package com.mentoria.triagem.messaging;

/**
 * Protecao contra reentrega. A entrega e at-least-once (FR-022): o consumidor pergunta se
 * esta e a primeira vez que ve o evento antes de produzir efeito de dominio.
 */
public interface InboxGuard {

  /** @return true apenas na primeira entrega deste evento para este consumidor (FR-020) */
  boolean firstDelivery(String consumer, String eventId);
}
