package com.mentoria.triagem.requests;

import static com.mentoria.triagem.support.DomainFixture.ANA;
import static com.mentoria.triagem.support.DomainFixture.BRUNO;
import static com.mentoria.triagem.support.DomainFixture.CARLA;
import static com.mentoria.triagem.support.DomainFixture.DANI;
import static com.mentoria.triagem.support.DomainFixture.WS_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentoria.triagem.identity.LastAdminException;
import com.mentoria.triagem.identity.Membership;
import com.mentoria.triagem.identity.NotAuthorizedException;
import com.mentoria.triagem.identity.Role;
import com.mentoria.triagem.support.DomainFixture;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Cenarios de aceite da Spec 001. Sem infraestrutura: dominio e fakes deterministicos. */
class RequestFlowTest {

  /** Cenario 15: criacao agenda analise e resultado util vai a NEEDS_REVIEW com sugestao. */
  @Test
  void analiseInicialLevaAFilaHumanaComSugestao() {
    DomainFixture fixture = new DomainFixture();
    RequestView created =
        fixture.requests.create(WS_A, ANA, "Recebo acesso negado ao entrar na conta de teste", "k1");
    fixture.triage.runTriage(WS_A, created.requestId());

    RequestView afterTriage = fixture.requests.get(WS_A, ANA, created.requestId());
    assertThat(afterTriage.status()).isEqualTo(RequestStatus.NEEDS_REVIEW);
    assertThat(afterTriage.suggestion()).isNotNull();
    assertThat(afterTriage.suggestion().category()).isEqualTo(Category.ACCESS);
    assertThat(afterTriage.status()).isNotEqualTo(RequestStatus.OPEN);
  }

  /** Cenario 16: texto vago pede complemento e aceita uma unica rodada automatica. */
  @Test
  void textoVagoPedeComplementoUmaVez() {
    DomainFixture fixture = new DomainFixture();
    RequestView created = fixture.requests.create(WS_A, ANA, "nao funciona", "k1");
    fixture.triage.runTriage(WS_A, created.requestId());

    RequestView pending = fixture.requests.get(WS_A, ANA, created.requestId());
    assertThat(pending.status()).isEqualTo(RequestStatus.NEEDS_CLARIFICATION);
    assertThat(pending.pendingQuestion()).isNotBlank();

    RequestView complemented =
        fixture.requests.addClarification(
            WS_A, ANA, created.requestId(), pending.version(), "nada demais");
    fixture.triage.runTriage(WS_A, complemented.requestId());

    RequestView afterSecondTry = fixture.requests.get(WS_A, ANA, created.requestId());
    assertThat(afterSecondTry.status()).isEqualTo(RequestStatus.NEEDS_REVIEW);
    assertThat(afterSecondTry.humanRoutingReason()).isEqualTo("CLARIFICATION_LIMIT");
    assertThat(afterSecondTry.suggestion()).isNull();
  }

  /** Cenario 17: requester pede pessoa agente sem satisfazer a IA. */
  @Test
  void requesterPedePessoaAgente() {
    DomainFixture fixture = new DomainFixture();
    RequestView created = fixture.requests.create(WS_A, ANA, "nao funciona", "k1");
    fixture.triage.runTriage(WS_A, created.requestId());
    RequestView pending = fixture.requests.get(WS_A, ANA, created.requestId());

    RequestView escalated =
        fixture.requests.requestHumanReview(WS_A, ANA, created.requestId(), pending.version());

    assertThat(escalated.status()).isEqualTo(RequestStatus.NEEDS_REVIEW);
    assertThat(escalated.humanRoutingReason()).isEqualTo("REQUESTER_REQUESTED_HUMAN");
    assertThat(escalated.suggestion()).isNull();
  }

  /** Cenario 3: idempotencia por subject + workspace + operacao + chave. */
  @Test
  void criacaoIdempotente() {
    DomainFixture fixture = new DomainFixture();
    RequestView first = fixture.requests.create(WS_A, ANA, "Acesso negado no ambiente", "k1");
    RequestView repeat = fixture.requests.create(WS_A, ANA, "Acesso negado no ambiente", "k1");

    assertThat(repeat.requestId()).isEqualTo(first.requestId());
    assertThatThrownBy(() -> fixture.requests.create(WS_A, ANA, "Outro texto diferente", "k1"))
        .isInstanceOf(IdempotencyConflictException.class);
  }

