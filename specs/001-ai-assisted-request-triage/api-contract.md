# Contrato HTTP — Spec 001

> **Para estudar:** cada rota descreve uma promessa observável que a dupla vai implementar e testar. Antes de escrever controller, identifique quem pode chamar a rota, qual versão e estado ela exige e qual erro não deve revelar dados de outro workspace. Não existe servidor atendendo a este contrato hoje.

Contrato funcional da [Spec 001](spec.md). Antes da primeira implementação, publicar OpenAPI que reflita estes recursos e manter ambos alinhados. Todos os exemplos usam valores sintéticos.

## Convenções

- Prefixo `/api/v1`; JSON UTF-8; `Authorization: Bearer <OIDC access token>`.
- `workspaceId` seleciona o contexto; o servidor valida membership e papel em cada chamada.
- IDs opacos; datas RFC 3339 em UTC. Listas usam cursor opaco, `limit` limitado no servidor e ordenação estável por `(createdAt, id)`.
- Escritas de estado recebem `expectedVersion` inteiro positivo no JSON. Concorrência perdida responde `409 VERSION_CONFLICT`. O contrato usa um único mecanismo de concorrência; o OpenAPI deve refletir isso.
- Erros: `{ "error": { "code": "...", "message": "...", "correlationId": "...", "details": {} } }`. `details` nunca inclui conteúdo de outro tenant nem segredo.
- `401` token ausente ou inválido; `403` papel insuficiente; `404` recurso ausente ou fora do workspace; `409` conflito de chave, versão ou estado; `422` validação; `429` limite de uso; `503` dependência necessária indisponível.

## Recursos

### Criar solicitação

`POST /api/v1/workspaces/{workspaceId}/requests`

Headers obrigatórios: `Idempotency-Key` (valor aleatório, 16–128 caracteres) e bearer OIDC.

```json
{"text":"Não consigo acessar o ambiente de desenvolvimento.","source":"WEB"}
```

`201 Created` na primeira execução e na repetição idêntica dentro da retenção da chave; responde `{ "requestId", "status": "RECEIVED", "version": 1, "createdAt" }`. A chave é armazenada como hash, com escopo de subject, workspace e operação. Reuso com corpo diferente: `409 IDEMPOTENCY_KEY_REUSED`. Permitido a `REQUESTER`, `AGENT` e `ADMIN` associados ao workspace. `source` aceita `WEB` e `CONVERSATION`; o segundo valor é gravado apenas pelo fluxo interno da [Spec 003](../003-ai-chat-resolution/spec.md) e rejeitado quando enviado pelo cliente.

### Listar

`GET /api/v1/workspaces/{workspaceId}/requests?status=&category=&urgency=&from=&to=&cursor=&limit=`

Agent/admin recebem por padrão a fila de revisão humana (`NEEDS_REVIEW`), com filtro de estado para exceções e acompanhamento. `RECEIVED` e `TRIAGE_PENDING` aparecem em visão operacional; `NEEDS_CLARIFICATION` pode ser acompanhado, mas não entra na fila humana comum até haver complemento ou pedido explícito. Requester recebe somente as próprias, ignorando filtro de `requesterId` enviado pelo cliente. A resposta inclui `items`, `nextCursor` e `hasMore`; não aceita query arbitrária.

### Detalhe e histórico

`GET /api/v1/workspaces/{workspaceId}/requests/{requestId}`

Retorna, apenas a membros autorizados do workspace: texto original, estado, versão, sugestão atual opcional, pergunta de clarificação opcional, motivo seguro de encaminhamento humano, origem (`WEB` ou `CONVERSATION`, com `conversationId` e contexto quando houver) e histórico paginado e autorizado. Requester acessa somente recurso de sua autoria.

### Revisar sugestão

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/reviews`

```json
{"decision":"APPROVE_EDITED","expectedVersion":4,"category":"ACCESS","urgency":"HIGH","summary":"Acesso ao ambiente bloqueado."}
```

Decisões `APPROVE`, `APPROVE_EDITED` e `REJECT`; agent/admin. Um review é imutável. A aprovação exige sugestão validada e versão atual. Quando `NEEDS_REVIEW` não tem sugestão, usar classificação manual. A rejeição conserva a sugestão e mantém o item disponível para classificação manual.

### Classificação manual

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/manual-classification`

```json
{"expectedVersion":4,"category":"ACCESS","urgency":"HIGH","summary":"Acesso ao ambiente bloqueado.","reason":"Broker indisponível; triagem automática suspensa."}
```

