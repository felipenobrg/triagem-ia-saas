# Contrato HTTP inicial

Contrato funcional da Spec 001. Antes da primeira implementação, publicar OpenAPI que reflita estes recursos e manter ambos alinhados. Exemplos usam valores sintéticos.

## Convenções

- Prefixo `/api/v1`; JSON UTF-8; token `Authorization: Bearer <OIDC access token>`.
- `workspaceId` seleciona contexto; servidor valida membership e papel em cada chamada.
- IDs opacos; datas RFC 3339 UTC. Listas usam cursor opaco, `limit` limitado no servidor e ordenação estável por `(createdAt,id)`.
- Escritas de estado recebem `If-Match: <version>` (ou campo `expectedVersion` se o framework não suportar ETag). Concorrência perdida responde `409 VERSION_CONFLICT`.
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

Agent/admin recebem fila do workspace. Requester recebe somente as próprias, ignorando filtro de `requesterId` enviado pelo cliente. Resposta inclui `items`, `nextCursor` e `hasMore`; não aceita query arbitrária.

### Detalhe e histórico

`GET /api/v1/workspaces/{workspaceId}/requests/{requestId}`

Retorna texto original apenas a membros autorizados do workspace, estado, versão, sugestão atual, tentativas em forma segura e histórico paginado/autorizado. Requester só acessa recurso de sua autoria.

### Revisar sugestão

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/reviews`

```json
{"decision":"APPROVE_EDITED","expectedVersion":4,"category":"ACCESS","urgency":"HIGH","summary":"Acesso ao ambiente bloqueado."}
```

Decisões `APPROVE`, `APPROVE_EDITED`, `REJECT`; agent/admin. Um review é imutável. Aprovação exige sugestão validada e versão atual. Rejeição conserva sugestão e mantém item disponível para classificação manual.

### Classificação manual

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/manual-classification`

```json
{"expectedVersion":4,"category":"ACCESS","urgency":"HIGH","summary":"Acesso ao ambiente bloqueado.","reason":"..."}
```

Agent/admin; registra autor e origem manual, transiciona para `OPEN` atomicamente com auditoria.

### Repetir triagem

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/triage-retries`

Agent/admin. Só permitido para tentativa `FAILED` ou `RETRY_SCHEDULED`, sem trabalho já em curso. `OUTCOME_UNKNOWN` exige confirmação humana explícita no body (`confirmPossibleDuplicateCharge: true`) e usa nova chave de operação; o sistema mostra aviso de chamada possivelmente já cobrada.

## Contrato do evento interno

`TriageRequested.v1`: `{eventId,eventType,schemaVersion,occurredAt,workspaceId,requestId,correlationId,causationId}`. Sem texto, email, token ou conteúdo de prompt. Consumidor reabre dados autorizados pelo `requestId` e confere que `workspaceId` corresponde antes de executar. Versão desconhecida vai para DLQ sem tentativa de inferir payload.
