-- V4 — card_relations + groups + group_members + group_relations (Phase 2 Task 2.3)
-- DataModel.md §3.5 + Implementation.md Task 2.3 기준
-- Personal-First: card_relations / groups / group_relations 모두 user_id NN

-- ───────────────────────────────────────────────────────────
-- 2.3.1 card_relations (화살표 — 두 카드 사이 관계)
-- ───────────────────────────────────────────────────────────
CREATE TABLE card_relations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by      UUID        REFERENCES users(id),
    board_id        UUID        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    from_card_id    UUID        NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    to_card_id      UUID        NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    relation_kind   TEXT        NOT NULL DEFAULT 'default',
    summary         TEXT,
    body_md         TEXT,
    attrs           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_card_relations_self CHECK (from_card_id <> to_card_id)
);

CREATE UNIQUE INDEX uq_card_relations_active
    ON card_relations (board_id, from_card_id, to_card_id, relation_kind, summary)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_card_relations_from
    ON card_relations (workspace_id, user_id, board_id, from_card_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_card_relations_to
    ON card_relations (board_id, to_card_id)
    WHERE deleted_at IS NULL;

CREATE TRIGGER card_relations_updated_at
    BEFORE UPDATE ON card_relations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.3.3 groups (카드들의 단기 목표 묶음, Personal-First)
-- display_order TEXT (v4) → V12에서 priority INTEGER 변환
-- ───────────────────────────────────────────────────────────
CREATE TABLE groups (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by      UUID        REFERENCES users(id),
    board_id        UUID        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    name            TEXT        NOT NULL CHECK (char_length(name) <= 200),
    color           TEXT,
    display_order   TEXT        NOT NULL,
    attrs           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_groups_workspace_user_board_order
    ON groups (workspace_id, user_id, board_id, display_order)
    WHERE deleted_at IS NULL;

CREATE TRIGGER groups_updated_at
    BEFORE UPDATE ON groups
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.3.4 group_members (junction)
-- 그룹·카드 어느 쪽 soft delete돼도 row 유지 (고아 멤버십은 cron 정리)
-- ───────────────────────────────────────────────────────────
CREATE TABLE group_members (
    group_id        UUID        NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    card_id         UUID        NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    added_by        UUID        REFERENCES users(id),
    PRIMARY KEY (group_id, card_id)
);

CREATE INDEX idx_group_members_card
    ON group_members (card_id);

-- ───────────────────────────────────────────────────────────
-- 2.3.5 group_relations (그룹 화살표, Personal-First)
-- ───────────────────────────────────────────────────────────
CREATE TABLE group_relations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by      UUID        REFERENCES users(id),
    board_id        UUID        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    from_group_id   UUID        NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    to_group_id     UUID        NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    relation_kind   TEXT        NOT NULL DEFAULT 'default',
    summary         TEXT,
    body_md         TEXT        CHECK (body_md IS NULL OR char_length(body_md) <= 100000),
    attrs           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_group_relations_self CHECK (from_group_id <> to_group_id)
);

CREATE UNIQUE INDEX uq_group_relations_active
    ON group_relations (board_id, from_group_id, to_group_id, relation_kind, summary)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_group_relations_from
    ON group_relations (board_id, from_group_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_group_relations_to
    ON group_relations (board_id, to_group_id)
    WHERE deleted_at IS NULL;

CREATE TRIGGER group_relations_updated_at
    BEFORE UPDATE ON group_relations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
