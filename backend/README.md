# Backend — monólito modular (material de estudo)

> **Estado:** fatia executável do exercício, com PostgreSQL, RabbitMQ e Keycloak reais. Os provedores de IA continuam **falsos e determinísticos**: nada aqui demonstra qualidade de IA. A Spec 002 e o frontend não existem. O mapa requisito → teste está em [estado da implementação](../docs/implementation-status.md).

## Subindo tudo com Docker (caminho recomendado)

A partir da raiz do repositório:

```bash
docker compose up -d --build
curl http://localhost:8080/api/v1/health
```

Sobem quatro serviços: `postgres` (5432), `rabbitmq` (5672, painel em 15672), `keycloak` (8081) e `backend` (8080). O backend espera os health checks dos outros três antes de iniciar, aplica as migrações Flyway e declara a topologia do broker na subida.

| Serviço | Acesso sintético |
| --- | --- |
| Keycloak admin | http://localhost:8081 — `admin` / `admin-sintetico` |
| RabbitMQ painel | http://localhost:15672 — `triagem` / `triagem-sintetico` |
| PostgreSQL | `triagem` / `triagem-sintetico`, banco `triagem` |
| Usuários do realm | `ana` (requester), `bruno` (agent), `carla` (admin), `dani` (outro workspace) — senha `senha-sintetica` |

Nada disso é credencial real. O Keycloak roda em `start-dev` e o realm é importado de [`infra/keycloak/realm-triagem.json`](../infra/keycloak/realm-triagem.json).

## Demonstração do chatbot (autenticada)

```bash
# 1. Token da Ana no Keycloak
ANA=$(curl -s -X POST "http://localhost:8081/realms/triagem/protocol/openid-connect/token" \
  -d client_id=triagem-ui -d username=ana -d password=senha-sintetica -d grant_type=password \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

# Sem token, rota de negócio responde 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/workspaces/ws-demo/conversations

# 2. Ana abre a conversa
curl -X POST http://localhost:8080/api/v1/workspaces/ws-demo/conversations \
  -H "Authorization: Bearer $ANA" -H "Idempotency-Key: demo-0001" \
  -H "Content-Type: application/json" \
  -d '{"text":"Recebo acesso negado ao entrar na conta de teste"}'
# -> 201 com status ACTIVE. Repare: a resposta NÃO espera o JEV.

# 3. Ana responde que continua quebrado (use conversationId e version da resposta)
curl -X POST http://localhost:8080/api/v1/workspaces/ws-demo/conversations/<conversationId>/messages \
  -H "Authorization: Bearer $ANA" -H "Content-Type: application/json" \
  -d '{"expectedVersion":3,"text":"Ja estou no grupo dev-sandbox e continua o mesmo erro"}'
# -> 200 ainda ACTIVE; segundos depois a consulta mostra ESCALATED

# 4. Consultar de novo até ver o efeito do veredito assíncrono
curl http://localhost:8080/api/v1/workspaces/ws-demo/conversations/<conversationId> \
  -H "Authorization: Bearer $ANA"
# -> status ESCALATED, escalationReason JUDGE_ESCALATED, escalatedRequestId preenchido

# 5. Bruno (agente) vê o chamado com o contexto e lê a transcrição
BRUNO=$(curl -s -X POST "http://localhost:8081/realms/triagem/protocol/openid-connect/token" \
  -d client_id=triagem-ui -d username=bruno -d password=senha-sintetica -d grant_type=password \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

curl http://localhost:8080/api/v1/workspaces/ws-demo/requests/<requestId> -H "Authorization: Bearer $BRUNO"
curl http://localhost:8080/api/v1/workspaces/ws-demo/requests/<requestId>/conversation -H "Authorization: Bearer $BRUNO"
```

O outro desfecho — resolvido **sem** abrir chamado — é: responder "era isso, consegui entrar agora", esperar o estado virar `AWAITING_CONFIRMATION` e confirmar:

```bash
curl -X POST http://localhost:8080/api/v1/workspaces/ws-demo/conversations/<conversationId>/resolution-confirmations \
  -H "Authorization: Bearer $ANA" -H "Content-Type: application/json" \
  -d '{"expectedVersion":<version>,"resolved":true}'
# -> RESOLVED_CONFIRMED e escalatedRequestId null
```

