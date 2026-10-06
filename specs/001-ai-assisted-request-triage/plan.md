# Plan 001: Technical approach for assisted request triage

- **Status:** Proposed; implement only after resolving blocking questions in `spec.md`.
- **Related spec:** [`spec.md`](spec.md)
- **Architecture records:** [`docs/architecture/decisions/`](../../docs/architecture/decisions/)

## 1. Design goals

Make the simplest system that demonstrates production-relevant reasoning: explicit domain boundaries, tenant isolation, transactional state changes, resilient asynchronous work, controlled AI use, observable failure and a verifiable delivery path. Keep the core workflow useful without the AI provider or broker.

## 2. Proposed technology baseline

| Layer | Proposed choice | Reason / constraint |
| --- | --- | --- |
| API and worker | Java LTS + Spring Boot | Familiar to both mentees; share domain/application code in one deployable backend |
| Relational data | PostgreSQL + Flyway | Transactions, constraints, migrations and durable outbox; one source of truth for request state |
| Vector search | PostgreSQL `pgvector` in the RAG milestone | Keep workspace filtering and transactional context close to domain data; benchmark and test filters before wider use |
| Broker | RabbitMQ, AMQP | Exercise durable queues, publisher confirms, acknowledgements, redelivery and DLQ; not a system-of-record |
| Web client | React + TypeScript | Complete request/review workflow and a practical API integration surface |
| API contract | OpenAPI | Reviewable boundary and generated/client contract tests where useful |
| Local runtime | Docker Compose | Reproducible API, database, broker and optional fake AI adapter |
| CI | GitHub Actions | Build, tests, static checks, container build and dependency/security checks |
| Cloud learning | Temporary AWS sandbox | Learn deployment, network boundaries, secrets, logs/metrics, cost and teardown with synthetic data only |

Pin supported runtime/dependency versions when implementation begins. No framework or AWS service in this proposal is yet a deployed or tested dependency.

## 3. Module boundaries

The deployable backend contains these modules. Modules expose application interfaces, not repositories or ORM entities.

1. **workspace** — membership, roles, intake configuration and usage policy. Owns workspace access decisions.
2. **requests** — request aggregate, immutable original text, lifecycle, status transitions, filters and audit history.
3. **triage** — commands and suggestion lifecycle, provider-neutral triage port, schema validation, usage accounting and review actions.
4. **knowledge** *(later milestone)* — source lifecycle, parsing, chunking, embeddings, workspace-scoped retrieval and citation assembly.
5. **messaging** — outbox dispatcher, broker configuration, message envelope, idempotency ledger, retries and DLQ operations.
6. **identity** — authentication integration and mapping identity claims to workspace memberships; never owns domain authorization alone.
7. **web/API** — REST controllers, input/output DTOs, OpenAPI, error mapping and authentication middleware.

Allowed direction: web and infrastructure adapters call application use cases; application coordinates domain and ports; domain has no framework, database, broker, UI or provider dependency. `messaging` consumes application commands through explicit ports. Avoid cross-module table access and direct imports of another module's persistence layer.

## 4. Domain model and lightweight CQRS

Initial aggregates:

- `Workspace` protects settings, membership/role operations, intake-token rotation and quota configuration.
- `Request` protects immutable original text, legal status transitions and link to current triage revision.
- `TriageSuggestion` is a versioned proposal. Review state and reviewer identity are explicit; provider output cannot mutate request state directly.
- `KnowledgeSource` is introduced later and owns processing/deletion lifecycle for one workspace.

Commands include `SubmitRequest`, `RequestTriage`, `RecordTriageResult`, `ReviewSuggestion`, `ClassifyManually`, `ChangeRequestStatus` and later `IngestKnowledgeSource`. Queries include `GetQueue`, `GetRequestDetails`, `GetRequestHistory` and later `SearchKnowledge`. CQRS here separates intent and read shape in one application and database. Begin with ordinary relational tables and transactions. Do not implement event sourcing, projections in a second store, or a separate read database without a measured requirement.

