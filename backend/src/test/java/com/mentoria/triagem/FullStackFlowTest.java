package com.mentoria.triagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de ponta a ponta com PostgreSQL e RabbitMQ reais (Testcontainers).
 *
 * <p>E aqui que o desenho assincrono fica demonstravel: a criacao responde sem esperar a IA, o
 * evento sai pela outbox, o consumidor aplica o resultado e o estado muda depois. Os testes de
 * dominio nao provam isso, porque nao tem broker.
 *
 * <p>Identidade: perfil {@code devstub} (header X-Dev-Subject). Subir o Keycloak dentro deste
 * teste nao foi feito; a validacao do token foi exercitada manualmente contra o Compose e esta
 * registrada em docs/implementation-status.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("devstub")
@Testcontainers
class FullStackFlowTest {

  private static final String WS = "ws-demo";
  private static final String ANA = "requester-ana";
  private static final String BRUNO = "agent-bruno";
  private static final String SUBJECT_HEADER = "X-Dev-Subject";

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("triagem")
          .withUsername("triagem")
          .withPassword("triagem-sintetico");

  @Container
  static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
    registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
    registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    registry.add("app.auth.dev-stub-enabled", () -> "true");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbc;

  /** Migracoes aplicadas e membros sinteticos no lugar (NFR-001). */
  @Test
  void migracoesCriamSchemaESeedSintetico() {
    Integer memberships =
        jdbc.queryForObject(
            "SELECT count(*) FROM membership WHERE workspace_id = 'ws-demo'", Integer.class);
    assertThat(memberships).isGreaterThanOrEqualTo(3);

    Integer uniqueIndex =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_indexes WHERE indexname = 'ux_request_open_per_conversation'",
            Integer.class);
    assertThat(uniqueIndex).isEqualTo(1);
  }

  /**
   * FR-018 a FR-021: a criacao responde em RECEIVED e a analise inicial chega por evento.
   * Quem cria nao espera a IA.
   */
  @Test
  void analiseInicialChegaPorEvento() throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/v1/workspaces/{ws}/requests", WS)
                    .header(SUBJECT_HEADER, ANA)
                    .header("Idempotency-Key", "it-" + Instant.now().toEpochMilli())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"text\":\"Recebo acesso negado ao entrar na conta de teste\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("RECEIVED"))
            .andReturn();

    String requestId = json(created).get("requestId").asText();

    String status = await(() -> statusOfRequest(requestId), value -> !value.equals("RECEIVED"));
    assertThat(status).isEqualTo("NEEDS_REVIEW");

    // A outbox nao fica com pendencia depois da publicacao confirmada (FR-019).
    Integer pending =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_message WHERE published_at IS NULL", Integer.class);
    assertThat(pending).isZero();

    // A inbox registrou o consumo exatamente uma vez (FR-020).
    Integer inbox =
        jdbc.queryForObject(
            "SELECT count(*) FROM inbox_message WHERE consumer = 'triage'", Integer.class);
    assertThat(inbox).isGreaterThanOrEqualTo(1);
  }

  /** FR3-008: o veredito do JEV chega por evento e escala a conversa com contexto. */
  @Test
  void conversaEscalaPorVereditoAssincrono() throws Exception {
    MvcResult opened =
        mockMvc
            .perform(
                post("/api/v1/workspaces/{ws}/conversations", WS)
                    .header(SUBJECT_HEADER, ANA)
                    .header("Idempotency-Key", "conv-" + Instant.now().toEpochMilli())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Recebo acesso negado ao entrar na conta de teste\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode conversation = json(opened);
    String conversationId = conversation.get("conversationId").asText();

    // O primeiro veredito e CONTINUE: a conversa segue ativa.
    await(() -> verdictCount(conversationId), count -> count >= 1);

    long version = currentVersion(conversationId);
    mockMvc
        .perform(
            post("/api/v1/workspaces/{ws}/conversations/{id}/messages", WS, conversationId)
                .header(SUBJECT_HEADER, ANA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"expectedVersion\":"
                        + version
                        + ",\"text\":\"Ja estou no grupo e continua o mesmo erro\"}"))
        .andExpect(status().isOk());

    String conversationStatus =
        await(() -> statusOfConversation(conversationId), value -> value.equals("ESCALATED"));
    assertThat(conversationStatus).isEqualTo("ESCALATED");

    String requestId =
        jdbc.queryForObject(
            "SELECT escalated_request_id::text FROM conversation WHERE id = ?::uuid",
            String.class,
            conversationId);
    assertThat(requestId).isNotNull();

    // O chamado existe em NEEDS_REVIEW, com contexto gravado em tabela propria.
    mockMvc
        .perform(
            get("/api/v1/workspaces/{ws}/requests/{id}", WS, requestId)
                .header(SUBJECT_HEADER, BRUNO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NEEDS_REVIEW"))
        .andExpect(jsonPath("$.source").value("CONVERSATION"))
        .andExpect(jsonPath("$.problemContext.attemptedSteps").isNotEmpty());

    Integer contextItems =
        jdbc.queryForObject(
            "SELECT count(*) FROM problem_context_item WHERE request_id = ?::uuid",
            Integer.class,
            requestId);
    assertThat(contextItems).isGreaterThan(0);

    // Indice parcial unico: nao existe segundo chamado nao terminal para a mesma conversa.
    Integer requestsForConversation =
        jdbc.queryForObject(
            "SELECT count(*) FROM service_request WHERE conversation_id = ?::uuid",
            Integer.class,
            conversationId);
    assertThat(requestsForConversation).isEqualTo(1);

    // Leitura da transcricao pelo tecnico fica auditada no banco (FR3-018).
    mockMvc
        .perform(
            get("/api/v1/workspaces/{ws}/requests/{id}/conversation", WS, requestId)
                .header(SUBJECT_HEADER, BRUNO))
        .andExpect(status().isOk());

    Integer accesses =
        jdbc.queryForObject(
            "SELECT count(*) FROM transcript_access WHERE conversation_id = ?::uuid",
            Integer.class,
            conversationId);
    assertThat(accesses).isEqualTo(1);
  }

  /** Turnos sao somente inseridos: o historico da conversa fica no banco (BR3-003). */
  @Test
  void turnosSaoPersistidos() throws Exception {
    MvcResult opened =
        mockMvc
            .perform(
                post("/api/v1/workspaces/{ws}/conversations", WS)
                    .header(SUBJECT_HEADER, ANA)
                    .header("Idempotency-Key", "turns-" + Instant.now().toEpochMilli())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"O ambiente esta lento desde ontem\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    String conversationId = json(opened).get("conversationId").asText();

    Integer turns =
        jdbc.queryForObject(
            "SELECT count(*) FROM conversation_turn WHERE conversation_id = ?::uuid",
            Integer.class,
            conversationId);
    assertThat(turns).isEqualTo(2);

    mockMvc
        .perform(
            get("/api/v1/workspaces/{ws}/conversations/{id}", WS, conversationId)
                .header(SUBJECT_HEADER, ANA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.turns.length()").value(2));
  }

  /** NFR-004 no banco: UPDATE condicional por versao faz a segunda escrita perder. */
  @Test
  void conflitoDeVersaoNoBanco() throws Exception {
    MvcResult opened =
        mockMvc
            .perform(
                post("/api/v1/workspaces/{ws}/conversations", WS)
                    .header(SUBJECT_HEADER, ANA)
                    .header("Idempotency-Key", "conflict-" + Instant.now().toEpochMilli())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Nao consigo acessar o ambiente de desenvolvimento\"}"))
            .andExpect(status().isCreated())
            .andReturn();

    String conversationId = json(opened).get("conversationId").asText();
    long staleVersion = 1;

    mockMvc
        .perform(
            post("/api/v1/workspaces/{ws}/conversations/{id}/messages", WS, conversationId)
                .header(SUBJECT_HEADER, ANA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"expectedVersion\":" + staleVersion + ",\"text\":\"mensagem atrasada\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
  }

  /**
   * FR-019 e cenario 6 da Spec 001: publish sem binding recebe {@code basic.return}. Mesmo com
   * publisher confirm, a mensagem continua pendente e visivel para operacao, em vez de ser
   * marcada como publicada e desaparecer.
   */
  @Test
  void eventoSemBindingFicaPendenteEVisivel() {
    String eventId = java.util.UUID.randomUUID().toString();
    jdbc.update(
        """
        INSERT INTO outbox_message (
          event_id, event_type, schema_version, workspace_id, aggregate_id, payload,
          correlation_id, occurred_at, next_attempt_at)
        VALUES (?::uuid, 'EventoSemConsumidor', 1, 'ws-demo', 'x', '{}', 'c', now(), now())
        """,
        eventId);

    // Da tempo ao dispatcher de tentar publicar algumas vezes.
    Integer attempts =
        await(() -> attemptsOf(eventId), value -> value != null && value >= 1);

    assertThat(attempts).isGreaterThanOrEqualTo(1);
    String publishedAt =
        jdbc.queryForObject(
            "SELECT coalesce(published_at::text, 'PENDENTE') FROM outbox_message"
                + " WHERE event_id = ?::uuid",
            String.class,
            eventId);
    assertThat(publishedAt).isEqualTo("PENDENTE");

    String lastError =
        jdbc.queryForObject(
            "SELECT last_error FROM outbox_message WHERE event_id = ?::uuid", String.class, eventId);
    assertThat(lastError).contains("retornada pelo broker");
  }

  /** Isolamento: sem membership no workspace, nada e criado (FR-002). */
  @Test
  void semMembershipResponde403() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workspaces/{ws}/conversations", "ws-outro")
                .header(SUBJECT_HEADER, ANA)
                .header("Idempotency-Key", "forbidden-" + Instant.now().toEpochMilli())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Nao consigo acessar o ambiente\"}"))
        .andExpect(status().isForbidden());
  }

  // --- apoio ------------------------------------------------------------------------------

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private String statusOfRequest(String requestId) {
    return jdbc.queryForObject(
        "SELECT status FROM service_request WHERE id = ?::uuid", String.class, requestId);
  }

  private String statusOfConversation(String conversationId) {
    return jdbc.queryForObject(
        "SELECT status FROM conversation WHERE id = ?::uuid", String.class, conversationId);
  }

  private long currentVersion(String conversationId) {
    Long version =
        jdbc.queryForObject(
            "SELECT version FROM conversation WHERE id = ?::uuid", Long.class, conversationId);
    return version == null ? 0 : version;
  }

  private Integer attemptsOf(String eventId) {
    return jdbc.queryForObject(
        "SELECT attempts FROM outbox_message WHERE event_id = ?::uuid", Integer.class, eventId);
  }

  private int verdictCount(String conversationId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM judge_verdict WHERE conversation_id = ?::uuid",
            Integer.class,
            conversationId);
    return count == null ? 0 : count;
  }

  /** Espera ativa curta: o efeito chega por broker, nao no mesmo request. */
  private <T> T await(Supplier<T> probe, java.util.function.Predicate<T> done) {
    Duration timeout = Duration.ofSeconds(20);
    Instant deadline = Instant.now().plus(timeout);
    T last = probe.get();
    while (Instant.now().isBefore(deadline)) {
      if (done.test(last)) {
        return last;
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        break;
      }
      last = probe.get();
    }
    return last;
  }
}
