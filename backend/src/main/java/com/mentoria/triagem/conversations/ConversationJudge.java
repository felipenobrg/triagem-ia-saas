package com.mentoria.triagem.conversations;

import java.util.List;

/**
 * Porta do JEV: avaliador separado do agente de conversa (FR3-012, ADR-0007). Ele julga a
 * conversa, nunca o merito do problema, e nao encerra nada (FR3-013).
 */
public interface ConversationJudge {

  /**
   * @throws JudgeUnavailableException falha ou indisponibilidade; a conversa escala (FR3-019)
   */
  JudgeOutcome evaluate(List<TurnView> turns);
}
