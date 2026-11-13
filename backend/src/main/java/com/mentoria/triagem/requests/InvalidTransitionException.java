package com.mentoria.triagem.requests;

/** Transicao fora da tabela de estados da Spec 001 (FR-008). */
public class InvalidTransitionException extends RuntimeException {

  public InvalidTransitionException(String message) {
    super(message);
  }
}
