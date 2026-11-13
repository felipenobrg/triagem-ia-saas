package com.mentoria.triagem.triage.internal;

import com.mentoria.triagem.requests.Category;
import com.mentoria.triagem.requests.Urgency;
import com.mentoria.triagem.triage.TriageOutcome;
import com.mentoria.triagem.triage.TriageProvider;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Provedor falso e deterministico (FR-014). Regras simples e explicaveis: nenhuma
 * qualidade de classificacao e alegada, e o conjunto de avaliacao (FR-017) ainda nao existe.
 */
@Component
public class FakeTriageProvider implements TriageProvider {

  private static final int MIN_USEFUL_LENGTH = 15;

  @Override
  public TriageOutcome analyze(String text) {
    String normalized = text.toLowerCase(Locale.ROOT).trim();

    if (normalized.length() < MIN_USEFUL_LENGTH || !hasTechnicalSignal(normalized)) {
      return TriageOutcome.clarification(
          "Pode descrever o que voce tentou fazer e qual mensagem apareceu?");
    }

    Category category = categoryOf(normalized);
    Urgency urgency = urgencyOf(normalized);
    String summary = text.length() <= 120 ? text : text.substring(0, 117) + "...";
    return TriageOutcome.humanReview(category, urgency, summary);
  }

  private static boolean hasTechnicalSignal(String text) {
    String[] signals = {
      "acesso", "login", "senha", "erro", "falha", "negado", "ambiente", "api", "banco",
      "deploy", "permissao", "instalar", "configurar", "lento", "fora do ar", "indisponivel",
      "nao consigo", "nao funciona o", "timeout", "token"
    };
    for (String signal : signals) {
      if (text.contains(signal)) {
        return true;
      }
    }
    return false;
  }

  private static Category categoryOf(String text) {
    if (contains(text, "acesso", "login", "senha", "permissao", "negado", "token")) {
      return Category.ACCESS;
    }
    if (contains(text, "fora do ar", "indisponivel", "erro", "falha", "timeout", "lento")) {
      return Category.INCIDENT;
    }
    if (contains(text, "como", "duvida", "pode explicar")) {
      return Category.QUESTION;
    }
    if (contains(text, "instalar", "configurar", "criar", "liberar", "solicitar")) {
      return Category.SERVICE_REQUEST;
    }
    return Category.OTHER;
  }

  /** Conservador: sem evidencia explicita, nao eleva urgencia (Spec 001, secao 4). */
  private static Urgency urgencyOf(String text) {
    if (contains(text, "fora do ar", "producao parada", "ninguem consegue", "critico")) {
      return Urgency.CRITICAL;
    }
    if (contains(text, "bloqueado", "nao consigo trabalhar", "equipe toda", "urgente")) {
      return Urgency.HIGH;
    }
    return Urgency.NORMAL;
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
