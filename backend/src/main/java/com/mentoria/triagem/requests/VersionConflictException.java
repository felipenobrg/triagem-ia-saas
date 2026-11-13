package com.mentoria.triagem.requests;

/** expectedVersion divergente: a escrita perdeu a corrida (NFR-004). */
public class VersionConflictException extends RuntimeException {

  public VersionConflictException(String message) {
    super(message);
  }
}
