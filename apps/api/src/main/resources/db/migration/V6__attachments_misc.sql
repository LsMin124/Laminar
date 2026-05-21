-- V6 — 첨부 + 날짜 메모 + Sample Manager 링크 (Phase 2 Task 2.5)
-- DataModel.md §10 + Implementation.md Task 2.5 기준 — 모두 Personal-First

-- ───────────────────────────────────────────────────────────
-- 2.5.1 attachments (카드 또는 영구노트 첨부, R2 storage_key)
-- ───────────────────────────────────────────────────────────
CREATE TABLE attachments (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id            UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id                 UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    uploaded_by             UUID        REFERENCES users(id),
    parent_type             TEXT        NOT NULL,
    parent_id               UUID        NOT NULL,
    storage_key             TEXT        NOT NULL,
    original_name           TEXT,
    mime                    TEXT,
    size_bytes              BIGINT      CHECK (size_bytes IS NULL OR size_bytes <= 20971520),
    sha256                  TEXT,
    access_check_required   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT chk_attachments_parent_type CHECK (parent_type IN ('card', 'perpetual'))
);

CREATE INDEX idx_attachments_parent
    ON attachments (parent_type, parent_id)
    WHERE deleted_at IS NULL;

CREATE TRIGGER attachments_updated_at
    BEFORE UPDATE ON attachments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.5.2 date_memos (캘린더 헤더 날짜별 메모, Personal-First)
-- pk (board_id, user_id, date) — 사용자별 날짜당 1개
-- ───────────────────────────────────────────────────────────
CREATE TABLE date_memos (
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    board_id        UUID        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    date            DATE        NOT NULL,
    body_md         TEXT        CHECK (body_md IS NULL OR char_length(body_md) <= 10000),
    attrs           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (board_id, user_id, date)
);

CREATE TRIGGER date_memos_updated_at
    BEFORE UPDATE ON date_memos
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.5.3 sample_manager_links (SM step 결과 ↔ Laminar 카드 멱등 import)
-- ───────────────────────────────────────────────────────────
CREATE TABLE sample_manager_links (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id             UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by          UUID        REFERENCES users(id),
    card_id             UUID        NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    sample_id           TEXT        NOT NULL,
    step_id             TEXT        NOT NULL,
    sample_manager_url  TEXT,
    synced_at           TIMESTAMPTZ,
    payload_snapshot    JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_sample_manager_links_sample_step
    ON sample_manager_links (card_id, sample_id, step_id);

CREATE INDEX idx_sample_manager_links_card
    ON sample_manager_links (card_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_sample_manager_links_sample_step
    ON sample_manager_links (sample_id, step_id);

CREATE TRIGGER sample_manager_links_updated_at
    BEFORE UPDATE ON sample_manager_links
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
