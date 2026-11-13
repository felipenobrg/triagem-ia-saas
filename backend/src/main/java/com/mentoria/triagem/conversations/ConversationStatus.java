package com.mentoria.triagem.conversations;

/** Estados da conversa (Spec 003, secao 7). */
public enum ConversationStatus {
  ACTIVE,
  AWAITING_CONFIRMATION,
  RESOLVED_CONFIRMED,
  ESCALATED,
  CLOSED_INACTIVE;

  /** RESOLVED_CONFIRMED e ESCALATED sao terminais; CLOSED_INACTIVE ainda pode escalar. */
  public boolean isTerminal() {
    return this == RESOLVED_CONFIRMED || this == ESCALATED;
  }
}
