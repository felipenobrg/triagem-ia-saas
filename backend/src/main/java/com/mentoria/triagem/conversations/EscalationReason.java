package com.mentoria.triagem.conversations;

/** Motivos seguros de escalonamento, expostos na API e no chamado (FR3-019). */
public final class EscalationReason {

  public static final String REQUESTER_ASKED_HUMAN = "REQUESTER_ASKED_HUMAN";
  public static final String USER_REPORTED_STILL_BROKEN = "USER_REPORTED_STILL_BROKEN";
  public static final String JUDGE_ESCALATED = "JUDGE_ESCALATED";
  public static final String JUDGE_INVALID_OUTPUT = "JUDGE_INVALID_OUTPUT";
  public static final String JUDGE_UNAVAILABLE = "JUDGE_UNAVAILABLE";
  public static final String TURN_LIMIT_REACHED = "TURN_LIMIT_REACHED";
  public static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
  public static final String AI_INVALID_OUTPUT = "AI_INVALID_OUTPUT";
  public static final String AI_BUDGET_BLOCKED = "AI_BUDGET_BLOCKED";
  public static final String ADMIN_FORCED = "ADMIN_FORCED";

  private EscalationReason() {}
}
