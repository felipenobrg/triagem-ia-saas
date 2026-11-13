package com.mentoria.triagem.triage;

import com.mentoria.triagem.requests.RequestService;
import com.mentoria.triagem.requests.RequestView;
import com.mentoria.triagem.requests.Suggestion;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Aplica a triagem a uma solicitacao. Validacao de dominio acontece aqui (FR-012): o que
 * o provedor devolve nunca e tratado como verdade.
 *
 * <p><b>Limitacao desta implementacao:</b> a execucao e sincrona e em processo. Outbox,
 * RabbitMQ, inbox, retry e DLQ (FR-018 a FR-022) ainda nao foram implementados.
 */
@Service
public class TriageService {

  private static final int MAX_SUMMARY = 500;
  private static final int MAX_QUESTION = 300;
  private static final String[] SECRET_WORDS = {"senha", "password", "token", "chave privada", "secret"};

  private final TriageProvider provider;
  private final RequestService requests;
  private final Clock clock;

  public TriageService(TriageProvider provider, RequestService requests, Clock clock) {
    this.provider = provider;
    this.requests = requests;
    this.clock = clock;
  }

  /**
   * Executa uma tentativa de triagem. Devolve a solicitacao atualizada, ou vazio quando uma
   * decisao humana venceu a corrida e o resultado ficou obsoleto (cenario 13 da Spec 001).
   */
  public Optional<RequestView> runTriage(String workspaceId, String requestId) {
    RequestView scheduled = requests.scheduleTriage(workspaceId, requestId);
    long attemptVersion = scheduled.version();

    TriageOutcome outcome;
    try {
      outcome = provider.analyze(scheduled.originalText());
    } catch (RuntimeException failure) {
      return requests.applyHumanReviewRoute(
          workspaceId, requestId, attemptVersion, null, "AI_UNAVAILABLE");
    }

    String validationError = validate(outcome);
    if (validationError != null) {
      return requests.applyHumanReviewRoute(
          workspaceId, requestId, attemptVersion, null, "AI_INVALID_OUTPUT");
    }

    if (outcome.route() == TriageRoute.HUMAN_REVIEW) {
      Suggestion suggestion =
          new Suggestion(
              outcome.category(), outcome.urgency(), outcome.summary(), "FAKE_TRIAGE", clock.instant());
      return requests.applyHumanReviewRoute(workspaceId, requestId, attemptVersion, suggestion, null);
    }
    return requests.applyClarificationRoute(
        workspaceId, requestId, attemptVersion, outcome.question());
  }

  /** @return null quando a saida e valida; caso contrario, o motivo da rejeicao. */
  String validate(TriageOutcome outcome) {
    if (outcome == null || outcome.route() == null) {
      return "rota ausente";
    }
    if (outcome.route() == TriageRoute.HUMAN_REVIEW) {
      if (outcome.category() == null || outcome.urgency() == null) {
        return "categoria e urgencia obrigatorias na rota humana";
      }
      if (isBlank(outcome.summary()) || outcome.summary().length() > MAX_SUMMARY) {
        return "resumo ausente ou acima do limite";
      }
      return null;
    }
    if (isBlank(outcome.question()) || outcome.question().length() > MAX_QUESTION) {
      return "pergunta ausente ou acima do limite";
    }
    if (asksForSecret(outcome.question())) {
      return "pergunta pede credencial";
    }
    return null;
  }

  private static boolean asksForSecret(String text) {
    String normalized = text.toLowerCase(Locale.ROOT);
    for (String word : SECRET_WORDS) {
      if (normalized.contains(word)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
