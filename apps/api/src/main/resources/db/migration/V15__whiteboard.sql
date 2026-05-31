-- V15: 독립 화이트보드 (그래프 뷰) — 타임라인/캘린더와 무관한 자유 노드·엣지 캔버스.
-- 사용자 재정의(2026-06-01): 그래프는 타임라인 카드/관계와 분리된 별개 화이트보드.
-- Personal-First (workspace_id/user_id NN), board별. hard-delete(스크래치 공간).
CREATE TABLE whiteboard_nodes (
    id            UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID             NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id       UUID             NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by    UUID             REFERENCES users(id),
    board_id      UUID             NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    text          TEXT             NOT NULL DEFAULT '' CHECK (char_length(text) <= 2000),
    x             DOUBLE PRECISION NOT NULL DEFAULT 0,
    y             DOUBLE PRECISION NOT NULL DEFAULT 0,
    width         DOUBLE PRECISION NOT NULL DEFAULT 180,
    height        DOUBLE PRECISION NOT NULL DEFAULT 88,
    color         TEXT,
    attrs         JSONB            NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_whiteboard_nodes_board
    ON whiteboard_nodes (workspace_id, user_id, board_id);

CREATE TRIGGER whiteboard_nodes_updated_at
    BEFORE UPDATE ON whiteboard_nodes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE whiteboard_edges (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by    UUID        REFERENCES users(id),
    board_id      UUID        NOT NULL REFERENCES boards(id) ON DELETE CASCADE,
    from_node_id  UUID        NOT NULL REFERENCES whiteboard_nodes(id) ON DELETE CASCADE,
    to_node_id    UUID        NOT NULL REFERENCES whiteboard_nodes(id) ON DELETE CASCADE,
    label         TEXT        CHECK (label IS NULL OR char_length(label) <= 200),
    attrs         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_whiteboard_edges_distinct CHECK (from_node_id <> to_node_id)
);

CREATE INDEX idx_whiteboard_edges_board
    ON whiteboard_edges (workspace_id, user_id, board_id);

CREATE TRIGGER whiteboard_edges_updated_at
    BEFORE UPDATE ON whiteboard_edges
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
