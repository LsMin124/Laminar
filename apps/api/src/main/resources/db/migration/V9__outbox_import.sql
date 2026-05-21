-- V9 — outbox 패턴 + import_jobs (Phase 2 Task 2.8)
-- DataModel.md §3.7 + Implementation.md Task 2.8

-- ───────────────────────────────────────────────────────────
-- 2.8.1 jobs_outbox (워커가 SKIP LOCKED로 polling)
-- ───────────────────────────────────────────────────────────
CREATE TABLE jobs_outbox (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        REFERENCES workspaces(id) ON DELETE CASCADE,
    kind            TEXT        NOT NULL,
    payload         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    run_after       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    attempt_count   INT         NOT NULL DEFAULT 0,
    last_error      TEXT,
    completed_at    TIMESTAMPTZ,
    failed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jobs_outbox_pending
    ON jobs_outbox (run_after)
    WHERE completed_at IS NULL AND failed_at IS NULL;

CREATE INDEX idx_jobs_outbox_kind_payload
    ON jobs_outbox (kind, payload)
    WHERE completed_at IS NULL;

-- ───────────────────────────────────────────────────────────
-- 2.8.2 email_outbox (트랜잭션 안 INSERT, flush cron이 외부 발송)
-- §12.3.1 v3 정책: 도메인 미검증 시 last_error='no_domain_verified' 마킹
-- ───────────────────────────────────────────────────────────
CREATE TABLE email_outbox (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    to_email        CITEXT      NOT NULL,
    subject         TEXT        NOT NULL,
    body_html       TEXT,
    body_text       TEXT,
    template_key    TEXT,
    attempt_count   INT         NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_email_outbox_pending
    ON email_outbox (sent_at)
    WHERE sent_at IS NULL;

-- ───────────────────────────────────────────────────────────
-- 2.8.3 import_jobs (옵시디언 vault 임포트 진행 상태)
-- ───────────────────────────────────────────────────────────
CREATE TABLE import_jobs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by      UUID        REFERENCES users(id),
    status          TEXT        NOT NULL DEFAULT 'pending',
    progress        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    last_error      TEXT,
    import_token    TEXT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_import_jobs_status CHECK (
        status IN ('pending', 'running', 'completed', 'failed', 'cancelled')
    )
);

CREATE INDEX idx_import_jobs_workspace_status
    ON import_jobs (workspace_id, status)
    WHERE deleted_at IS NULL;

CREATE TRIGGER import_jobs_updated_at
    BEFORE UPDATE ON import_jobs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
