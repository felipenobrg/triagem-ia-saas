package com.mentoria.triagem.support;

import com.mentoria.triagem.conversations.ConversationJudge;
import com.mentoria.triagem.conversations.JudgeOutcome;
import com.mentoria.triagem.conversations.JudgeUnavailableException;
import com.mentoria.triagem.conversations.TurnView;
import java.util.List;

/** JEV controlado pelo teste: devolve um veredito fixo, invalido, ou falha. */
public class StubJudge implements ConversationJudge {

  private JudgeOutcome next;
  private boolean fail;

  public static StubJudge returning(JudgeOutcome outcome) {
    StubJudge stub = new StubJudge();
    stub.next = outcome;
    return stub;
  }

  public static StubJudge failing() {
    StubJudge stub = new StubJudge();
    stub.fail = true;
    return stub;
  }

  public void setNext(JudgeOutcome outcome) {
    this.next = outcome;
    this.fail = false;
  }

  @Override
  public JudgeOutcome evaluate(List<TurnView> turns) {
    if (fail) {
      throw new JudgeUnavailableException("provedor do JEV indisponivel (teste)");
    }
    return next;
  }
}
