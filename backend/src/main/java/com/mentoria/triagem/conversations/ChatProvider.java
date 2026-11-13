package com.mentoria.triagem.conversations;

import java.util.List;

/** Porta do agente de conversa (FR3-012). Fake deterministico e o padrao. */
public interface ChatProvider {

  /**
   * @param history turnos anteriores, em ordem
   * @return texto da resposta do assistente
   * @throws ChatProviderException falha ou indisponibilidade; o dominio escala (FR3-019)
   */
  String reply(List<TurnView> history, String latestMessage);
}
