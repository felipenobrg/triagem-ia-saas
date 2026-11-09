# Contrato HTTP inicial

> **Para estudar:** cada rota descreve uma promessa observável que os alunos deverão implementar e testar. Antes de escrever controller, identifique quem pode chamar a rota, qual versão/estado ela exige e qual erro não deve revelar dados de outro workspace. Não existe servidor atendendo a este contrato hoje.

Contrato funcional da Spec 001. Antes da primeira implementação, publicar OpenAPI que reflita estes recursos e manter ambos alinhados. Exemplos usam valores sintéticos.

## Convenções

- Prefixo `/api/v1`; JSON UTF-8; token `Authorization: Bearer <OIDC access token>`.
- `workspaceId` seleciona contexto; servidor valida membership e papel em cada chamada.
- IDs opacos; datas RFC 3339 UTC. Listas usam cursor opaco, `limit` limitado no servidor e ordenação estável por `(createdAt,id)`.
- Escritas de estado recebem `expectedVersion` inteiro positivo no JSON. Concorrência perdida responde `409 VERSION_CONFLICT`. Este contrato usa um único mecanismo; OpenAPI deve refletir isso.
- Erros: `{ "error": { "code": "...", "message": "...", "correlationId": "...", "details": {} } }`. `details` nunca inclui conteúdo de outro tenant ou segredo.
- `401` token ausente/inválido; `403` papel insuficiente; `404` recurso ausente ou fora do workspace; `409` conflito de chave/versão/estado; `422` validação; `429` limite de uso; `503` dependência necessária indisponível.

## Recursos

### Criar solicitação

`POST /api/v1/workspaces/{workspaceId}/requests`

Headers obrigatórios: `Idempotency-Key` (valor aleatório, 16–128 caracteres); OIDC bearer.

```json
{"text":"Não consigo acessar o ambiente de desenvolvimento.","source":"WEB"}
```

`201 Created` na primeira execução e na repetição idêntica dentro da retenção da chave; responde `{ "requestId", "status": "RECEIVED", "version": 1, "createdAt" }`. A chave é armazenada como hash com escopo de subject, workspace e operação. Reuso com corpo diferente: `409 IDEMPOTENCY_KEY_REUSED`. Apenas `REQUESTER`, `AGENT`, `ADMIN` associado ao workspace.

### Listar

`GET /api/v1/workspaces/{workspaceId}/requests?status=&category=&urgency=&from=&to=&cursor=&limit=`

Agent/admin recebem fila de revisão humana (`NEEDS_REVIEW`) por padrão, com filtro de estado para exceções e acompanhamento. `RECEIVED`/`TRIAGE_PENDING` aparecem em visão operacional; `NEEDS_CLARIFICATION` pode ser acompanhado, mas não entra na fila humana comum até complemento ou pedido explícito. Requester recebe somente as próprias, ignorando filtro de `requesterId` enviado pelo cliente. Resposta inclui `items`, `nextCursor` e `hasMore`; não aceita query arbitrária.

### Detalhe e histórico

`GET /api/v1/workspaces/{workspaceId}/requests/{requestId}`

Retorna texto original apenas a membros autorizados do workspace, estado, versão, sugestão atual opcional, pergunta de clarificação opcional, motivo seguro de encaminhamento humano e histórico paginado/autorizado. Requester só acessa recurso de sua autoria.

### Revisar sugestão

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/reviews`

```json
{"decision":"APPROVE_EDITED","expectedVersion":4,"category":"ACCESS","urgency":"HIGH","summary":"Acesso ao ambiente bloqueado."}
```

Decisões `APPROVE`, `APPROVE_EDITED`, `REJECT`; agent/admin. Um review é imutável. Aprovação exige sugestão validada e versão atual. Quando `NEEDS_REVIEW` não tem sugestão, usar classificação manual. Rejeição conserva sugestão e mantém item disponível para classificação manual.

### Classificação manual

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/manual-classification`

```json
{"expectedVersion":4,"category":"ACCESS","urgency":"HIGH","summary":"Acesso ao ambiente bloqueado.","reason":"..."}
```

Agent/admin; registra autor e origem manual, transiciona para `OPEN` atomicamente com auditoria. Fora de `NEEDS_REVIEW`, `reason` não pode ser vazio: é bypass explícito da análise da IA, por exemplo durante indisponibilidade do broker.