  /** Cenario 2: requester so le o que e dele e nao revisa. */
  @Test
  void requesterLimitado() {
    DomainFixture fixture = new DomainFixture();
    RequestView anaRequest = fixture.requests.create(WS_A, ANA, "Acesso negado no ambiente", "k1");
    fixture.memberships.upsert(new Membership(WS_A, "requester-joao", Role.REQUESTER, true));

    assertThat(fixture.requests.list(WS_A, "requester-joao", null)).isEmpty();
    assertThatThrownBy(() -> fixture.requests.get(WS_A, "requester-joao", anaRequest.requestId()))
        .isInstanceOf(RequestNotFoundException.class);
    assertThatThrownBy(
            () ->
                fixture.requests.classifyManually(
                    WS_A,
                    ANA,
                    anaRequest.requestId(),
                    anaRequest.version(),
                    Category.ACCESS,
                    Urgency.HIGH,
                    "motivo"))
        .isInstanceOf(NotAuthorizedException.class);
  }

  /** Cenario 1: acesso cruzado nao confirma existencia. */
  @Test
  void acessoCruzadoNaoRevelaRecurso() {
    DomainFixture fixture = new DomainFixture();
    RequestView anaRequest = fixture.requests.create(WS_A, ANA, "Acesso negado no ambiente", "k1");

    assertThatThrownBy(() -> fixture.requests.get("ws-b", DANI, anaRequest.requestId()))
        .isInstanceOf(RequestNotFoundException.class);
  }

  /** Cenario 13: decisao humana vence resultado tardio da IA; tentativa fica obsoleta. */
  @Test
  void decisaoManualVenceResultadoTardio() {
    DomainFixture fixture = new DomainFixture();
    RequestView created = fixture.requests.create(WS_A, ANA, "Acesso negado no ambiente", "k1");
    RequestView scheduled = fixture.requests.scheduleTriage(WS_A, created.requestId());
    long attemptVersion = scheduled.version();

    RequestView classified =
        fixture.requests.classifyManually(
            WS_A,
            BRUNO,
            created.requestId(),
            scheduled.version(),
            Category.ACCESS,
            Urgency.HIGH,
            "classificacao manual durante indisponibilidade");

    Optional<RequestView> lateResult =
        fixture.requests.applyHumanReviewRoute(
            WS_A,
            created.requestId(),
            attemptVersion,
            new Suggestion(Category.OTHER, Urgency.LOW, "resumo tardio", "FAKE", fixture.clock.instant()),
            null);

    assertThat(lateResult).isEmpty();
    RequestView current = fixture.requests.get(WS_A, BRUNO, created.requestId());
    assertThat(current.status()).isEqualTo(RequestStatus.OPEN);
    assertThat(current.version()).isEqualTo(classified.version());
    assertThat(current.category()).isEqualTo(Category.ACCESS);
  }

  /** Cenario 4: duas revisoes na mesma versao; uma vence, a outra conflita. */
  @Test
  void revisoesConcorrentes() {
    DomainFixture fixture = new DomainFixture();
    RequestView created = fixture.requests.create(WS_A, ANA, "Acesso negado no ambiente", "k1");
    fixture.triage.runTriage(WS_A, created.requestId());
    RequestView inReview = fixture.requests.get(WS_A, BRUNO, created.requestId());

    fixture.requests.review(
        WS_A, BRUNO, created.requestId(), inReview.version(), ReviewDecision.APPROVE, null, null);

    assertThatThrownBy(
            () ->
                fixture.requests.review(
                    WS_A,
                    CARLA,
                    created.requestId(),
                    inReview.version(),
                    ReviewDecision.APPROVE,
                    null,
                    null))
        .isInstanceOf(VersionConflictException.class);
  }

