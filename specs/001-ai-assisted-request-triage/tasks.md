# Tasks 001: Delivery backlog

Tasks are proposed and not evidence of implementation. Mark a task complete only after code, tests, documentation and review meet its acceptance conditions. Each task references the product requirements it serves.

## Group 0 — Resolve specification

- [ ] **T001** Confirm categories, urgency scale, MVP auth mechanism and fake/real provider boundary. References: `FR-009`, `FR-013`, open questions.
- [ ] **T002** Agree whether public intake and RabbitMQ are part of the first demonstrable vertical slice. References: `FR-003`, `FR-015`–`FR-018`.
- [ ] **T003** Define response/error contract, optimistic concurrency token and retention/deletion defaults. References: `FR-008`, `NFR-001`, `NFR-008`.
- [ ] **T004** Create initial threat model review and synthetic fixture policy. References: `FR-001`–`FR-003`, `NFR-001`, `NFR-008`.

## Group 1 — Domain and repository foundation

- [ ] **T101** Create Java backend and React/TypeScript frontend skeleton with pinned toolchain and documented local commands. Acceptance: clean clone can run lint/build commands; no secrets are required.
- [ ] **T102** Implement workspace/request domain value objects and request state machine without framework dependencies. References: `FR-004`, `FR-006`, `BR-001`, `BR-006`. Acceptance: transition matrix tests include all valid and invalid transitions.
- [ ] **T103** Add PostgreSQL schema and Flyway migrations for workspace, member, request, audit and suggestion foundations. References: `FR-001`, `FR-004`, `FR-008`. Acceptance: migration from empty database and documented local rollback/reset procedure.
- [ ] **T104** Add CI for format/static checks, unit tests, integration tests, dependency scan and build artifacts. Acceptance: pull request cannot merge when required jobs fail.

## Group 2 — Identity, tenancy and intake

- [ ] **T201** Implement identity-to-membership mapping and role-based use-case authorization. References: `FR-001`, `FR-002`. Acceptance: tests deny missing membership and agent admin actions.
- [ ] **T202** Implement revocable, hashed, workspace-scoped intake token, payload limits and rate-limit seam. References: `FR-003`, `NFR-001`, `NFR-008`. Acceptance: token is absent from logs and guessed/rotated token cannot submit.
- [ ] **T203** Implement request intake transaction, immutable original text, audit entry and API validation. References: `FR-004`, `BR-001`. Acceptance: invalid payload creates no request; accepted payload has opaque receipt.
- [ ] **T204** Implement paginated queue, filters, request detail and history with tenant-aware repository APIs. References: `FR-005`, `FR-008`. Acceptance: cross-workspace list/detail/update tests fail closed.
- [ ] **T205** Implement manual classification and permitted status actions. References: `FR-006`, `FR-007`. Acceptance: invalid transition returns stable conflict response and persists no mutation.

## Group 3 — Durable asynchronous delivery

- [ ] **T301** Add outbox schema, transaction integration and stable event envelope. References: `FR-015`, `BR-004`. Acceptance: request/outbox commit together or neither persists.
- [ ] **T302** Implement bounded outbox dispatcher with durable topology, publisher confirms, backoff and metrics. References: `FR-015`, `NFR-002`, `NFR-004`. Acceptance: broker outage leaves replayable rows; confirmed publish is recorded.
- [ ] **T303** Implement triage consumer with idempotency ledger and post-commit acknowledgement. References: `FR-016`, `BR-004`. Acceptance: same event delivered multiple times results in one durable outcome.
- [ ] **T304** Configure bounded retry, poison-message classification, DLQ inspection and explicit safe replay. References: `FR-017`, `FR-018`. Acceptance: tests cover transient/permanent errors and prove no infinite retry.
- [ ] **T305** Add resilience walkthrough for broker outage, publisher crash, duplicate delivery, worker crash and DLQ recovery. References: `NFR-002`. Acceptance: README runbook records commands, expected state and evidence.

## Group 4 — AI suggestion and human review

- [ ] **T401** Define provider-neutral `TriageProvider` port, fake deterministic adapter, timeout and safe failure taxonomy. References: `FR-009`, `FR-012`.
- [ ] **T402** Implement versioned suggestion schema, strict validation, prompt/model metadata and usage caps. References: `FR-010`, `FR-013`, `FR-014`, `NFR-005`.
- [ ] **T403** Implement accept, edit-and-accept and reject review operations with reviewer audit. References: `FR-011`, `BR-002`. Acceptance: worker cannot approve; cross-tenant reviewer cannot act.
- [ ] **T404** Implement frontend queue/detail/review/manual flows. References: `FR-005`–`FR-012`, `NFR-006`. Acceptance: keyboard path and visible pending/failure/manual states are verified.
- [ ] **T405** Run synthetic end-to-end path from intake through outbox, broker, fake provider, human review and queue. Acceptance: persisted original, suggestion, decision and correlation ID are inspectable.

## Group 5 — RAG milestone

- [ ] **T501** Create synthetic knowledge/evaluation corpus and a baseline without retrieval. References: `FR-019`–`FR-022`.
- [ ] **T502** Implement workspace knowledge source lifecycle, checksum, versioned parsing/chunking and deletion queue. References: `FR-019`, `FR-022`.
- [ ] **T503** Add embeddings/vector store and mandatory tenant predicate with negative isolation tests. References: `FR-020`, `NFR-001`.
- [ ] **T504** Return source IDs/excerpts and reject unsupported citations; evaluate relevance, citation accuracy and cross-tenant leakage. References: `FR-021`.
- [ ] **T505** Rehearse delete/reindex and document stale-index recovery, provider costs and limits. References: `FR-022`, `NFR-005`, `NFR-008`.

## Group 6 — Temporary AWS sandbox

- [ ] **T601** Confirm current account permissions, region, service availability, estimated cost and teardown. Do not proceed with paid resources until the mentor explicitly confirms the concrete plan.
- [ ] **T602** Create Terraform for isolated network, container runtime, managed database/broker only if cost/quotas are acceptable, secrets, logs/metrics and least-privilege roles.
- [ ] **T603** Deploy synthetic-only build; verify API, worker, migration, broker, logs, metrics and controlled access.
- [ ] **T604** Export/review evidence, destroy resources, confirm no billable services remain and record any residual storage/snapshots.

## Group 7 — Optional MCP extension

- [ ] **T701** Decide whether an MCP server adds a demonstrated client/workflow value after core SaaS acceptance.
- [ ] **T702** If approved, add one read-only, bounded, authenticated workspace-scoped search tool through an application port. References: `NFR-001`, MCP extension in `plan.md`.
- [ ] **T703** Test prompt-injected request content, unauthorized tenant arguments, output minimization, rate limit and audit. Keep MCP off unless all checks pass.

## Portfolio closeout

- [ ] **T801** Publish architecture diagram, ADR index, API docs, local setup, test evidence and known limits.
- [ ] **T802** Add demo screenshots/video using synthetic content and identify each contributor's PRs and decisions accurately.
- [ ] **T803** Prepare a technical walkthrough: domain boundaries, outbox trade-off, delivery semantics, AI failure path, tenant isolation and one deliberately rejected alternative.
