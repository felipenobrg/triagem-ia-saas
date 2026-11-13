# Contrato HTTP proposto — Spec 003

> **Para estudar:** antes de implementar qualquer rota, responda: quem pode chamar, qual estado e versão ela exige, o que acontece se o provedor de IA não responder e o que o solicitante de outra conversa precisa receber. Nenhum servidor atende a este contrato hoje.

Complementa o [contrato da Spec 001](../001-ai-assisted-request-triage/api-contract.md). Prefixo, autenticação, envelope de erro, paginação por cursor e concorrência por `expectedVersion` são os mesmos.

## Convenções adicionais

- Texto de turno: 1–2.000 caracteres Unicode após trim. Resposta do assistente: até 2.000 caracteres validados no servidor.
- Toda escrita na conversa recebe `expectedVersion` da conversa; perda de corrida responde `409 VERSION_CONFLICT`.
- `POST /messages`, `POST /human-handoffs` e `POST /resolution-confirmations` exigem `Idempotency-Key`, com o mesmo escopo (subject + workspace + operação) e retenção mínima de 24 horas da Spec 001.
- Códigos próprios: `CONVERSATION_NOT_ACTIVE`, `TURN_LIMIT_REACHED`, `ACTIVE_CONVERSATION_LIMIT`, `NOT_CONVERSATION_AUTHOR`, `ALREADY_ESCALATED`, `CONFIRMATION_NOT_PENDING`, `REOPEN_WINDOW_EXPIRED`, `AI_UNAVAILABLE`, `AI_INVALID_OUTPUT`, `AI_OUTCOME_UNKNOWN`, `AI_BUDGET_BLOCKED`, `JUDGE_INVALID_OUTPUT`.
- Nenhuma resposta expõe prompt, raciocínio do modelo, nome de provedor para o requester ou texto de conversa alheia.

## Abrir conversa

`POST /api/v1/workspaces/{workspaceId}/conversations`

Headers: `Idempotency-Key` e bearer OIDC.

```json
{"text":"Não consigo acessar o ambiente de desenvolvimento."}
```

`201 Created` com `{ "conversationId", "status": "ACTIVE", "version": 1, "turns": [...], "turnsRemaining": 10, "createdAt" }`. A resposta inclui o primeiro turno do assistente quando a geração é bem-sucedida; se o provedor falhar, o turno do assistente vem com `state: "FAILED"` e a conversa indica que o escalonamento está disponível.

Somente `REQUESTER`, `AGENT` ou `ADMIN` com membership ativo, sempre como autor. Exceder conversas ativas: `409 ACTIVE_CONVERSATION_LIMIT`. Repetição idêntica da chave devolve a mesma conversa; corpo divergente, `409 IDEMPOTENCY_KEY_REUSED`.

## Listar e ler

`GET /api/v1/workspaces/{workspaceId}/conversations?status=&cursor=&limit=`

Devolve apenas as conversas do subject autenticado, com `conversationId`, estado, instante do último turno, `turnsRemaining` e `escalatedRequestId` quando existir. Filtro de autor enviado pelo cliente é ignorado. Agent e admin **não** listam conversas por esta rota.

`GET /api/v1/workspaces/{workspaceId}/conversations/{conversationId}`

Somente o autor. Devolve turnos ordenados (autor, sequência, instante, texto, estado do turno), estado da conversa, versão, `turnsRemaining`, pendência de confirmação e `escalatedRequestId`. Vereditos do JEV não são expostos ao requester além do efeito observável (pergunta de confirmação, escalonamento e motivo seguro).

## Enviar mensagem

`POST /api/v1/workspaces/{workspaceId}/conversations/{conversationId}/messages`

```json
{"expectedVersion":3,"text":"Aparece acesso negado ao entrar na conta de teste."}
```

Somente o autor, em `ACTIVE` ou `AWAITING_CONFIRMATION` (nesse estado, enviar mensagem equivale a negar a resolução e continuar). `200 OK` devolve o turno gravado do autor, o turno do assistente quando houver, a nova versão, `turnsRemaining` e o estado resultante.

- Conversa terminal: `409 CONVERSATION_NOT_ACTIVE`.
- Limite de turnos atingido: a conversa escala e a resposta traz `status: "ESCALATED"`, `escalationReason: "TURN_LIMIT_REACHED"` e `escalatedRequestId`.
- Provedor indisponível: `200 OK` com turno do assistente em `FAILED` e indicação de que o autor pode reenviar ou pedir humano; nenhum texto inventado é gravado como resposta do assistente.
- Timeout após envio ao provedor: turno do assistente em `OUTCOME_UNKNOWN`, sem retry automático.
- Orçamento esgotado: a conversa escala com `AI_BUDGET_BLOCKED`, sem chamada real.

