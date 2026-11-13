-- Material de estudo. Schema inicial das Specs 001 e 003.
-- Toda tabela de negocio carrega workspace_id e participa dos indices: o isolamento por
-- workspace precisa ser verificavel na consulta, nao so no codigo (NFR-002).

CREATE TABLE membership (
  workspace_id VARCHAR(64)  NOT NULL,
  subject      VARCHAR(128) NOT NULL,
  role         VARCHAR(16)  NOT NULL,
  active       BOOLEAN      NOT NULL DEFAULT TRUE,
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  PRIMARY KEY (workspace_id, subject),
  CONSTRAINT membership_role_check CHECK (role IN ('REQUESTER', 'AGENT', 'ADMIN'))
);

CREATE INDEX ix_membership_workspace_role ON membership (workspace_id, role) WHERE active;

CREATE TABLE service_request (
  id                       UUID         PRIMARY KEY,
  workspace_id             VARCHAR(64)  NOT NULL,
  requester_subject        VARCHAR(128) NOT NULL,
  -- Texto original imutavel (FR-006): nenhuma operacao faz UPDATE desta coluna.
  original_text            TEXT         NOT NULL,
  source                   VARCHAR(16)  NOT NULL,
  conversation_id          UUID         NULL,
  status                   VARCHAR(32)  NOT NULL,
  version                  BIGINT       NOT NULL,
  category                 VARCHAR(32)  NULL,
  urgency                  VARCHAR(16)  NULL,
  suggestion_category      VARCHAR(32)  NULL,
  suggestion_urgency       VARCHAR(16)  NULL,
  suggestion_summary       TEXT         NULL,
  suggestion_origin        VARCHAR(32)  NULL,
  suggestion_created_at    TIMESTAMPTZ  NULL,
  pending_question         TEXT         NULL,
  human_routing_reason     VARCHAR(64)  NULL,
  clarification_round_used BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at               TIMESTAMPTZ  NOT NULL,
  CONSTRAINT service_request_source_check CHECK (source IN ('WEB', 'CONVERSATION'))
);

CREATE INDEX ix_request_workspace_status ON service_request (workspace_id, status, created_at, id);
CREATE INDEX ix_request_workspace_requester ON service_request (workspace_id, requester_subject, created_at, id);

-- No maximo um chamado nao terminal por conversa (FR3-016, BR3-007). A invariante fica no
-- banco: duas transacoes concorrentes nao conseguem criar dois chamados para a mesma conversa.
CREATE UNIQUE INDEX ux_request_open_per_conversation
  ON service_request (workspace_id, conversation_id)
  WHERE conversation_id IS NOT NULL AND status <> 'RESOLVED';

CREATE TABLE request_clarification (
  request_id   UUID         NOT NULL REFERENCES service_request (id) ON DELETE CASCADE,
  workspace_id VARCHAR(64)  NOT NULL,
  position     INT          NOT NULL,
  text         TEXT         NOT NULL,
  created_at   TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (request_id, position)
);

