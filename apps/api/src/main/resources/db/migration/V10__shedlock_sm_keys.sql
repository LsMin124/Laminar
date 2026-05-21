-- V10 — ShedLock + Sample Manager API keys (Phase 2 Task 2.9)
-- Implementation.md Task 2.9

-- ───────────────────────────────────────────────────────────
-- 2.9.1 shedlock (ShedLock JDBC 표준 스키마)
-- @Scheduled cron은 단일 인스턴스 가정이지만 향후 다중 인스턴스 대비
-- ───────────────────────────────────────────────────────────
CREATE TABLE shedlock (
    name        VARCHAR(64)     PRIMARY KEY,
    lock_until  TIMESTAMP       NOT NULL,
    locked_at   TIMESTAMP       NOT NULL,
    locked_by   VARCHAR(255)    NOT NULL
);

-- ───────────────────────────────────────────────────────────
-- 2.9.2 sample_manager_api_keys (워크스페이스당 1개, HMAC-SHA256 서명)
-- ───────────────────────────────────────────────────────────
CREATE TABLE sample_manager_api_keys (
    workspace_id    UUID        PRIMARY KEY REFERENCES workspaces(id) ON DELETE CASCADE,
    key_hash        TEXT        NOT NULL,
    created_by      UUID        NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at    TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ
);
