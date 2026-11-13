package com.mentoria.triagem.support;

import com.mentoria.triagem.conversations.ConversationJudge;
import com.mentoria.triagem.conversations.JudgeOutcome;
import com.mentoria.triagem.conversations.TurnView;
import java.util.List;

/** Envolve um JEV para contar consultas: permite provar que uma rota nao consultou o modelo. */
public class CountingJudge implements ConversationJudge {

  private final ConversationJudge delegate;
  private int calls;

  public CountingJudge(ConversationJudge delegate) {
    this.delegate = delegate;
  }

  @Override
  public JudgeOutcome evaluate(List<TurnView> turns) {
    calls++;
    return delegate.evaluate(turns);
  }

  public int calls() {
    return calls;
  }
}