  /** Cenario 11: aprovacao registra sugerido e aprovado e so entao abre atendimento. */
  @Test
  void aprovacaoHumanaAbreAtendimento() {
    DomainFixture fixture = new DomainFixture();
    RequestView created =
        fixture.requests.create(WS_A, ANA, "Acesso negado ao entrar na conta de teste", "k1");
    fixture.triage.runTriage(WS_A, created.requestId());
    RequestView inReview = fixture.requests.get(WS_A, BRUNO, created.requestId());

    RequestView approved =
        fixture.requests.review(
            WS_A,
            BRUNO,
            created.requestId(),
            inReview.version(),
            ReviewDecision.APPROVE_EDITED,
            Category.ACCESS,
            Urgency.CRITICAL);

    assertThat(approved.status()).isEqualTo(RequestStatus.OPEN);
    assertThat(approved.urgency()).isEqualTo(Urgency.CRITICAL);
    assertThat(approved.suggestion().urgency()).isNotEqualTo(Urgency.CRITICAL);
    assertThat(approved.history())
        .anyMatch(entry -> entry.action().equals("SUGGESTION_APPROVED"));
  }

  /** Cenario 14: rejeitar sugestao volta a RECEIVED e nao agenda nova triagem. */
  @Test
  void rejeicaoVoltaAReceived() {
    DomainFixture fixture = new DomainFixture();
    RequestView created = fixture.requests.create(WS_A, ANA, "Acesso negado no ambiente", "k1");
    fixture.triage.runTriage(WS_A, created.requestId());
    RequestView inReview = fixture.requests.get(WS_A, BRUNO, created.requestId());

    RequestView rejected =
        fixture.requests.review(
            WS_A, BRUNO, created.requestId(), inReview.version(), ReviewDecision.REJECT, null, null);

    assertThat(rejected.status()).isEqualTo(RequestStatus.RECEIVED);
    assertThat(rejected.suggestion()).isNotNull();
  }

  /** FR-009: bypass fora da fila humana exige motivo. */
  @Test
  void bypassExigeMotivo() {
    DomainFixture fixture = new DomainFixture();
    RequestView created = fixture.requests.create(WS_A, ANA, "Acesso negado no ambiente", "k1");

    assertThatThrownBy(
            () ->
                fixture.requests.classifyManually(
                    WS_A,
                    BRUNO,
                    created.requestId(),
                    created.version(),
                    Category.ACCESS,
                    Urgency.HIGH,
                    " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** FR-008: transicao fora da tabela nao altera dados. */
  @Test
  void transicaoInvalida() {
    DomainFixture fixture = new DomainFixture();
    RequestView created = fixture.requests.create(WS_A, ANA, "Acesso negado no ambiente", "k1");

    assertThatThrownBy(
            () ->
                fixture.requests.transition(
                    WS_A, BRUNO, created.requestId(), created.version(), RequestStatus.RESOLVED, null))
        .isInstanceOf(InvalidTransitionException.class);
    assertThat(fixture.requests.get(WS_A, BRUNO, created.requestId()).status())
        .isEqualTo(RequestStatus.RECEIVED);
  }

  /** FR-004: workspace precisa manter pelo menos um admin ativo. */
  @Test
  void ultimoAdminNaoPodeSerRemovido() {
    DomainFixture fixture = new DomainFixture();

    assertThatThrownBy(
            () -> fixture.memberships.upsert(new Membership(WS_A, CARLA, Role.ADMIN, false)))
        .isInstanceOf(LastAdminException.class);
  }

  /** FR-006: o texto original permanece intacto depois de um complemento. */
  @Test
  void textoOriginalEhImutavel() {
    DomainFixture fixture = new DomainFixture();
    RequestView created = fixture.requests.create(WS_A, ANA, "nao funciona", "k1");
    fixture.triage.runTriage(WS_A, created.requestId());
    RequestView pending = fixture.requests.get(WS_A, ANA, created.requestId());

    fixture.requests.addClarification(
        WS_A, ANA, created.requestId(), pending.version(), "acesso negado na conta de teste");

    RequestView current = fixture.requests.get(WS_A, ANA, created.requestId());
    assertThat(current.originalText()).isEqualTo("nao funciona");
    assertThat(current.clarifications()).containsExactly("acesso negado na conta de teste");
  }
}
