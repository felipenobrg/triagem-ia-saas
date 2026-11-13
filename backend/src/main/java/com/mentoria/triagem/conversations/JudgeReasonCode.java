package com.mentoria.triagem.conversations;

/** Motivos aceitos do JEV. Qualquer outro valor e saida invalida (Spec 003, secao 4). */
public enum JudgeReasonCode {
  USER_REPORTED_STILL_BROKEN,
  NEEDS_HUMAN_ACTION,
  OUT_OF_ASSISTANT_SCOPE,
  LOW_CONFIDENCE,
  USER_CONFIRMED_FIX,
  MORE_INFO_USEFUL
}
