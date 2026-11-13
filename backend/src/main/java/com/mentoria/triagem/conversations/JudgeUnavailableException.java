package com.mentoria.triagem.conversations;

/** Falha do JEV. A duvida sobre a propria IA favorece a pessoa: escala (FR3-019). */
public class JudgeUnavailableException extends RuntimeException {

  public JudgeUnavailableException(String message) {
    super(message);
  }
}
