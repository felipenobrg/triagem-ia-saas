package com.mentoria.triagem.identity;

/** Tentativa de remover ou rebaixar o ultimo admin ativo (FR-004). */
public class LastAdminException extends RuntimeException {

  public LastAdminException(String message) {
    super(message);
  }
}
