package com.mentoria.triagem.requests;

import java.util.List;

/**
 * Contexto do problema trazido por um escalonamento de conversa (FR3-015).
 * {@code generated=false} significa que o contexto nao pode ser produzido: o chamado
 * entra sem sugestao e o tecnico le a conversa.
 */
public record ProblemContext(
    String summary,
    List<String> symptoms,
    List<String> attemptedSteps,
    Category suggestedCategory,
    Urgency suggestedUrgency,
    String escalationReason,
    boolean generated) {

  public static ProblemContext unavailable(String escalationReason) {
    return new ProblemContext(null, List.of(), List.of(), null, null, escalationReason, false);
  }
}
