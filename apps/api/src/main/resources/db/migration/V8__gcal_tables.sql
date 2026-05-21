-- V8 — Google Calendar 연동 3 테이블 (Phase 2 Task 2.7)
-- DataModel.md §3.6 + §10 + §11.2 (envelope 암호화 SOR)

-- ───────────────────────────────────────────────────────────
-- 2.7.1 google_oauth_tokens (사용자당 1개, GCal 토큰의 SOR)
-- access/refresh token은 APP_SECRET_V1 envelope AES-GCM 암호화
-- ───────────────────────────────────────────────────────────
CREATE TABLE google_oauth_tokens (
    user_id             UUID        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    access_token_enc    BYTEA,
    refresh_token_enc   BYTEA,
    expires_at          TIMESTAMPTZ,
    scope               TEXT,
    key_version         INT         NOT NULL DEFAULT 1,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER google_oauth_tokens_updated_at
    BEFORE UPDATE ON google_oauth_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.7.2 board_calendar_links (보드 ↔ 사용자별 GCal 매핑, Personal-First)
-- ───────────────────────────────────────────────────────────
CREATE TABLE board_calendar_links (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id             UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by          UUID        REFERENCES users(id),
    board_id            UUID        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    google_calendar_id  TEXT        NOT NULL,
    sync_direction      TEXT        NOT NULL DEFAULT 'two-way',
    sync_token          TEXT,
    last_sync_at        TIMESTAMPTZ,
    last_sync_error     TEXT,
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT chk_bcl_sync_direction CHECK (sync_direction IN ('push', 'pull', 'two-way')),
    UNIQUE (board_id, user_id, google_calendar_id)
);

CREATE INDEX idx_board_calendar_links_active
    ON board_calendar_links (is_active, last_sync_at)
    WHERE is_active = TRUE;

CREATE TRIGGER board_calendar_links_updated_at
    BEFORE UPDATE ON board_calendar_links
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.7.3 card_event_links (카드 ↔ GCal event 1:1 매핑)
-- ───────────────────────────────────────────────────────────
CREATE TABLE card_event_links (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id            UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    card_id                 UUID        NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    board_calendar_link_id  UUID        NOT NULL REFERENCES board_calendar_links(id) ON DELETE CASCADE,
    google_event_id         TEXT        NOT NULL,
    etag                    TEXT,
    last_synced_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_pushed_hash        TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_card_event_links_card
    ON card_event_links (board_calendar_link_id, card_id);

CREATE UNIQUE INDEX uq_card_event_links_event
    ON card_event_links (board_calendar_link_id, google_event_id);

CREATE INDEX idx_card_event_links_last_synced
    ON card_event_links (board_calendar_link_id, last_synced_at);

CREATE TRIGGER card_event_links_updated_at
    BEFORE UPDATE ON card_event_links
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
