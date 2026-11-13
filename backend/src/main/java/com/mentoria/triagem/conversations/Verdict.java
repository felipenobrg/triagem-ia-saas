package com.mentoria.triagem.conversations;

/** As tres unicas saidas do JEV (FR3-009). Ele nao pode encerrar nada (FR3-013). */
public enum Verdict {
  CONTINUE,
  LIKELY_RESOLVED,
  ESCALATE
}
