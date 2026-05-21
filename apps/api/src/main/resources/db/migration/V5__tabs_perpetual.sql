-- V5 — 탭(트리+화살표) + 영구노트(타입 시스템 + 버전 마커) (Phase 2 Task 2.4)
-- DataModel.md §3.5 (v4·v5.1) + Implementation.md Task 2.4 기준
-- 7 신규 테이블 + cards.linked_perpetual_id FK 추가 (V3에서 컬럼만 생성됐음)

-- ───────────────────────────────────────────────────────────
-- 2.4.1 tabs (그룹들의 중기 목표 묶음, 트리 + 화살표 둘 다)
-- ───────────────────────────────────────────────────────────
CREATE TABLE tabs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by      UUID        REFERENCES users(id),
    board_id        UUID        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    parent_tab_id   UUID        REFERENCES tabs(id) ON DELETE SET NULL,
    name            TEXT        NOT NULL CHECK (char_length(name) <= 200),
    priority        INTEGER     NOT NULL DEFAULT 0,
    is_visible      BOOLEAN     NOT NULL DEFAULT TRUE,
    is_collapsed    BOOLEAN     NOT NULL DEFAULT FALSE,
    show_label      BOOLEAN     NOT NULL DEFAULT FALSE,
    label_color     TEXT,
    attrs           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_tabs_board_parent
    ON tabs (board_id, parent_tab_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_tabs_board_priority
    ON tabs (board_id, priority)
    WHERE deleted_at IS NULL;

CREATE TRIGGER tabs_updated_at
    BEFORE UPDATE ON tabs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.4.2 tab_members (junction, priority v5)
-- ───────────────────────────────────────────────────────────
CREATE TABLE tab_members (
    tab_id          UUID        NOT NULL REFERENCES tabs(id) ON DELETE CASCADE,
    card_id         UUID        NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    priority        INTEGER     NOT NULL DEFAULT 0,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    added_by        UUID        REFERENCES users(id),
    PRIMARY KEY (tab_id, card_id)
);

CREATE INDEX idx_tab_members_card
    ON tab_members (card_id);

-- ───────────────────────────────────────────────────────────
-- 2.4.3 tab_relations (탭 화살표, DAG 강제는 서비스 레이어)
-- ───────────────────────────────────────────────────────────
CREATE TABLE tab_relations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by      UUID        REFERENCES users(id),
    board_id        UUID        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    from_tab_id     UUID        NOT NULL REFERENCES tabs(id) ON DELETE CASCADE,
    to_tab_id       UUID        NOT NULL REFERENCES tabs(id) ON DELETE CASCADE,
    summary         TEXT,
    body_md         TEXT        CHECK (body_md IS NULL OR char_length(body_md) <= 10000),
    attrs           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_tab_relations_self CHECK (from_tab_id <> to_tab_id)
);

