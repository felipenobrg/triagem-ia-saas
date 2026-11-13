package com.mentoria.triagem.conversations;

/** Estado do turno do assistente (Spec 003, secao 7). */
public enum TurnState {
  GENERATED,
  FAILED,
  /** Timeout depois do envio: a chamada pode ter sido processada e cobrada (FR3-020). */
  OUTCOME_UNKNOWN
}
