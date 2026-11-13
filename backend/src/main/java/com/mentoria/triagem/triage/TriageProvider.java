package com.mentoria.triagem.triage;

/** Porta do agente de triagem (FR-014). O fake e o padrao local e obrigatorio em teste. */
public interface TriageProvider {

  /**
   * @throws TriageProviderException falha do provedor; o dominio encaminha ao humano (FR-024)
   */
  TriageOutcome analyze(String text);
}