CREATE UNIQUE INDEX uq_tab_relations_active
    ON tab_relations (board_id, from_tab_id, to_tab_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_tab_relations_from
    ON tab_relations (board_id, from_tab_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_tab_relations_to
    ON tab_relations (board_id, to_tab_id)
    WHERE deleted_at IS NULL;

CREATE TRIGGER tab_relations_updated_at
    BEFORE UPDATE ON tab_relations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.4.5 perpetual_notes (영구노트 — 트리 + 버전 이력의 모체)
-- 트리 깊이 ≤10 검증은 service layer
-- ───────────────────────────────────────────────────────────
CREATE TABLE perpetual_notes (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id            UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id                 UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by              UUID        REFERENCES users(id),
    board_id                UUID        REFERENCES boards(id) ON DELETE SET NULL,
    tab_id                  UUID        REFERENCES tabs(id) ON DELETE SET NULL,
    parent_perpetual_id     UUID        REFERENCES perpetual_notes(id) ON DELETE SET NULL,
    title                   TEXT        NOT NULL CHECK (char_length(title) <= 200),
    body_md                 TEXT        CHECK (body_md IS NULL OR char_length(body_md) <= 100000),
    priority                INTEGER     NOT NULL DEFAULT 0,
    attrs                   JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ
);

CREATE INDEX idx_perpetual_notes_tab_parent
    ON perpetual_notes (tab_id, parent_perpetual_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_perpetual_notes_tab_parent_priority
    ON perpetual_notes (tab_id, parent_perpetual_id, priority)
    WHERE deleted_at IS NULL;

CREATE TRIGGER perpetual_notes_updated_at
    BEFORE UPDATE ON perpetual_notes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.4.6 perpetual_column_definitions (v5.1 — 보드 단위 컬럼 정의)
-- 시트형 동적 컬럼: text/dropdown/checkbox 3종 (원본 ColumnDef 패턴)
-- ───────────────────────────────────────────────────────────
CREATE TABLE perpetual_column_definitions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by      UUID        REFERENCES users(id),
    board_id        UUID        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    name            TEXT        NOT NULL CHECK (char_length(name) <= 100),
    type            TEXT        NOT NULL,
    enum_values     JSONB,
    priority        INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_perp_col_def_type CHECK (type IN ('text', 'dropdown', 'checkbox'))
);

CREATE UNIQUE INDEX uq_perpetual_column_definitions_active
    ON perpetual_column_definitions (board_id, user_id, name)
    WHERE deleted_at IS NULL;

CREATE TRIGGER perpetual_column_definitions_updated_at
    BEFORE UPDATE ON perpetual_column_definitions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.4.7 perpetual_columns (영구노트별 값 — v5.1 정정)
-- value TEXT 공통 (checkbox='true'/'false', dropdown=enum 중 하나)
-- ───────────────────────────────────────────────────────────
CREATE TABLE perpetual_columns (
    perpetual_note_id       UUID    NOT NULL REFERENCES perpetual_notes(id) ON DELETE CASCADE,
    column_definition_id    UUID    NOT NULL REFERENCES perpetual_column_definitions(id) ON DELETE CASCADE,
    value                   TEXT,
    PRIMARY KEY (perpetual_note_id, column_definition_id)
);

-- ───────────────────────────────────────────────────────────
-- 2.4.8 perpetual_versions (영구노트 버전 마커 — git commit과 같은 의미)
-- v5.1: is_current_diff로 "최신 1건만 live diff" 규칙 강제
-- ───────────────────────────────────────────────────────────
CREATE TABLE perpetual_versions (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id             UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by          UUID        REFERENCES users(id),
    perpetual_note_id   UUID        NOT NULL REFERENCES perpetual_notes(id) ON DELETE CASCADE,
    card_id             UUID        REFERENCES cards(id) ON DELETE SET NULL,
    version_number      INT         NOT NULL CHECK (version_number >= 1),
    summary             TEXT        CHECK (summary IS NULL OR char_length(summary) <= 500),
    body_diff_md        TEXT        CHECK (body_diff_md IS NULL OR char_length(body_diff_md) <= 100000),
    is_current_diff     BOOLEAN     NOT NULL DEFAULT FALSE,
    committed_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_perpetual_versions_note_version
    ON perpetual_versions (perpetual_note_id, version_number);

CREATE UNIQUE INDEX uq_perpetual_versions_card
    ON perpetual_versions (card_id)
    WHERE card_id IS NOT NULL;

-- v5.1: 영구노트당 live diff 정확히 1건
CREATE UNIQUE INDEX uq_perpetual_versions_current_diff
    ON perpetual_versions (perpetual_note_id)
    WHERE is_current_diff = TRUE;

CREATE INDEX idx_perpetual_versions_note_committed
    ON perpetual_versions (perpetual_note_id, committed_at DESC);

CREATE TRIGGER perpetual_versions_updated_at
    BEFORE UPDATE ON perpetual_versions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.4.10 V3 cards.linked_perpetual_id에 FK 추가 (perpetual_notes 생성 완료 후)
-- ───────────────────────────────────────────────────────────
ALTER TABLE cards
    ADD CONSTRAINT fk_cards_linked_perpetual
    FOREIGN KEY (linked_perpetual_id)
    REFERENCES perpetual_notes(id)
    ON DELETE SET NULL;
