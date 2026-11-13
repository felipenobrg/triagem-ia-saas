package com.mentoria.triagem.support;

import com.mentoria.triagem.conversations.ChatProvider;
import com.mentoria.triagem.conversations.ChatProviderException;
import com.mentoria.triagem.conversations.TurnView;
import java.util.List;

/** Atendente controlado pelo teste: resposta fixa, falha, ou timeout ambiguo. */
public class StubChatProvider implements ChatProvider {

  private String reply = "Pode detalhar o erro que aparece?";
  private boolean fail;
  private boolean outcomeUnknown;

  public static StubChatProvider replying(String reply) {
    StubChatProvider stub = new StubChatProvider();
    stub.reply = reply;
    return stub;
  }

  public static StubChatProvider unavailable() {
    StubChatProvider stub = new StubChatProvider();
    stub.fail = true;
    return stub;
  }

  public static StubChatProvider ambiguousTimeout() {
    StubChatProvider stub = new StubChatProvider();
    stub.fail = true;
    stub.outcomeUnknown = true;
    return stub;
  }

  @Override
  public String reply(List<TurnView> history, String latestMessage) {
    if (fail) {
      throw new ChatProviderException("provedor de conversa indisponivel (teste)", outcomeUnknown);
    }
    return reply;
  }
}
