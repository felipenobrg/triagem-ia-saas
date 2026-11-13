package com.mentoria.triagem.conversations;

/** Ciclo de vida de um veredito (Spec 003, secao 7). */
public enum VerdictState {
  APPLIED,
  REJECTED_INVALID,
  FAILED,
  /** Turno novo, escalonamento ou confirmacao venceram a corrida (FR3-011). */
  OBSOLETE
}