O turno do assistente gravado agenda a avaliação do JEV na mesma transação (evento `ConversationEvaluationRequested.v1`).

## Pedir pessoa humana ou escalar

`POST /api/v1/workspaces/{workspaceId}/conversations/{conversationId}/human-handoffs`

```json
{"expectedVersion":5,"reason":"Continuo sem acesso."}
```

- **Autor:** permitido em `ACTIVE`, `AWAITING_CONFIRMATION` e em `CLOSED_INACTIVE` dentro de 7 dias corridos do encerramento (`409 REOPEN_WINDOW_EXPIRED` fora da janela). Escala imediatamente, sem consultar o JEV. `reason` é opcional para o autor.
- **Agent/admin do workspace:** permitido em conversa não terminal, com `reason` não vazio. Não dá acesso de escrita à conversa.

`201 Created` com `{ "conversationId", "status": "ESCALATED", "version", "escalatedRequestId", "requestStatus": "NEEDS_REVIEW", "problemContextIncluded": true }`. Conversa já escalada: `409 ALREADY_ESCALATED`, ou o mesmo resultado quando a chave idempotente é repetida. Nenhum chamado nasce em `OPEN`.

## Confirmar ou negar resolução

`POST /api/v1/workspaces/{workspaceId}/conversations/{conversationId}/resolution-confirmations`

```json
{"expectedVersion":6,"resolved":true}
```

Somente o autor, somente em `AWAITING_CONFIRMATION` (`409 CONFIRMATION_NOT_PENDING` nos outros estados). `resolved: true` leva a `RESOLVED_CONFIRMED` e **não** cria chamado. `resolved: false` volta a `ACTIVE` quando ainda há turnos disponíveis, ou escala com motivo `USER_REPORTED_STILL_BROKEN`. Outro usuário recebe `404`.

## Leitura da conversa pelo técnico

`GET /api/v1/workspaces/{workspaceId}/requests/{requestId}/conversation`

Agent/admin com membership ativo, somente quando o chamado tem `source = CONVERSATION` e a conversa está `ESCALATED`. Devolve turnos ordenados e o contexto do problema. Cada chamada registra ator, instante e correlation ID na auditoria do chamado. Conversa não escalada ou chamado de outro workspace: `404`. Requester que não é o autor: `404`.

O detalhe do chamado (`GET .../requests/{requestId}`, Spec 001) passa a trazer, quando a origem é conversa: `conversationId`, `problemContext` (`summary`, `symptoms`, `attemptedSteps`, `suggestedCategory`, `suggestedUrgency`, `escalationReason`) e `contextGenerated: true|false`. Quando o contexto não pôde ser gerado, o chamado vem sem sugestão, com motivo seguro, e o técnico usa a conversa.

## Eventos internos

`ConversationEvaluationRequested.v1`: `{ eventId, eventType, schemaVersion, occurredAt, workspaceId, conversationId, turnSequence, correlationId, causationId }`.

`ConversationEscalated.v1`: `{ eventId, eventType, schemaVersion, occurredAt, workspaceId, conversationId, requestId, escalationReason, correlationId, causationId }`.

Nenhum evento carrega texto de turno, contexto do problema, prompt, e-mail ou token. O consumidor reabre os dados autorizados pelos IDs, confere o `workspaceId` e aplica o veredito somente se `turnSequence` ainda for o último turno de assistente; caso contrário, marca o veredito `OBSOLETE`. Versão de schema desconhecida vai à DLQ.

## Saída validada dos provedores

**Agente de conversa (`ChatProvider`):** `{ "reply": "<1–2000 chars>" }`. Resposta vazia, acima do limite, com pedido de credencial ou com schema inválido é descartada: o turno fica `FAILED` com `AI_INVALID_OUTPUT` e nenhum texto do modelo é gravado como resposta.

**JEV (`ConversationJudge`):** `{ "verdict": "CONTINUE" | "LIKELY_RESOLVED" | "ESCALATE", "reasonCode": "<lista da spec>", "confidence": 0.0–1.0, "suggestedCategory": "...", "suggestedUrgency": "...", "summary": "<até 500 chars>" }`. `suggestedCategory`, `suggestedUrgency` e `summary` são obrigatórios apenas em `ESCALATE` e validados contra os enums da Spec 001. Qualquer violação é `JUDGE_INVALID_OUTPUT` e trata-se como `ESCALATE`. O veredito nunca é aplicado como classificação final: a revisão humana da Spec 001 continua obrigatória.
