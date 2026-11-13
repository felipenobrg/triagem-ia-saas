package com.mentoria.triagem.requests;

/** Mesma chave idempotente com corpo diferente (FR-005). */
public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException(String message) {
    super(message);
  }
}
