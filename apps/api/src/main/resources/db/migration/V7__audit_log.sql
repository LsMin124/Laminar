-- V7 — audit_log (Phase 2 Task 2.6)
-- DataModel.md §10 audit_log + §11.9.1
-- workspace-shared. 90일 보존 → cleanup cron이 hard delete

CREATE TABLE audit_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    actor_user_id   UUID        REFERENCES users(id),
    action          TEXT        NOT NULL,
    target_type     TEXT,
    target_id       UUID,
    summary         TEXT,
    payload         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_workspace_occurred
    ON audit_log (workspace_id, occurred_at DESC);

CREATE INDEX idx_audit_log_occurred
    ON audit_log (occurred_at);
