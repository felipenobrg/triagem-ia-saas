package com.mentoria.triagem.messaging;

/** Tipos de evento e nomes de fila do exercicio. */
public final class EventTypes {

  public static final String TRIAGE_REQUESTED = "TriageRequested";
  public static final String CONVERSATION_EVALUATION_REQUESTED = "ConversationEvaluationRequested";
  public static final String CONVERSATION_ESCALATED = "ConversationEscalated";

  public static final String EXCHANGE = "triagem.events";
  public static final String DEAD_LETTER_EXCHANGE = "triagem.dlx";
  public static final String QUEUE_TRIAGE = "triagem.triage";
  public static final String QUEUE_CONVERSATION_EVALUATION = "triagem.conversation-evaluation";
  public static final String QUEUE_DEAD_LETTER = "triagem.dlq";

  private EventTypes() {}
}
