package com.mentoria.triagem.conversations.internal;

import com.mentoria.triagem.conversations.ChatProvider;
import com.mentoria.triagem.conversations.TurnView;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Atendente falso e deterministico (FR3-012, NFR3-001). Faz uma pergunta objetiva quando o
 * relato esta vago e sugere uma verificacao quando reconhece um sintoma conhecido.
 *
 * <p>Nao resolve nada de verdade e nunca pede credencial. Nenhuma taxa de resolucao e alegada.
 */
@Component
public class FakeChatProvider implements ChatProvider {

  @Override
  public String reply(List<TurnView> history, String latestMessage) {
    String text = latestMessage.toLowerCase(Locale.ROOT);
    long assistantTurns = history.stream().filter(t -> t.author().name().equals("ASSISTANT")).count();

    if (text.length() < 15) {
      return "Pode detalhar o que voce tentou fazer e qual mensagem apareceu na tela?";
    }
    if (contains(text, "acesso negado", "sem acesso", "nao consigo acessar", "permissao")) {
      if (assistantTurns == 0) {
        return "Qual mensagem exata aparece e em qual ambiente? Confira tambem se sua conta "
            + "esta no grupo dev-sandbox em Perfil > Grupos.";
      }
      return "Obrigado. Se o grupo ja esta correto e o erro continua, isso costuma precisar de "
          + "uma pessoa da equipe tecnica para verificar a liberacao.";
    }
    if (contains(text, "lento", "timeout", "fora do ar", "indisponivel")) {
      return "Isso aparece em todas as telas ou em uma especifica? E desde quando acontece?";
    }
    if (contains(text, "resolveu", "funcionou", "consegui", "era isso")) {
      return "Otimo. Confirma que o problema esta resolvido?";
    }
    return "Entendi. Pode me dizer o que acontece quando voce tenta de novo e se aparece "
        + "alguma mensagem de erro?";
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
