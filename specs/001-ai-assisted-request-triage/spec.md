# Spec 001: Triagem assistida de solicitações

- **Status:** Draft for review
- **Owner:** Product team
- **Created:** 2026-10-06
- **Target:** portfolio MVP on synthetic data
- **Source of truth:** this document defines expected product behavior; `plan.md` defines the proposed implementation.

## 1. Problem

Small operations and support teams receive requests in free text. Manual classification consumes time; automatic classification without review can misroute work or invent facts. The product must organize incoming requests and use AI as a reversible suggestion, with clear human control, traceable state, and a manual path when dependencies fail.

## 2. Goals

- Provide a multi-workspace SaaS workflow for intake, triage, review, and request tracking.
- Preserve original requester text and distinguish it from AI-generated suggestions and human edits.
- Demonstrate DDD, modularity, lightweight CQRS, transactional outbox, RabbitMQ, idempotency, tests, CI, observability, and a controlled RAG extension.
- Produce a deployable educational portfolio project using synthetic content and documented trade-offs.

## 3. Non-goals for the first release

- Use by real operational teams or with personal, confidential, clinical, financial, or employer data.
- Guaranteeing that AI classification is correct or suitable for safety-critical work.
- Autonomous approvals, external actions, outbound messaging, or calling client systems.
- Public self-service signup, billing/subscriptions, marketplace, SSO, or organization directory sync.
- Microservices, Kubernetes, event sourcing, separate CQRS databases, or exactly-once delivery.
- RAG over unrestricted internet content, cross-workspace knowledge search, or using retrieval as authorization.
- MCP tools that mutate requests, run shell/SQL, or take action on behalf of an operator.

## 4. Actors and roles

| Actor | Permissions |
| --- | --- |
| Workspace administrator | Manage workspace settings, members/roles, intake key, knowledge sources and usage limits; read and manage requests in the workspace |
| Agent | Read queue and history; review, edit, accept or reject suggestions; update permitted request status |
| Requester | Submit a request through the workspace's controlled intake form; receives a non-sensitive confirmation reference |
| Background worker | Process only the workspace and request identifiers carried by an authenticated internal event; no user-wide access |
| AI provider | Receives only minimized request text and authorized retrieval context for one triage operation; no authority to access the product directly |

For the educational MVP, workspace and initial administrator provisioning may use a documented seed/bootstrap command. Member management remains inside the workspace boundary. Public intake uses a revocable workspace-specific token, rate limits, validation and no file attachment support.

## 5. Domain language

- **Workspace:** tenant boundary for members, requests, settings, usage and knowledge sources.
- **Request:** original submission and its lifecycle; retains immutable original text and current operational status.
- **Triage suggestion:** versioned, untrusted proposal for category, urgency and concise summary.
- **Review:** human decision to accept, edit-and-accept, or reject a suggestion.
- **Knowledge source:** workspace-owned document eligible for retrieval after explicit ingestion and validation.
- **Usage allowance:** workspace-configured bound for AI calls/tokens per time window; not a billing ledger.
- **Outbox event:** durable record of a domain/application event awaiting broker publication.

## 6. Functional requirements

### Workspace and access

- **FR-001 — Workspace isolation:** Every authenticated read/write is authorized against the active workspace. The service derives the allowed workspace from the authenticated membership, never solely from a client-supplied ID.
- **FR-002 — Roles:** An administrator can invite/remove members and assign administrator or agent role. An agent cannot manage members, intake credentials, usage settings, or knowledge sources.
- **FR-003 — Intake boundary:** The workspace can enable/disable and rotate a public intake token. Intake validates token, payload size, rate limit, and required fields; it returns an opaque reference and does not reveal whether another workspace's request exists.

### Requests and workflow

- **FR-004 — Request creation:** A valid submission stores original text, workspace, creation time, source and a stable request identifier. The original text is immutable; edits are stored separately.
- **FR-005 — Queue and search:** Authorized agents can list and filter their workspace's requests by status, category, urgency and date, and open a request's history.
- **FR-006 — State transitions:** A request follows explicit valid transitions: `RECEIVED → TRIAGE_PENDING → NEEDS_REVIEW → OPEN → IN_PROGRESS → RESOLVED`; a reviewer can mark a suggestion `REJECTED`, leaving the request eligible for manual triage. Invalid transitions return a conflict and do not mutate state.
- **FR-007 — Manual operation:** An agent can classify and update a request without AI. The queue remains usable during broker, vector-store, or AI-provider outage.
- **FR-008 — Audit history:** Material changes record actor or system identity, timestamp, prior and new state, source of change, and relevant correlation ID. Audit history is append-only through application behavior.