## 5. Transaction and event flow

### Request intake

1. Validate size, token, rate limit and allowed fields.
2. Resolve intake token to one workspace. Never accept arbitrary tenant selection from the body.
3. In one PostgreSQL transaction insert `Request`, append audit entry, and insert `RequestSubmitted` into `outbox_events` with stable event ID and schema version.
4. Return an opaque receipt promptly; AI is not called on the request thread.

### Outbox publisher

- Claim available rows with bounded batches and lease/locking safe for multiple publisher instances.
- Publish persistent message with stable `eventId`, schema version, workspace/request IDs, occurred-at, correlation ID and trace context. Do not include raw request body.
- Require RabbitMQ publisher confirm before marking the row published. If confirm is absent/negative, retain for retry.
- Ensure exchange, routing key, durable queue, dead-letter route and topology declaration are documented as code/config.
- Monitor oldest pending row and publish failures. Provide safe replay for retained records.

### Triage worker

- Acknowledge only after result/failure state is durably committed.
- Use a unique constraint/idempotency record keyed by event ID and operation type. A redelivery returns the prior outcome rather than generating a new suggestion.
- Fetch request through workspace-aware application service. Event IDs are references, not authorization.
- For transient provider/network errors: bounded exponential delay with jitter, then DLQ. For invalid schema/policy/permanent input: record classified failure and do not retry indefinitely.
- A poison message in DLQ is visible to a safe operator workflow; replay reuses request/event identity with an explicit replay record.

Expected delivery is at least once. There is a crash window after provider success and before result commit; idempotency limits duplicate state writes, while provider request idempotency is used only if supported. Avoid claiming exactly-once external inference.

## 6. AI adapter and RAG boundary

`TriageProvider` accepts a minimized, schema-defined input and returns an untrusted value object. `TriageApplicationService` owns quota check, provider timeout, schema validation, persistence and state transition. A `FakeTriageProvider` supplies deterministic local scenarios. A real adapter is configured only in an explicit sandbox and never required for tests.

Prompt input includes only original request text and, after the RAG milestone, approved workspace-local excerpts. Provider response is parsed into a strict schema with category/urgency allowlists and summary/rationale lengths. Validate that outputs cannot set `workspaceId`, permissions, approval or workflow status. Version prompts and schemas; collect latency, outcome and token/cost estimates without raw text.

RAG milestone stages: (1) synthetic knowledge fixtures and evaluation set; (2) source upload/import limited to small Markdown/text files; (3) parse and chunk with source, checksum, workspace and version metadata; (4) embed and store vectors; (5) query with mandatory workspace predicate, bounded `topK` and similarity threshold; (6) attach source IDs/excerpts; (7) evaluate retrieval relevance, citation correctness, cross-tenant leakage and deletion; (8) enable only if results beat a documented baseline. Retrieval is advisory; it does not expand user permissions. Treat retrieved prompt-injection text as untrusted data.

## 7. Security, identity and tenancy

- Authentication creates an identity principal; a membership service resolves allowed workspace and role for each operation.
- API authorization and data access both scope by tenant. Repository methods require workspace ID; tests attempt guessed IDs and filters.
- Public intake token is hashed at rest, revocable, rate-limited, scoped to a single workspace and excluded from logs/URLs in telemetry. Prefer header over path if operational tooling permits; settle exact route in OpenAPI design.
- Use explicit CORS, CSRF strategy if cookie auth, secure headers, request-size limits, validation, brute-force controls and secrets management.
- For PostgreSQL, consider row-level security as defense in depth only after transaction/session context and connection pooling are proven; it does not replace application authorization or negative tests.
- Security events use correlation ID, actor/workspace IDs where safe, event type and outcome; do not log raw body, prompts, access tokens or full model output.

## 8. API and UI

