package com.mentoria.triagem.conversations;

/** Conversa ausente, de outro autor ou nao escalada: nunca confirma existencia (BR3-010). */
public class ConversationNotFoundException extends RuntimeException {

  public ConversationNotFoundException(String message) {
    super(message);
  }
}