Se worker estiver processando o mesmo pedido, decisão manual vence pela versão do pedido. Resultado tardio marca tentativa `OBSOLETE`, sem sugestão revisável ou mudança de estado.

### Complementar informação ou pedir revisão humana

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/clarifications`

Headers: `Idempotency-Key` com o mesmo escopo e regras de retenção da criação. Body: `{ "expectedVersion": 3, "text": "O erro é acesso negado ao entrar na conta de teste." }`. Somente requester autor em `NEEDS_CLARIFICATION`; texto é complemento imutável separado da origem, com 1–2.000 caracteres Unicode após trim. A operação grava texto, auditoria e novo `TriageRequested.v1` na mesma transação e leva a `TRIAGE_PENDING`. Uma única rodada automática é permitida; repetição idêntica da chave devolve o mesmo resultado, versão desatualizada retorna conflito.

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/human-review-requests`

Body: `{ "expectedVersion": 3 }`. Somente requester autor em `NEEDS_CLARIFICATION`; leva a `NEEDS_REVIEW` sem sugestão, com motivo seguro `REQUESTER_REQUESTED_HUMAN`. Agent/admin continua autorizado a classificar manualmente mediante motivo explícito de bypass.

### Alterar estado operacional

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/status-transitions`

```json
{"expectedVersion":5,"toStatus":"IN_PROGRESS","reason":"Atendimento iniciado."}
```

Agent/admin; somente transições da tabela da Spec 001. `reason` é obrigatório para `IN_PROGRESS → OPEN`; estado inválido retorna `409 INVALID_TRANSITION`. `RESOLVED` é terminal na Spec 001; reabertura é definida na Spec 002.

### Administrar membros

`POST /api/v1/workspaces/{workspaceId}/memberships` associa subject OIDC já existente a papel `REQUESTER`, `AGENT` ou `ADMIN`; somente admin. `PATCH /api/v1/workspaces/{workspaceId}/memberships/{subject}` altera papel/status com `expectedVersion` da membership. Remover/rebaixar último admin ativo retorna `409 LAST_ADMIN`. O servidor verifica subject e workspace, registra auditoria e nunca confia em papel do token como autorização de produto. A Spec 002 acrescenta desatribuição atômica quando responsável perde papel.

### Repetir triagem

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/triage-retries`

Agent/admin. Só permitido para tentativa `FAILED` ou `RETRY_SCHEDULED`, sem trabalho já em curso. `OUTCOME_UNKNOWN` exige confirmação humana explícita no body (`confirmPossibleDuplicateCharge: true`) e usa nova chave de operação; o sistema mostra aviso de chamada possivelmente já cobrada.

Rejeição de sugestão bem-sucedida não agenda outra tentativa nem torna essa rota elegível automaticamente. O pedido segue disponível para classificação manual.

## Contrato do evento interno

`TriageRequested.v1`: `{eventId,eventType,schemaVersion,occurredAt,workspaceId,requestId,correlationId,causationId}`. Sem texto, email, token ou conteúdo de prompt. Consumidor reabre dados autorizados pelo `requestId` e confere que `workspaceId` corresponde antes de executar. Versão desconhecida vai para DLQ sem tentativa de inferir payload.

Resultado validado do provedor: `HUMAN_REVIEW` exige `category`, `urgency`, `summary`; `REQUEST_CLARIFICATION` exige `question` de 1–300 caracteres Unicode após trim e não cria sugestão. Pergunta que solicite credencial/segredo é inválida e vai ao humano sem sugestão. A primeira rota leva a `NEEDS_REVIEW` com sugestão; a segunda, a `NEEDS_CLARIFICATION`. Resposta incerta/inválida ou falha terminal leva a `NEEDS_REVIEW` sem sugestão, com código seguro (`AI_UNCERTAIN`, `AI_INVALID_OUTPUT`, `AI_UNAVAILABLE`, `AI_BUDGET_BLOCKED` ou `AI_OUTCOME_UNKNOWN`). Após um complemento, nova tentativa de clarificação também leva ao humano sem sugestão, com `CLARIFICATION_LIMIT`. Nenhuma dessas rotas fecha ou rejeita o pedido.
