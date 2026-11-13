package com.mentoria.triagem.triage;

/** Falha do provedor de IA. Nunca vira sugestao: encaminha ao humano sem sugestao (FR-024). */
public class TriageProviderException extends RuntimeException {

  public TriageProviderException(String message) {
    super(message);
  }
}