Para olhar os bastidores:

```bash
docker exec triagem-postgres psql -U triagem -d triagem -c \
  "SELECT event_type, published_at IS NOT NULL AS publicado, attempts FROM outbox_message ORDER BY occurred_at;"
docker exec triagem-postgres psql -U triagem -d triagem -c \
  "SELECT turn_sequence, verdict, state, confidence FROM judge_verdict ORDER BY id;"
docker exec triagem-rabbitmq rabbitmqctl list_queues name messages
```

## Rodando sem Docker

Precisa de **JDK 21** (o wrapper usa o `JAVA_HOME` do ambiente; nesta máquina o `java` do PATH é um JRE 8) e de rede na primeira execução.

```bash
cd backend
export JAVA_HOME="/c/Users/<voce>/.jdks/ms-21.0.12.1"
./mvnw test   # 47 testes; os de integração sobem Postgres e RabbitMQ via Testcontainers (precisa de Docker)
```

Para subir a aplicação sem Keycloak, use o perfil `devstub`, em que o subject vem do header `X-Dev-Subject`. Ele **não** satisfaz o FR-001 e só serve para estudo local — com ele, qualquer um se declara quem quiser:

```bash
SPRING_PROFILES_ACTIVE=devstub APP_AUTH_DEV_STUB_ENABLED=true ./mvnw spring-boot:run
curl -X POST http://localhost:8080/api/v1/workspaces/ws-demo/conversations \
  -H "X-Dev-Subject: requester-ana" -H "Idempotency-Key: k1" \
  -H "Content-Type: application/json" -d '{"text":"Recebo acesso negado na conta de teste"}'
```

Ainda assim é preciso um PostgreSQL e um RabbitMQ acessíveis (`docker compose up -d postgres rabbitmq`).

## Como as peças se encaixam

| Módulo | Responsabilidade |
| --- | --- |
| `identity` | Membership e papel por workspace, em PostgreSQL. Toda autorização passa por aqui; papel **nunca** vem do token |
| `requests` | Solicitação: texto original imutável, estados, versão otimista no banco, histórico, contexto do problema |
| `triage` | Porta `TriageProvider`, validação da saída e aplicação da rota; consumidor de `TriageRequested.v1` |
| `conversations` | Conversa, turnos imutáveis, portas `ChatProvider` e `ConversationJudge` (JEV), escalonamento transacional; consumidor de `ConversationEvaluationRequested.v1` |
| `messaging` | Outbox, inbox, dispatcher com publisher confirms e `mandatory=true`, topologia e DLQ |
| `api` | Controllers, envelope de erro, resource server OIDC |

Fluxo de uma mensagem do chat:

1. `POST /messages` grava o turno do autor (transação).
2. O `ChatProvider` é chamado **fora** de transação (é rede).
3. O turno do assistente e o evento `ConversationEvaluationRequested.v1` são gravados na mesma transação (FR-018). A resposta HTTP volta aqui.
4. O `OutboxDispatcher` publica, confirma e só então marca como publicada; mensagem sem binding volta pelo `basic.return` e **continua pendente**.
5. O consumidor registra na inbox, chama o JEV, valida a saída e aplica o veredito — ou marca `OBSOLETE` se o turno já foi superado.

`ModuleBoundariesTest` roda `ApplicationModules.verify()`: tipos em subpacotes `internal` não vazam entre módulos.

## Limitações desta fatia

Requisitos que **não** estão implementados e não devem ser apresentados como prontos:

- **IA real:** só os fakes determinísticos. Não há adapter de provedor, reserva de orçamento, telemetria de custo nem avaliação. O que falta decidir e configurar está em [estado da implementação](../docs/implementation-status.md).
- **Spec 002 inteira:** prioridade, prazo, responsável, espera, reabertura e duplicados.
- **Frontend:** não existe.
- **Paginação por cursor (`FR-007`):** a lista filtra só por estado.
- **Agendador de inatividade:** `closeInactiveConversations()` existe e é testado, mas nada o chama periodicamente.
- **CI:** não configurado.
- **Backup/restore (`NFR-005`):** não documentado nem exercitado.

Os comandos acima foram executados nesta máquina. Conforme o `NFR-001`, só contam como verificados depois que **outra pessoa** os rodar em clone limpo (T504).
