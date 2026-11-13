package com.mentoria.triagem.triage;

import com.mentoria.triagem.requests.Category;
import com.mentoria.triagem.requests.Urgency;

/**
 * Saida bruta do provedor de triagem. Nao e confiavel: o servidor valida antes de
 * transformar em sugestao (FR-012).
 */
public record TriageOutcome(
    TriageRoute route, Category category, Urgency urgency, String summary, String question) {

  public static TriageOutcome humanReview(Category category, Urgency urgency, String summary) {
    return new TriageOutcome(TriageRoute.HUMAN_REVIEW, category, urgency, summary, null);
  }

  public static TriageOutcome clarification(String question) {
    return new TriageOutcome(TriageRoute.REQUEST_CLARIFICATION, null, null, null, question);
  }
}