### AI-assisted triage

- **FR-009 — Suggestion creation:** For an eligible request, the worker asks a configured triage adapter for `category`, `urgency`, and `summary`, plus a brief rationale when supported. The allowed categories and urgency values are versioned in this spec/API.
- **FR-010 — Structured validation:** The application validates provider output against a versioned schema, length limits, enumerations, and request/workspace identity. Invalid output is not displayed as a usable suggestion.
- **FR-011 — Human review:** An agent can accept, edit-and-accept, or reject a suggestion. Only a human review can move an AI-triaged request from `NEEDS_REVIEW` to `OPEN`.
- **FR-012 — Provider failure:** Timeout, rate limit, malformed output, exhausted allowance, or provider error produces a visible retryable/manual state. The original request remains saved and available; it is never auto-approved or silently dropped.
- **FR-013 — Usage limits:** The system checks workspace limits before a provider call and records an auditable usage estimate/status. MVP limits are protective caps, not billing or an exact invoice.
- **FR-014 — Traceability:** Each suggestion stores schema version, prompt version, provider/model identifier when available, timestamps, outcome, and reviewed-by/reviewed-at fields. Do not store hidden chain-of-thought or provider secrets.

### Asynchronous processing

- **FR-015 — Durable dispatch:** Request creation and the event that schedules triage are committed atomically in PostgreSQL through an outbox record.
- **FR-016 — Idempotent consumption:** Repeated delivery of the same event does not create duplicate suggestions or duplicate state changes.
- **FR-017 — Retry and dead letter:** Transient errors use bounded delayed retry; permanent/poison messages are routed to a dead-letter queue with a correlation ID and safe failure classification. An authorized operator can inspect/replay a failed job through a documented operation.
- **FR-018 — Delivery semantics:** The product documents at-least-once delivery and eventual completion. It does not claim exactly-once processing.

### RAG extension (after core triage)

- **FR-019 — Knowledge ingestion:** An administrator can add/remove synthetic text/Markdown knowledge sources for the workspace. Ingestion records source, checksum, parser/index version and status.
- **FR-020 — Workspace-scoped retrieval:** Retrieval filters by authorized workspace before any content is supplied to the model. It returns source identifiers and excerpts for the reviewer to inspect.
- **FR-021 — Grounded suggestion:** Retrieved content is advisory context, not executable instruction. Suggestions can indicate that no supporting source was found; the model must not invent citations.
- **FR-022 — Deletion and reindex:** Removing a source prevents future retrieval and schedules deletion of its chunks/embeddings; reindexing is idempotent and versioned.

## 7. Business rules

- **BR-001:** Request original text is immutable after intake; corrections are separate revision/audit entries.
- **BR-002:** An AI suggestion cannot transition a request to `OPEN` without an authenticated human reviewer in that workspace.
- **BR-003:** No request, member, vector result, file, audit entry or usage record may cross workspace boundaries.
- **BR-004:** The request row and its outbox event are atomic. Broker publication can be repeated safely.
- **BR-005:** A valid suggestion belongs to exactly one request and one workspace and records its prompt/schema versions.
- **BR-006:** A failed AI call must not erase or block manual access to a request.
- **BR-007:** RAG content and public intake text are untrusted input; neither can override system policy or permission checks.
- **BR-008:** All demo seeds and evaluation fixtures are synthetic and clearly labeled.

## 8. Acceptance scenarios

### A. Isolate tenant access

**Given** agent A is authenticated only in workspace A and request B belongs to workspace B
**When** agent A requests, filters, updates, or reviews request B by guessing its identifier
**Then** the API returns a non-disclosing not-found/forbidden response, creates no audit mutation, and records a safe security signal.

### B. Persist intake despite broker outage

**Given** valid intake credentials and PostgreSQL is available while RabbitMQ is unavailable
**When** a requester submits a valid request
**Then** the request and outbox event commit together, the requester receives a reference, and the dispatcher publishes the event after the broker recovers without creating a duplicate request.

