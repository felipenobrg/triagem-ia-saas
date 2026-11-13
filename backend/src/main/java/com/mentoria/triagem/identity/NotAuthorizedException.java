package com.mentoria.triagem.identity;

/** Falta de membership ativo ou papel insuficiente (FR-002). */
public class NotAuthorizedException extends RuntimeException {

  public NotAuthorizedException(String message) {
    super(message);
  }
}
