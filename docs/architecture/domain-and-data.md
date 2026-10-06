# Domínio e modelo de dados

Este é o modelo conceitual da [Spec 001](../../specs/001-ai-assisted-request-triage/spec.md), para o MVP local. Não há código nem esquema de banco implementado ainda. RAG/MCP são extensões fora desta modelagem inicial.

## Linguagem ubíqua

| Termo | Significado e limite |
| --- | --- |
| Workspace | Tenant e raiz da membership/autorização. |
| Membership | Relação ativa entre subject autenticado e workspace, com papel `REQUESTER`, `AGENT` ou `ADMIN`. |
| Solicitação | Texto original imutável, autor, estado operacional e versão concorrente. |
| Tentativa de triagem | Execução assíncrona, com seu próprio estado e metadados de provedor. |
| Sugestão | Saída validada sintaticamente, ainda não confiável como decisão até revisão humana. |
| Revisão | Decisão imutável de agent/admin sobre uma sugestão/version, possivelmente com edição. |
| Evento | Notificação versionada entre partes do sistema; não é fonte de verdade nem event sourcing. |

Categorias e estados estão na spec. Vocabulário novo é decidido com participantes antes de aparecer em API ou banco.

## Bounded contexts propostos

| Contexto | Responsabilidade | Proprietário dos dados |
| --- | --- | --- |
| Identity/workspace | Resolver subject OIDC, membership, papéis e workspace permitido | workspace, membership |
| Requests | Criar e transicionar solicitações, histórico, fila e consultas | request, request audit, idempotency scope de criação |
| Triage | Orquestrar tentativa, validar sugestão, revisão, orçamento e adapter IA | attempts, suggestions, reviews, usage/budget |
| Messaging | Outbox, publicação, inbox, retry e DLQ operacionais | outbox, inbox, delivery attempt |
| API | Transporte HTTP, DTO, autenticação de borda e contrato OpenAPI | Sem estado de negócio próprio |

Contexto é boundary no monólito, não deployment. Outro módulo usa API pública de aplicação; não importa package interno nem altera tabela alheia. Spring Modulith verifica cycles/internal access. O banco compartilhado não elimina ownership e autorização.

## Agregados e invariantes

### Workspace e Membership

Keycloak autentica. A aplicação consulta membership ativo no banco em cada comando/consulta de negócio. O último admin ativo não pode ser removido/rebaixado sem substituição na mesma operação. Role e workspace enviados pelo frontend ou evento não autorizam por si próprios.

### Request

Raiz de agregado: `Request`. Campos conceituais: `id`, `workspaceId`, `requesterSubject`, `originalText`, `status`, `version`, `createdAt`, `updatedAt`. Texto inicial imutável. O método de domínio/caso de uso valida transição; update condicional pela versão previne lost update. Requester lê somente próprias; agente/admin opera fila no workspace.

`Request` não contém estado da tentativa IA. Sugestão, revisão e histórico são registros separados, porém suas alterações de workflow e audit ocorrem atomicamente. Uma sugestão não pode ser aprovada depois que request/suggestion/version deixou de ser elegível.

### Triage Attempt e Suggestion

Tentativa tem ID, request/workspace, sequência, estado, provider/model, schema/prompt version, horários, uso, erro seguro e correlation ID. Sugestão contém somente categoria, urgência e resumo limitados, resultado de validação e vínculo à tentativa. Não guarda chain-of-thought, prompt integral ou campos de autoridade. Timeout ambíguo fica `OUTCOME_UNKNOWN` até resolução explícita.

### Review

Registro imutável contém agente, decisão, valores aceitos/alterados, versão da sugestão e instante. A alteração de request para `OPEN` e evento de audit pertencem à mesma transação. Concorrência é decidida por versionamento otimista.

## Modelo relacional indicativo

- `workspaces(id, name, status, created_at)`
- `memberships(workspace_id, subject, role, status, created_at)` unique `(workspace_id,subject)`
- `requests(workspace_id,id,requester_subject,original_text,status,version,created_at,updated_at)` unique `(workspace_id,id)`
- `request_audit_events(workspace_id,id,request_id,actor_type,actor_id,action,from_state,to_state,version,correlation_id,created_at,safe_metadata)`
- `triage_attempts(workspace_id,id,request_id,attempt_no,status,provider,model,schema_version,prompt_version,started_at,finished_at,safe_error_code,correlation_id)` unique `(workspace_id,request_id,attempt_no)`
- `triage_suggestions(workspace_id,id,request_id,attempt_id,category,urgency,summary,validation_status,created_at)`
- `triage_reviews(workspace_id,id,request_id,suggestion_id,reviewer_subject,decision,edited_values,created_at)`
- `idempotency_records(scope_hash,key_hash,request_hash,response_ref,created_at,expires_at)` unique `(scope_hash,key_hash)`
- `outbox_events(event_id,event_type,schema_version,workspace_id,aggregate_id,payload,status,attempts,next_attempt_at,lease_until,created_at,published_at)`
- `inbox_events(consumer,event_id,processed_at,result_ref)` unique `(consumer,event_id)`
- `ai_budget_period(period,limit_usd,reserved_usd,reported_usd,blocked)`; `ai_usage` guarda contagem/custo sem conteúdo livre.

Usar FK composta `(workspace_id,request_id)` nas filhas, índice de fila começando por workspace e query com predicado tenant. RLS pode ser estudado como hardening depois que a autorização da aplicação estiver implementada/testada; não substitui ela. O esquema final deve seguir queries e migrations reais.

## Eventos e CQRS

Envelope `eventId`, `eventType`, `schemaVersion`, `occurredAt`, `workspaceId`, `aggregateId`, `correlationId`, `causationId`, sem texto ou PII. Worker verifica schema, recarrega entidade autorizada por API de aplicação e garante correspondência workspace/request.

Eventos possíveis: `TriageRequested.v1`, `TriageCompleted.v1`, `TriageFailed.v1`, `SuggestionReviewed.v1`, `RequestStatusChanged.v1`. Publicação é outbox + Rabbit; at-least-once. Não se reconstrói estado do produto pelo log de eventos.

`ListQueue` e `GetRequestDetails` são consultas autorizadas; `CreateRequest`, `ReviewSuggestion` e `ChangeRequestStatus` são comandos que aplicam invariantes. Inicialmente usam PostgreSQL compartilhado e consistência transacional. Projeções assíncronas só entram se medição justificar atraso e custo de reconstrução.
