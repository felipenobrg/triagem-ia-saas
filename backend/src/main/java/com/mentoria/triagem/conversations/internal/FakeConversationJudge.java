package com.mentoria.triagem.conversations.internal;

import com.mentoria.triagem.conversations.ConversationJudge;
import com.mentoria.triagem.conversations.JudgeOutcome;
import com.mentoria.triagem.conversations.JudgeReasonCode;
import com.mentoria.triagem.conversations.TurnAuthor;
import com.mentoria.triagem.conversations.TurnView;
import com.mentoria.triagem.requests.Category;
import com.mentoria.triagem.requests.Urgency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * JEV falso e deterministico (FR3-012, NFR3-001). Julga a conversa, nao o merito do problema.
 *
 * <p>Repare na assimetria exigida pela spec: um sinal de que o usuario ainda esta travado
 * escala; um sinal de que funcionou apenas propoe a confirmacao ao autor, que decide.
 * Nenhuma taxa de acerto deste fake foi medida.
 */
@Component
public class FakeConversationJudge implements ConversationJudge {

  @Override
  public JudgeOutcome evaluate(List<TurnView> turns) {
    Optional<String> lastAuthorMessage =
        turns.stream()
            .filter(turn -> turn.author() == TurnAuthor.REQUESTER)
            .map(TurnView::text)
            .reduce((first, second) -> second);

    if (lastAuthorMessage.isEmpty()) {
      return JudgeOutcome.continueConversation(JudgeReasonCode.MORE_INFO_USEFUL, 0.50);
    }

    String text = lastAuthorMessage.get().toLowerCase(Locale.ROOT);

    if (contains(text, "continua", "ainda nao", "mesmo erro", "nao resolveu", "persiste", "sem sucesso")) {
      return JudgeOutcome.escalate(
          JudgeReasonCode.USER_REPORTED_STILL_BROKEN,
          0.84,
          summarize(turns),
          categoryOf(turns),
          Urgency.HIGH);
    }
    if (contains(text, "preciso que alguem", "falar com alguem", "abrir chamado", "equipe tecnica")) {
      return JudgeOutcome.escalate(
          JudgeReasonCode.NEEDS_HUMAN_ACTION, 0.80, summarize(turns), categoryOf(turns), Urgency.NORMAL);
    }
    if (contains(text, "resolveu", "funcionou", "consegui", "era isso", "deu certo")) {
      return JudgeOutcome.likelyResolved(0.88);
    }
    if (contains(text, "ok", "entendi", "vou tentar")) {
      // Aceitacao ambigua: vale propor a confirmacao, mas com confianca baixa de proposito.
      return JudgeOutcome.likelyResolved(0.55);
    }
    return JudgeOutcome.continueConversation(JudgeReasonCode.MORE_INFO_USEFUL, 0.60);
  }

  private static String summarize(List<TurnView> turns) {
    String first =
        turns.stream()
            .filter(turn -> turn.author() == TurnAuthor.REQUESTER)
            .map(TurnView::text)
            .findFirst()
            .orElse("Conversa sem relato inicial.");
    return first.length() <= 200 ? first : first.substring(0, 197) + "...";
  }

  private static Category categoryOf(List<TurnView> turns) {
    String all =
        turns.stream()
            .filter(turn -> turn.author() == TurnAuthor.REQUESTER)
            .map(turn -> turn.text().toLowerCase(Locale.ROOT))
            .reduce("", (a, b) -> a + " " + b);
    if (contains(all, "acesso", "permissao", "login", "grupo", "negado")) {
      return Category.ACCESS;
    }
    if (contains(all, "erro", "fora do ar", "lento", "timeout", "falha")) {
      return Category.INCIDENT;
    }
    if (contains(all, "instalar", "liberar", "criar", "configurar")) {
      return Category.SERVICE_REQUEST;
    }
    return Category.OTHER;
  }

  private static boolean contains(String text, String... needles) {
    for (String needle : needles) {
      if (text.contains(needle)) {
        return true;
      }
    }
    return false;
  }
}
