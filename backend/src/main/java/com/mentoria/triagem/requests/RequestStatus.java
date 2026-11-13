package com.mentoria.triagem.requests;

/** Estados operacionais da solicitacao (Spec 001, secao 5). */
public enum RequestStatus {
  RECEIVED,
  TRIAGE_PENDING,
  NEEDS_CLARIFICATION,
  NEEDS_REVIEW,
  OPEN,
  IN_PROGRESS,
  RESOLVED;

  public boolean isTerminal() {
    return this == RESOLVED;
  }
}
