# Contrato HTTP proposto — Spec 002

> **Para estudar:** use os exemplos de payload para discutir transições, autorização e conflito de versão. Tente prever a resposta quando o responsável perde acesso ou quando dois pedidos de workspaces diferentes são vinculados. Nenhuma rota foi implementada.

Estende o [contrato da Spec 001](../001-ai-assisted-request-triage/api-contract.md). Ainda não é OpenAPI implementada. Todas as rotas exigem Bearer OIDC e membership ativo. `workspaceId` seleciona contexto; o servidor autoriza. Erros usam o envelope da Spec 001; recurso fora do escopo retorna `404` sem confirmar existência.

## Convenções

- Toda escrita abaixo recebe `expectedVersion` inteiro positivo no JSON; conflito de versão retorna `409 VERSION_CONFLICT`. O servidor devolve `version` atual apenas a quem pode ler o recurso.
- Campos de texto livre (`reason`, `question`, `answer`, `resolutionNote`) exigem 1–2.000 caracteres Unicode após remoção de espaços nas bordas; entradas vazias após essa normalização retornam `422`. O OpenAPI da implementação deve registrar o mesmo limite. Não são enviados em eventos nem logs.
- A resposta de detalhe acrescenta `reportedImpact`, `confirmedImpact`, `calculatedPriority`, `effectivePriority`, `priorityOverrideReason` (somente agent/admin), `assigneeSubject` (somente agent/admin), `sla: {cycle, openedAt, dueAt, pausedAt, breachedAt, resolvedAt, businessMinutesConsumed, businessMinutesRemaining}` e estado `WAITING_REQUESTER`. `dueAt` é `null` durante pausa aberta. Requester vê seu prazo e histórico próprio, sem identidade técnica de agente nem referência a pedido alheio.
- Lista de agente aceita `assignee=me|unassigned|<subject>`, `priority=P1|P2|P3|P4` e `breached=true|false`; cursor vincula filtros, workspace e ordenação estável. Requester não pode usar filtro de responsável.
- Histórico é paginado e só retorna campos autorizados para o papel. `409` para estado/conflito elegível, `422` para payload inválido, `404` para recurso fora do workspace/autoria.

## Extensões de criação e classificação

`POST /api/v1/workspaces/{workspaceId}/requests` aceita `reportedImpact` opcional (`SINGLE_USER`, `TEAM`, `WORKSPACE`); a chave de idempotência da Spec 001 inclui esse campo no corpo canonicalizado. O solicitante não pode enviar `confirmedImpact`, prioridade ou responsável.

`POST .../{requestId}/manual-classification` e `POST .../{requestId}/reviews` recebem `confirmedImpact`. No momento da decisão humana, servidor calcula prioridade pela matriz da Spec 002 e cria o primeiro ciclo. Override opcional usa `{ "effectivePriority": "P1", "priorityReason": "Impacto em serviço compartilhado." }`; motivo é obrigatório quando a prioridade efetiva difere da calculada. IA não fornece esses campos.

## Atribuição

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/assignment`

```json
{"expectedVersion":5,"assigneeSubject":"subject-do-agente","reason":"Assumir atendimento"}
```

Agent/admin pode assumir pedido `OPEN` sem responsável apenas para si. Responsável atual ou admin pode transferir para outro membro ativo `AGENT`/`ADMIN` do mesmo workspace, com motivo não vazio; admin pode enviar `assigneeSubject: null` para desatribuir. Resposta `200` traz `requestId`, `assigneeSubject`, `version`. Alvo inválido ou sem membership retorna `422 INVALID_ASSIGNEE` sem revelar detalhes de outro workspace.

## Prioridade

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/priority`

```json
{"expectedVersion":6,"confirmedImpact":"TEAM","urgency":"HIGH","effectivePriority":"P1","reason":"A equipe inteira está parada."}
```

Agent/admin; pedido em `OPEN`, `IN_PROGRESS` ou `WAITING_REQUESTER`. Sem `effectivePriority`, usa valor calculado. Com valor diferente, `reason` é obrigatório. Resposta inclui prioridade calculada/efetiva, versão e prazo recalculado; `dueAt` continua `null` enquanto pausado.

## Espera, resposta, resolução e reabertura

- `POST .../{requestId}/waits` — responsável/admin em `IN_PROGRESS`; `{ "expectedVersion": 7, "question": "Qual mensagem de erro aparece?" }`; leva a `WAITING_REQUESTER`, pausa ciclo.
- `POST .../{requestId}/responses` — solicitante autor do pedido em `WAITING_REQUESTER`; `{ "expectedVersion": 8, "answer": "Aparece acesso negado." }`; leva a `IN_PROGRESS`, fecha pausa. Repetição com versão antiga retorna conflito e não duplica resposta.
- `POST .../{requestId}/wait-cancellation` — admin em `WAITING_REQUESTER`; `{ "expectedVersion": 8, "reason": "Solicitante sem acesso à conta; prosseguir com dados disponíveis." }`; leva a `IN_PROGRESS` e fecha pausa. Resposta tardia do requester não duplica retomada.
- `POST .../{requestId}/resolution` — responsável/admin em `IN_PROGRESS`; `{ "expectedVersion": 9, "resolutionNote": "Permissão corrigida e validada." }`; leva a `RESOLVED`, fecha ciclo.
- `POST .../{requestId}/reopen` — solicitante autor ou agent/admin até 7 dias corridos da resolução; `{ "expectedVersion": 10, "reason": "O erro voltou." }`; leva a `OPEN`, cria ciclo novo. Fora da janela: `409 REOPEN_WINDOW_EXPIRED`.

## Duplicados

`POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/duplicate-link`

```json
{"expectedVersion":11,"canonicalRequestId":"id-canonico","canonicalExpectedVersion":3,"reason":"Mesmo incidente relatado em outro pedido."}
```

Agent/admin. `requestId` é secundário não resolvido; canônico deve estar no mesmo workspace e não pode ser secundário. Travar ambos em ordem estável; conflito de qualquer versão retorna `409 VERSION_CONFLICT` sem alteração. `POST .../{requestId}/duplicate-unlink` com `expectedVersion`, `canonicalExpectedVersion` e `reason` remove vínculo mantendo auditoria. Agent/admin vê IDs vinculados; requester não recebe campo de vínculo ou ID do outro pedido.

## Eventos e evolução

Eventos internos futuros de atribuição, prioridade ou prazo carregam apenas IDs, versão, instante e códigos/enums. Pergunta, resposta, resolução e motivo ficam no banco. Alterações do contrato da Spec 001 serão versionadas em OpenAPI junto do primeiro PR que implementar a Spec 002; clientes antigos não podem enviar campos de autoridade como prioridade/responsável.