CREATE TABLE request_history (
  id           BIGSERIAL    PRIMARY KEY,
  request_id   UUID         NOT NULL REFERENCES service_request (id) ON DELETE CASCADE,
  workspace_id VARCHAR(64)  NOT NULL,
  actor        VARCHAR(128) NOT NULL,
  action       VARCHAR(48)  NOT NULL,
  from_status  VARCHAR(32)  NULL,
  to_status    VARCHAR(32)  NULL,
  version      BIGINT       NOT NULL,
  reason       TEXT         NULL,
  correlation_id VARCHAR(64) NULL,
  occurred_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_request_history_request ON request_history (request_id, id);

CREATE TABLE problem_context (
  request_id         UUID        PRIMARY KEY REFERENCES service_request (id) ON DELETE CASCADE,
  workspace_id       VARCHAR(64) NOT NULL,
  summary            TEXT        NULL,
  suggested_category VARCHAR(32) NULL,
  suggested_urgency  VARCHAR(16) NULL,
  escalation_reason  VARCHAR(64) NULL,
  generated          BOOLEAN     NOT NULL
);

CREATE TABLE problem_context_item (
  request_id UUID        NOT NULL REFERENCES problem_context (request_id) ON DELETE CASCADE,
  kind       VARCHAR(16) NOT NULL,
  position   INT         NOT NULL,
  text       TEXT        NOT NULL,
  PRIMARY KEY (request_id, kind, position),
  CONSTRAINT problem_context_item_kind_check CHECK (kind IN ('SYMPTOM', 'ATTEMPT'))
);

CREATE TABLE conversation (
  id                     UUID         PRIMARY KEY,
  workspace_id           VARCHAR(64)  NOT NULL,
  author_subject         VARCHAR(128) NOT NULL,
  status                 VARCHAR(32)  NOT NULL,
  version                BIGINT       NOT NULL,
  max_assistant_turns    INT          NOT NULL,
  assistant_turn_count   INT          NOT NULL,
  next_sequence          INT          NOT NULL,
  last_author_message_at TIMESTAMPTZ  NOT NULL,
  escalated_request_id   UUID         NULL,
  escalation_reason      VARCHAR(64)  NULL,
  closed_inactive_at     TIMESTAMPTZ  NULL,
  created_at             TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_conversation_author ON conversation (workspace_id, author_subject, created_at, id);
CREATE INDEX ix_conversation_inactivity ON conversation (status, last_author_message_at);

-- Turnos imutaveis e ordenados (BR3-003): sem UPDATE e sem DELETE em producao.
CREATE TABLE conversation_turn (
  conversation_id UUID         NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
  workspace_id    VARCHAR(64)  NOT NULL,
  sequence        INT          NOT NULL,
  author          VARCHAR(16)  NOT NULL,
  text            TEXT         NULL,
  state           VARCHAR(24)  NOT NULL,
  occurred_at     TIMESTAMPTZ  NOT NULL,
  PRIMARY KEY (conversation_id, sequence),
  CONSTRAINT conversation_turn_author_check CHECK (author IN ('REQUESTER', 'ASSISTANT')),
  CONSTRAINT conversation_turn_state_check CHECK (state IN ('GENERATED', 'FAILED', 'OUTCOME_UNKNOWN'))
);

CREATE TABLE judge_verdict (
  id              BIGSERIAL    PRIMARY KEY,
  conversation_id UUID         NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
  workspace_id    VARCHAR(64)  NOT NULL,
  turn_sequence   INT          NOT NULL,
  verdict         VARCHAR(24)  NULL,
  reason_code     VARCHAR(40)  NULL,
  confidence      NUMERIC(4,3) NULL,
  state           VARCHAR(24)  NOT NULL,
  rejection_reason TEXT        NULL,
  occurred_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_judge_verdict_conversation ON judge_verdict (conversation_id, id);

-- Auditoria de leitura da transcricao pelo tecnico (FR3-018).
CREATE TABLE transcript_access (
  id              BIGSERIAL    PRIMARY KEY,
  conversation_id UUID         NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
  workspace_id    VARCHAR(64)  NOT NULL,
  actor           VARCHAR(128) NOT NULL,
  occurred_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_transcript_access_conversation ON transcript_access (conversation_id, id);

-- Chave idempotente guardada como hash, com escopo subject + workspace + operacao (FR-005).
CREATE TABLE idempotency_key (
  scoped_key  VARCHAR(255) PRIMARY KEY,
  body_hash   VARCHAR(64)  NOT NULL,
  resource_id VARCHAR(64)  NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_idempotency_created ON idempotency_key (created_at);

-- Outbox: a intencao de publicar evento e gravada na mesma transacao do negocio (FR-018).
CREATE TABLE outbox_message (
  event_id       UUID         PRIMARY KEY,
  event_type     VARCHAR(64)  NOT NULL,
  schema_version INT          NOT NULL,
  workspace_id   VARCHAR(64)  NOT NULL,
  aggregate_id   VARCHAR(64)  NOT NULL,
  -- Payload tem apenas IDs, sequencia e correlation: nunca texto de solicitacao ou de
  -- conversa, nem prompt (BR-008, BR3-009).
  payload        TEXT         NOT NULL,
  correlation_id VARCHAR(64)  NULL,
  occurred_at    TIMESTAMPTZ  NOT NULL,
  published_at   TIMESTAMPTZ  NULL,
  attempts       INT          NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL,
  lease_until    TIMESTAMPTZ  NULL,
  last_error     TEXT         NULL
);

CREATE INDEX ix_outbox_pending ON outbox_message (next_attempt_at) WHERE published_at IS NULL;

-- Inbox: unicidade por consumidor + evento. Entrega e at-least-once (FR-020, FR-022).
CREATE TABLE inbox_message (
  consumer    VARCHAR(64) NOT NULL,
  event_id    UUID        NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (consumer, event_id)
);
