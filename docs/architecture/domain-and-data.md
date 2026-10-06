# Domínio e modelo de dados

Este documento detalha a linguagem e os limites propostos na [spec 001](../../specs/001-ai-assisted-request-triage/spec.md). É uma hipótese de design; validar durante os walkthroughs e ajustar a especificação antes da implementação.

## Contextos delimitados

| Contexto | Responsabilidade | Dados próprios | Não deve fazer |
| --- | --- | --- | --- |
| Workspace | Tenant, membros, papéis, política de intake e limites | Workspace, membership, intake credential metadata, policy | Classificar solicitação ou acessar tabelas de outro módulo |
| Requests | Entrada, estado operacional, histórico e consultas da fila | Request, request revision, request audit event | Chamar provedor externo diretamente |
| Triage | Criar sugestão, validar resposta, controlar revisão e consumo estimado | Suggestion, review, provider attempt, usage record | Aprovar sozinho ou escolher workspace a partir da resposta do modelo |
| Messaging | Outbox, publicação, consumo, idempotência e falha | Outbox event, inbox/idempotency record, delivery attempt | Tornar-se fonte de verdade do pedido |
| Knowledge (fase posterior) | Origem, ingestão, chunking, índice e busca autorizada | Knowledge source, processing job, chunk/embedding reference | Conceder autorização ou recuperar conteúdo cross-tenant |

Contexto é limite de linguagem e dependência no monólito; não implica serviço separado. Os módulos podem compartilhar a mesma instância PostgreSQL, mas acessam seus dados por APIs de aplicação e não por queries cruzadas arbitrárias.

## Agregados e invariantes

### Workspace

Raiz: `Workspace`. Membros e configurações pertencem a um workspace. Invariantes: existe sempre um administrador ativo; apenas administrador altera membros, intake token e limites; credencial pública é armazenada como hash e pode ser revogada; uso/limite é consultado antes de iniciar uma operação externa.

### Request

Raiz: `Request`. Campos conceituais: `requestId`, `workspaceId`, `originalText`, `submittedAt`, `source`, `status`, `version` e referências à triagem atual. O texto original não muda; correções e complementos viram revisões/audit events. Atualizações de estado verificam a versão para impedir lost update.

Invariantes: a solicitação pertence a um único workspace; só transições permitidas são aceitas; toda mudança material gera audit entry; uma sugestão pendente não é equivalente a uma solicitação aprovada; a origem pública não confere identidade de agente.

### TriageSuggestion

Raiz ou entidade versionada pertencente à solicitação. Armazena `suggestionId`, `requestId`, `workspaceId`, `revision`, categoria, urgência, resumo, referências recuperadas opcionais, schema/prompt/provider metadata, status de validação, timestamps e decisão humana. Não armazena chain-of-thought. A saída do modelo nunca contém campos de autorização, `workspaceId` confiável ou decisão de workflow.

## Tabelas conceituais

- `workspaces(id, name, status, created_at)`
- `memberships(workspace_id, identity_subject, role, status, created_at)` — unique por workspace/subject.
- `intake_credentials(id, workspace_id, token_hash, status, created_at, revoked_at)` — token em claro aparece apenas no momento controlado de emissão, se necessário.
- `workspace_policies(workspace_id, ai_enabled, max_calls, max_input_chars, updated_at)`
- `requests(id, workspace_id, original_text, status, source, current_suggestion_id, version, created_at, updated_at)` — índices começam sempre com `workspace_id` para consultas tenant scoped.
- `request_revisions(id, workspace_id, request_id, author_type, author_id, text, created_at)` — opcional se revisão de texto fora do original for necessária.
- `request_audit_events(id, workspace_id, request_id, actor_type, actor_id, event_type, from_state, to_state, correlation_id, created_at, safe_metadata)`.
- `triage_suggestions(id, workspace_id, request_id, revision, category, urgency, summary, rationale, schema_version, prompt_version, provider, model, validation_status, review_status, created_at)`.
- `triage_reviews(id, workspace_id, request_id, suggestion_id, reviewer_id, decision, edited_fields, comment, created_at)`.
- `ai_usage_records(id, workspace_id, request_id, provider_attempt_id, outcome, input_units, output_units, estimated_cost, created_at)` — evitar guardar prompt completo.
- `outbox_events(id, aggregate_type, aggregate_id, workspace_id, event_type, schema_version, payload, status, attempts, available_at, published_at, created_at)` — payload mínimo, sem texto original.
- `inbox_events(consumer, event_id, outcome, processed_at, correlation_id)` — unique `(consumer,event_id)`.
- `provider_attempts(id, workspace_id, request_id, attempt_no, outcome, safe_error_code, started_at, completed_at, correlation_id)`.
- *(later)* `knowledge_sources(id, workspace_id, name, checksum, status, parser_version, created_at, deleted_at)`.
- *(later)* `knowledge_chunks(id, workspace_id, source_id, chunk_no, content, embedding, metadata, index_version)`.

É preciso rever tipos/normalização, constraints, retenção e índices com queries reais e versão escolhida do PostgreSQL. `workspace_id` duplicado em tabelas filhas pode facilitar filtros e constraints compostas; adicionar chaves estrangeiras compostas `(workspace_id, request_id)` para impedir relações entre tenants no banco.

## Eventos de aplicação

Envelope mínimo: `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `workspaceId`, `aggregateId`, `correlationId`, `causationId` e payload estritamente necessário. Um evento transporta referência e contexto, não o pedido em texto aberto. Consumidores validam versão e autorização de workspace antes de acionar um caso de uso.

Eventos iniciais possíveis: `RequestSubmitted.v1`, `TriageRequested.v1`, `TriageCompleted.v1`, `TriageFailed.v1`, `SuggestionReviewed.v1`, `RequestStatusChanged.v1`. São contratos de integração internos versionados; não tornam o sistema event-sourced.

## Consultas e CQRS

`GetQueue` e `GetRequestDetails` podem projetar DTOs eficientes com join e paginação dentro do módulo requests. `ReviewSuggestion` é comando que valida papel, versão, estado e vínculo entre sugestão e request na mesma transação. Não materialize projeções assíncronas até que latência/carga justifique eventual consistency e novos mecanismos de reconstrução.
