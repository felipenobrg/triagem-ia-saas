package com.mentoria.triagem.conversations;

/** Falha do provedor de conversa. Nenhum texto inventado e gravado como resposta. */
public class ChatProviderException extends RuntimeException {

  private final boolean outcomeUnknown;

  public ChatProviderException(String message, boolean outcomeUnknown) {
    super(message);
    this.outcomeUnknown = outcomeUnknown;
  }

  /** True quando houve timeout depois do envio: pode ter sido processado e cobrado (FR3-020). */
  public boolean outcomeUnknown() {
    return outcomeUnknown;
  }
}
