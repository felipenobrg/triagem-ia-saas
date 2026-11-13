package com.mentoria.triagem.identity;

/** Papeis de negocio mantidos pela aplicacao (FR-002): nunca lidos do token. */
public enum Role {
  REQUESTER,
  AGENT,
  ADMIN;

  public boolean isAgentOrAdmin() {
    return this == AGENT || this == ADMIN;
  }
}
