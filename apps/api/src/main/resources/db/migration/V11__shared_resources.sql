-- V11 — 공용 자원 7테이블 (Phase 2 Task 2.10, v4 신규)
-- DataModel.md §10 공용 자원 + Implementation.md Task 2.10
-- workspace-shared (user_id 없음, 모든 멤버 read·write, admin만 일부 수정)

-- 2.10.16 btree_gist 확장 (equipment_reservations exclusion constraint용)
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ───────────────────────────────────────────────────────────
-- 2.10.1 equipment
-- ───────────────────────────────────────────────────────────
CREATE TABLE equipment (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    created_by          UUID        REFERENCES users(id),
    name                TEXT        NOT NULL CHECK (char_length(name) <= 200),
    description         TEXT        CHECK (description IS NULL OR char_length(description) <= 10000),
    location            TEXT        CHECK (location IS NULL OR char_length(location) <= 200),
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    default_log_columns JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_equipment_name_active
    ON equipment (workspace_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_equipment_workspace_active
    ON equipment (workspace_id, is_active)
    WHERE deleted_at IS NULL;

CREATE TRIGGER equipment_updated_at
    BEFORE UPDATE ON equipment
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.10.3 equipment_admins (장비 담당자 N:N, Q12)
-- ───────────────────────────────────────────────────────────
CREATE TABLE equipment_admins (
    equipment_id    UUID        NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    appointed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    appointed_by    UUID        REFERENCES users(id),
    PRIMARY KEY (equipment_id, user_id)
);

CREATE INDEX idx_equipment_admins_user
    ON equipment_admins (user_id);

-- ───────────────────────────────────────────────────────────
-- 2.10.4 shared_calendars (공용 캘린더 — 장비별 1:1 또는 일반)
-- ───────────────────────────────────────────────────────────
CREATE TABLE shared_calendars (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id            UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    created_by              UUID        REFERENCES users(id),
    equipment_id            UUID        REFERENCES equipment(id) ON DELETE CASCADE,
    name                    TEXT        NOT NULL CHECK (char_length(name) <= 200),
    color                   TEXT,
    default_view            TEXT        CHECK (default_view IS NULL OR default_view IN ('calendar', 'list')),
    is_announcement_only    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_shared_calendars_equipment
    ON shared_calendars (equipment_id)
    WHERE equipment_id IS NOT NULL AND deleted_at IS NULL;

CREATE TRIGGER shared_calendars_updated_at
    BEFORE UPDATE ON shared_calendars
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.10.6 equipment_reservations (예약 + RRULE + 시간 겹침 차단)
-- ───────────────────────────────────────────────────────────
CREATE TABLE equipment_reservations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    equipment_id    UUID        NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    reserved_by     UUID        NOT NULL REFERENCES users(id),
    start_at        TIMESTAMPTZ NOT NULL,
    end_at          TIMESTAMPTZ NOT NULL,
    purpose         TEXT        CHECK (purpose IS NULL OR char_length(purpose) <= 500),
    rrule           TEXT,
    card_id         UUID        REFERENCES cards(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_er_end_gt_start CHECK (end_at > start_at),
    CONSTRAINT chk_er_max_span CHECK (end_at - start_at <= interval '7 days'),
    EXCLUDE USING gist (
        equipment_id WITH =,
        tstzrange(start_at, end_at) WITH &&
    ) WHERE (deleted_at IS NULL AND rrule IS NULL)
);

CREATE INDEX idx_equipment_reservations_calendar
    ON equipment_reservations (equipment_id, start_at)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_equipment_reservations_user
    ON equipment_reservations (reserved_by, start_at);

CREATE TRIGGER equipment_reservations_updated_at
    BEFORE UPDATE ON equipment_reservations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.10.10 equipment_log_columns (장비별 log 시트 컬럼 정의)
-- display_order TEXT (v4) → V12에서 priority INTEGER 변환
-- ───────────────────────────────────────────────────────────
CREATE TABLE equipment_log_columns (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    equipment_id    UUID        NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    column_key      TEXT        NOT NULL CHECK (char_length(column_key) <= 50),
    column_label    TEXT        NOT NULL CHECK (char_length(column_label) <= 100),
    column_type     TEXT        NOT NULL,
    enum_values     JSONB,
    is_required     BOOLEAN     NOT NULL DEFAULT FALSE,
    display_order   TEXT        NOT NULL,
    default_value   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_elc_type CHECK (column_type IN ('text', 'number', 'enum', 'bool', 'datetime'))
);

CREATE UNIQUE INDEX uq_equipment_log_columns_key
    ON equipment_log_columns (equipment_id, column_key)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_equipment_log_columns_order
    ON equipment_log_columns (equipment_id, display_order)
    WHERE deleted_at IS NULL;

CREATE TRIGGER equipment_log_columns_updated_at
    BEFORE UPDATE ON equipment_log_columns
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.10.12 equipment_logs (log row, 컬럼 동적이라 JSONB)
-- ───────────────────────────────────────────────────────────
CREATE TABLE equipment_logs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    equipment_id    UUID        NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    logged_by       UUID        NOT NULL REFERENCES users(id),
    logged_at       TIMESTAMPTZ NOT NULL,
    reservation_id  UUID        REFERENCES equipment_reservations(id) ON DELETE SET NULL,
    values          JSONB       NOT NULL DEFAULT '{}'::jsonb,
    notes           TEXT        CHECK (notes IS NULL OR char_length(notes) <= 10000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_equipment_logs_equipment
    ON equipment_logs (equipment_id, logged_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_equipment_logs_user
    ON equipment_logs (logged_by, logged_at DESC);

CREATE TRIGGER equipment_logs_updated_at
    BEFORE UPDATE ON equipment_logs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.10.14 shared_calendar_announcements (공용 캘린더 공지)
-- ───────────────────────────────────────────────────────────
CREATE TABLE shared_calendar_announcements (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    shared_calendar_id  UUID        NOT NULL REFERENCES shared_calendars(id) ON DELETE CASCADE,
    posted_by           UUID        NOT NULL REFERENCES users(id),
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ,
    title               TEXT        NOT NULL CHECK (char_length(title) <= 200),
    body_md             TEXT        CHECK (body_md IS NULL OR char_length(body_md) <= 10000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE INDEX idx_shared_calendar_announcements_start
    ON shared_calendar_announcements (shared_calendar_id, start_at)
    WHERE deleted_at IS NULL;

CREATE TRIGGER shared_calendar_announcements_updated_at
    BEFORE UPDATE ON shared_calendar_announcements
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
