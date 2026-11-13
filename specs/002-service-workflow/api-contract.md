# Contrato HTTP proposto — Spec 002

> **Para estudar:** cada rota abaixo é uma proposta para a dupla revisar antes de implementar. Pergunte, para cada uma: quem pode chamar, qual versão ela exige, o que o histórico deve registrar e qual erro o solicitante de outro pedido deve receber. Nenhum servidor atende a este contrato hoje.

Complementa o [contrato da Spec 001](../001-ai-assisted-request-triage/api-contract.md). Convenções, envelope de erro, paginação e concorrência são os mesmos.

## Convenções adicionais

- Toda escrita de atendimento recebe `expectedVersion` do pedido; perda de corrida responde `409 VERSION_CONFLICT`.
- Operações sobre dois pedidos (duplicados) recebem as duas versões e falham inteiras, sem vínculo parcial.
- Datas de prazo são RFC 3339 em UTC; o cálculo é feito no servidor, no calendário fixo da spec.
- `reason`, `question`, `answer` e `resolutionNote` passam por limites de tamanho e não aparecem em log ou evento.
- Códigos próprios desta spec: `IMPACT_REQUIRED`, `PRIORITY_OVERRIDE_REASON_REQUIRED`, `NOT_ASSIGNEE`, `ALREADY_ASSIGNED`, `REOPEN_WINDOW_EXPIRED`, `DUPLICATE_LINK_INVALID`, `WAIT_NOT_OPEN`.

## Extensões de criação e classificação

`POST /api/v1/workspaces/{workspaceId}/requests` aceita `reportedImpact` opcional (`SINGLE_USER`, `TEAM`, `WORKSPACE`), que é percepção do solicitante e não define prioridade.

`POST .../{requestId}/manual-classification` e `POST .../{requestId}/reviews` passam a exigir `confirmedImpact` para levar o pedido a `OPEN`. Sem ele, a resposta é `422 IMPACT_REQUIRED`. A resposta inclui a prioridade calculada, o ciclo criado e o `dueAt`.

## Atribuição

`POST .../{requestId}/assignments`

```json
{"expectedVersion":7,"assigneeSubject":"agent-bruno"}
```

Assume ou transfere. Assumir exige pedido `OPEN` sem responsável. Transferir exige ser o responsável atual ou admin e `reason` não vazio. Destinatário precisa de membership ativo com papel `AGENT`/`ADMIN` no workspace. Conflito de corrida responde `409 ALREADY_ASSIGNED` ou `409 VERSION_CONFLICT`.

`DELETE .../{requestId}/assignments` desatribui; somente admin, com `expectedVersion` e `reason`.

## Prioridade

`PUT .../{requestId}/priority`

```json
{"expectedVersion":9,"confirmedImpact":"TEAM","acceptedUrgency":"HIGH","override":"P1","reason":"Equipe inteira bloqueada antes do deploy."}
```

Agent/admin. Sem `override`, a prioridade é a da matriz. Com `override`, `reason` é obrigatório (`422 PRIORITY_OVERRIDE_REASON_REQUIRED`) e a resposta devolve `calculatedPriority` e `effectivePriority`. O recálculo do prazo preserva os minutos úteis já consumidos e eventual violação.

## Espera, resposta, resolução e reabertura

`POST .../{requestId}/waits` — responsável ou admin, em `IN_PROGRESS`, com `{ "expectedVersion", "question" }`. Pausa o relógio e vai a `WAITING_REQUESTER`. Agente não responsável recebe `403 NOT_ASSIGNEE`.

`POST .../{requestId}/waits/current/answer` — somente o solicitante do pedido, com `{ "expectedVersion", "answer" }`. Fecha a pausa uma única vez e volta a `IN_PROGRESS`. Outro requester recebe `404`.

`DELETE .../{requestId}/waits/current` — somente admin, com `{ "expectedVersion", "reason" }`. Cancela a espera, retoma o prazo e volta a `IN_PROGRESS`. Resposta tardia do requester recebe conflito.

`POST .../{requestId}/resolutions` — responsável ou admin, com `{ "expectedVersion", "resolutionNote" }`. Fecha o consumo do ciclo e marca violação se houver.

`POST .../{requestId}/reopenings` — solicitante do pedido ou agent/admin, com `{ "expectedVersion", "reason" }`, até `resolvedAt + 7 dias corridos` (instante exato incluído). Fora da janela: `409 REOPEN_WINDOW_EXPIRED`. Cria novo ciclo e preserva os anteriores.

## Duplicados

`POST .../{requestId}/duplicate-link`

```json
{"expectedVersion":4,"canonicalRequestId":"req-123","canonicalExpectedVersion":11,"reason":"Mesmo erro de acesso ao ambiente."}
```

Agent/admin, no mesmo workspace. Rejeita auto-vínculo, cadeia, canônico já secundário, pedido resolvido e versões divergentes, com `409 DUPLICATE_LINK_INVALID`. Pedido de outro workspace responde `404`.

`DELETE .../{requestId}/duplicate-link` desvincula com `expectedVersion` e `reason`.

O requester nunca recebe `canonicalRequestId` de pedido que não é dele, nem no detalhe nem no histórico.

## Consulta

`GET /api/v1/workspaces/{workspaceId}/requests?assignee=&priority=&overdue=&status=&cursor=&limit=` — filtros adicionais para agent/admin. `overdue=true` é derivado do tempo útil consumido, não de job. Requester continua vendo apenas as próprias solicitações, sem campos de outro pedido.

O detalhe do pedido passa a expor: responsável, impacto informado e confirmado, prioridade calculada e efetiva com motivo, ciclo atual (`openedAt`, `dueAt` ou minutos úteis restantes, `breachedAt`), pausa aberta, ciclos anteriores e vínculo de duplicado quando autorizado.

## Eventos e evolução

Esta spec não exige novo evento de broker. Se um alerta de prazo for introduzido depois, o evento leva apenas IDs, prioridade e instantes — nunca pergunta, resposta ou nota de resolução — e o consumidor precisa ser idempotente, sem alterar o cálculo do prazo. Qualquer campo novo no contrato entra como opcional e com nota de compatibilidade.