Agent/admin; registra autor e origem manual e transiciona para `OPEN` atomicamente, com auditoria. Fora de `NEEDS_REVIEW`, `reason` não pode ser vazio: é bypass explícito da análise da IA.

Se o worker estiver processando o mesmo pedido, a decisão manual vence pela versão do pedido. O resultado tardio marca a tentativa como `OBSOLETE`, sem sugestão revisável nem mudança de estado.

### Complementar informação ou pedir revisão humana

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/clarifications`

Headers: `Idempotency-Key`, com o mesmo escopo e retenção da criação. Body: `{ "expectedVersion": 3, "text": "O erro é acesso negado ao entrar na conta de teste." }`. Somente o requester autor, em `NEEDS_CLARIFICATION`; o texto é complemento imutável, separado da origem, com 1–2.000 caracteres Unicode após trim. A operação grava texto, auditoria e novo `TriageRequested.v1` na mesma transação e leva a `TRIAGE_PENDING`. É permitida uma única rodada automática; repetição idêntica da chave devolve o mesmo resultado e versão desatualizada retorna conflito.

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/human-review-requests`

Body: `{ "expectedVersion": 3 }`. Somente o requester autor, em `NEEDS_CLARIFICATION`; leva a `NEEDS_REVIEW` sem sugestão, com motivo seguro `REQUESTER_REQUESTED_HUMAN`. Agent/admin continua autorizado a classificar manualmente mediante motivo explícito de bypass.

### Alterar estado operacional

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/status-transitions`

```json
{"expectedVersion":5,"toStatus":"IN_PROGRESS","reason":"Atendimento iniciado."}
```

Agent/admin; somente as transições da tabela da Spec 001. `reason` é obrigatório em `IN_PROGRESS → OPEN`; estado inválido retorna `409 INVALID_TRANSITION`. `RESOLVED` é terminal nesta spec e a reabertura é definida na Spec 002.

### Administrar membros

`POST /api/v1/workspaces/{workspaceId}/memberships` associa um subject OIDC existente ao papel `REQUESTER`, `AGENT` ou `ADMIN`; somente admin. `PATCH /api/v1/workspaces/{workspaceId}/memberships/{subject}` altera papel ou status com o `expectedVersion` da membership. Remover ou rebaixar o último admin ativo retorna `409 LAST_ADMIN`. O servidor verifica subject e workspace, registra auditoria e nunca trata papel do token como autorização de produto. A Spec 002 acrescenta desatribuição atômica quando o responsável perde papel.

### Repetir triagem

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/triage-retries`

Agent/admin. Permitido apenas para tentativa `FAILED` ou `RETRY_SCHEDULED`, sem trabalho em curso. `OUTCOME_UNKNOWN` exige confirmação humana explícita no body (`"confirmPossibleDuplicateCharge": true`) e usa nova chave de operação; o sistema mostra aviso de chamada possivelmente já cobrada.

Rejeitar uma sugestão bem-sucedida não agenda outra tentativa nem torna essa rota elegível automaticamente. O pedido segue disponível para classificação manual.

## Contrato do evento interno

`TriageRequested.v1`: `{ eventId, eventType, schemaVersion, occurredAt, workspaceId, requestId, correlationId, causationId }`. Sem texto, e-mail, token ou conteúdo de prompt. O consumidor reabre os dados autorizados pelo `requestId` e confere que o `workspaceId` corresponde antes de executar. Versão desconhecida vai à DLQ, sem tentativa de inferir payload.

Resultado validado do provedor: `HUMAN_REVIEW` exige `category`, `urgency` e `summary`; `REQUEST_CLARIFICATION` exige `question` de 1–300 caracteres Unicode após trim e não cria sugestão. Pergunta que solicite credencial ou segredo é inválida e segue ao humano sem sugestão. A primeira rota leva a `NEEDS_REVIEW` com sugestão; a segunda, a `NEEDS_CLARIFICATION`. Resposta incerta, inválida ou falha terminal leva a `NEEDS_REVIEW` sem sugestão, com código seguro (`AI_UNCERTAIN`, `AI_INVALID_OUTPUT`, `AI_UNAVAILABLE`, `AI_BUDGET_BLOCKED` ou `AI_OUTCOME_UNKNOWN`). Após um complemento, nova tentativa de clarificação também leva ao humano sem sugestão, com `CLARIFICATION_LIMIT`. Nenhuma dessas rotas fecha ou rejeita o pedido.
