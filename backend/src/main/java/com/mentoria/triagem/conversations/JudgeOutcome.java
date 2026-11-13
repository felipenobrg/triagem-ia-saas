package com.mentoria.triagem.conversations;

import com.mentoria.triagem.requests.Category;
import com.mentoria.triagem.requests.Urgency;

/**
 * Saida bruta do JEV. Nao e confiavel: o servidor valida rota, motivo, confianca e limites
 * antes de qualquer transicao (FR3-009).
 */
public record JudgeOutcome(
    Verdict verdict,
    JudgeReasonCode reasonCode,
    double confidence,
    String summary,
    Category suggestedCategory,
    Urgency suggestedUrgency) {

  public static JudgeOutcome continueConversation(JudgeReasonCode reason, double confidence) {
    return new JudgeOutcome(Verdict.CONTINUE, reason, confidence, null, null, null);
  }

  public static JudgeOutcome likelyResolved(double confidence) {
    return new JudgeOutcome(
        Verdict.LIKELY_RESOLVED, JudgeReasonCode.USER_CONFIRMED_FIX, confidence, null, null, null);
  }

  public static JudgeOutcome escalate(
      JudgeReasonCode reason,
      double confidence,
      String summary,
      Category category,
      Urgency urgency) {
    return new JudgeOutcome(Verdict.ESCALATE, reason, confidence, summary, category, urgency);
  }
}