### C. Reject repeated delivery

**Given** a triage event has been successfully processed
**When** RabbitMQ redelivers the same event identifier
**Then** no duplicate suggestion is created and the original result remains addressable.

### D. Require a person to approve

**Given** the AI returns a valid suggestion
**When** the worker completes triage
**Then** the request is `NEEDS_REVIEW`; only an authorized agent's explicit accept/edit action moves it to `OPEN`.

### E. Preserve manual workflow

**Given** the AI provider times out or produces invalid JSON
**When** the worker classifies the failure
**Then** the request and original text remain available, the suggestion is not presented as approved, and an agent can classify it manually or retry it safely.

### F. Enforce tenant-scoped RAG

**Given** workspace A and workspace B each have indexed synthetic knowledge
**When** triage for A retrieves context
**Then** every returned chunk belongs to A, even if B contains a closer semantic match; the suggestion contains only citations returned by that authorized query.

### G. Remove a knowledge source

**Given** an administrator removes a workspace knowledge source
**When** deletion/index cleanup completes
**Then** subsequent retrieval cannot return its chunks and the source/deletion state is auditable.

### H. Validate legal state transitions

**Given** a request is `RECEIVED`
**When** a client attempts an unsupported transition directly to `RESOLVED`
**Then** the operation is rejected without changing state or adding a success audit event.

## 9. Non-functional requirements

- **NFR-001 — Tenant safety:** Automated integration tests prove isolation for every request API and RAG query path. Authorization is deny-by-default.
- **NFR-002 — Reliability:** The local development environment can demonstrate outbox recovery, duplicate delivery, bounded retry, DLQ inspection and safe replay.
- **NFR-003 — Responsiveness:** Intake does not wait synchronously for an AI provider. Queue views use pagination and bounded filters. Establish numeric performance targets only after measuring the demo environment.
- **NFR-004 — Operability:** Every intake, outbox event, queue message, provider attempt, suggestion and audit event can be correlated with a non-sensitive correlation ID.
- **NFR-005 — Cost control:** Provider calls are bounded by per-request token/size caps and workspace quotas; sandbox cloud resources have documented estimates, budgets/alerts where available, and teardown steps.
- **NFR-006 — Accessibility:** Essential intake, queue and review workflows are keyboard operable, label form controls, and communicate status/errors without color alone.
- **NFR-007 — Maintainability:** Core domain rules are unit-testable without a running database, broker, web framework or AI SDK.
- **NFR-008 — Data minimization:** The demo has no real-user data. Logs exclude raw request text, credentials, prompts containing sensitive data and full model responses by default.

## 10. Initial API sketch (not a frozen contract)

- `POST /api/v1/intake/{publicToken}/requests` — public, rate-limited submission.
- `GET /api/v1/workspaces/{workspaceId}/requests` — authenticated, paginated, tenant-authorized queue.
- `GET /api/v1/workspaces/{workspaceId}/requests/{requestId}` — request and permitted history.
- `POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/reviews` — accept, edit-and-accept or reject.
- `POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/manual-triage` — manual category/urgency/summary.
- `POST /api/v1/workspaces/{workspaceId}/requests/{requestId}/retry-triage` — authorized, idempotent requeue.

The implementation plan must settle authentication transport, error envelope, optimistic concurrency and OpenAPI schemas before API code is treated as contract.

## 11. Open questions

- Which identity mechanism should the portfolio MVP use (local OIDC provider vs. managed Cognito in cloud)? Choose one for MVP and document the dev/prod-like boundary.
- Which provider adapter and model are available for local development and cost-controlled demo? Keep an offline fake adapter for deterministic tests.
- Does MVP deliver knowledge ingestion/RAG, or is that a second milestone after reliable core triage? Recommended: second milestone.
- Which exact category/urgency taxonomy supports a convincing synthetic demo without implying a real team's process?
- Should requester confirmation be only a reference on screen, or should a later version send an email? No outbound messaging in MVP.

## 12. Definition of acceptance

This specification is ready for implementation once open questions that affect architecture are resolved or explicitly marked as assumptions. A release is accepted only when required acceptance scenarios have automated evidence, the documentation reflects actual behavior, and every excluded capability remains off or unavailable.
