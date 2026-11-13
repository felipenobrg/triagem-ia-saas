package com.mentoria.triagem.requests;

/** Recurso ausente ou fora do workspace autorizado: nao confirma existencia (FR-003). */
public class RequestNotFoundException extends RuntimeException {

  public RequestNotFoundException(String message) {
    super(message);
  }
}