REST resources keep client inputs separate from persistence models. Version the API at `/api/v1`. Use consistent problem/error envelope with stable machine code, safe message and correlation ID. State changes include expected version/ETag or equivalent optimistic concurrency to prevent reviewers overwriting one another. Paginate queue results and make sorting/filtering allowlisted.

UI workflow: sign in → select/current workspace → queue and filters → request detail with original content and history → separate AI suggestion with sources/status → accept, edit-and-accept, reject, manual classify → subsequent status. Visual labeling distinguishes requester text, retrieved excerpts, provider suggestion and human-edited value. Show a recoverable pending/failure state; never use color alone for urgency/status.

## 9. Quality and test strategy

- **Domain unit tests:** state machine, allowed transitions, immutable original, role decisions, quota rules and review requirements.
- **Application tests:** use case orchestration, transaction boundaries, idempotency decisions and provider failures with fakes.
- **Integration tests:** PostgreSQL/Flyway constraints, outbox transaction, publisher confirm behavior, RabbitMQ redelivery/DLQ and `pgvector` tenant filtering (later).
- **API contract tests:** OpenAPI validation, auth/error envelope, pagination and cross-tenant negative cases.
- **Frontend tests:** intake validation, request states, keyboard review, suggestion distinction and error/manual fallback.
- **End-to-end:** synthetic request through outbox → broker → fake provider → review → queue state; broker/provider offline path; replay behavior.
- **Security checks:** secret scanning, dependency review, container scanning where available, input-size abuse and tenant boundary tests.
- **Resilience experiments:** stop RabbitMQ, kill worker between delivery and acknowledgement, inject duplicate delivery, timeout provider and restore broker. Record expected state and telemetry.

## 10. Observability and operations

Structured logs include event/request/workspace correlation IDs, module, outcome and latency, but omit raw text. Metrics include intake accepted/rejected, outbox age/size, publish confirms/failures, queue depth/age, retry/DLQ counts, worker duration, provider latency/error/quota, validation failure, RAG retrieval count/no-source and cost estimate. Never label metrics with unbounded request IDs or raw user text.

Readiness checks distinguish API, database and broker health. Liveness must not fail merely because an optional AI provider is down. Document startup order, migration behavior, backup/restore rehearsal, DLQ replay, incident examples and teardown. For the AWS learning environment, use separate sandbox config, minimum IAM, secret store, budget/alerts, synthetic fixtures and destroy procedure.

## 11. Cloud and deployment progression

1. Run all dependencies locally in Docker Compose.
2. Build immutable API and worker container images and run them locally.
3. Deploy a temporary AWS sandbox from Terraform: ECS Fargate API and worker, PostgreSQL/RDS with vector extension if supported by selected version, managed RabbitMQ/Amazon MQ only if account quotas/cost allow, S3 only for later source ingestion, Secrets Manager, CloudWatch and restricted network paths.
4. Use synthetic data, no custom production domain, controlled access, explicit teardown and a review of incurred cost.
5. Only discuss a real pilot after separate authorization, data review, threat model, restore test, operating budget and formal owner approval.

The AWS selection is an educational assumption; specific service availability, pricing, account permissions, quotas and regional support must be checked when implementing. MCP has no role in first deployment.

## 12. MCP extension (post-MVP, optional)

If an MCP server is added, host it as a separately authenticated adapter over application use cases, not direct database access. Begin with one read-only tool such as `search_requests` or `get_request_summary`; require user/workspace authorization on every invocation, bounded filters/results, safe output minimization, rate limits and audit logs. No provider prompt or tool argument can choose an arbitrary tenant. Add negative tests for cross-workspace search and prompt-injected request content. Keep MCP disabled if identity propagation cannot be demonstrated.

## 13. Delivery sequence

Follow task groups in `tasks.md`: repository/tooling and domain; workspace security; request/API/UI; outbox/RabbitMQ; triage adapter/human review; end-to-end reliability; RAG evaluation; temporary cloud sandbox; optional MCP. A group does not start merely because its technology is interesting: its prerequisites and earlier acceptance evidence must pass.
