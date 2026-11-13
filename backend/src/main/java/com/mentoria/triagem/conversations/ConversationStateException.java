package com.mentoria.triagem.conversations;

/** Operacao incompativel com o estado atual da conversa. */
public class ConversationStateException extends RuntimeException {

  private final String code;

  public ConversationStateException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
