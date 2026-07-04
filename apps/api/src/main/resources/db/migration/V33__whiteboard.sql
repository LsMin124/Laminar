-- V33 — 화이트보드 캔버스 (자유 배치 노드 + 관계 화살표). 신규 기능.
-- 기존 DAG(카드=시간축 강제)와 별개인 자유 x,y 시각화 공간. 데이터는 전용 테이블
-- (카드 확장 기각 — polymorphic mega-table 금지: 백로그 §1 · DB평가 §108).
-- ⚠ 구 V15 동명 테이블(동질 카드 노드, x=시간)은 V18에서 DROP됨 — 이번은 이질 미디어 노드 +
--   진짜 자유 x,y라 성격이 다르다(계획 확정 근거).
-- 격리: subject_id + user_id (Personal-First, personalFirstFilter) · soft-delete(deleted_at).

-- ───────────────────────────────────────────────────────────
-- 1) tabs.kind — 탭 종류(DAG 캔버스 | 화이트보드). 기존 탭은 전부 'dag'.
-- ───────────────────────────────────────────────────────────
ALTER TABLE tabs
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'dag'
    CONSTRAINT chk_tabs_kind CHECK (kind IN ('dag', 'whiteboard'));

-- ───────────────────────────────────────────────────────────
-- 2) whiteboard_nodes — 자유 배치 노드 (md | image)
-- ───────────────────────────────────────────────────────────
CREATE TABLE whiteboard_nodes (
    id          UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id  UUID             NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    user_id     UUID             NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by  UUID             REFERENCES users(id),
    tab_id      UUID             NOT NULL REFERENCES tabs(id) ON DELETE CASCADE,
    kind        TEXT             NOT NULL,
    x           DOUBLE PRECISION NOT NULL,
    y           DOUBLE PRECISION NOT NULL,
    width       DOUBLE PRECISION,
    height      DOUBLE PRECISION,
    text        TEXT             CHECK (text IS NULL OR char_length(text) <= 500),
    body_md     TEXT             CHECK (body_md IS NULL OR char_length(body_md) <= 100000),
    attrs       JSONB            NOT NULL DEFAULT '{}'::jsonb,
    version     BIGINT           NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT chk_whiteboard_nodes_kind CHECK (kind IN ('md', 'image'))
);

-- 탭 그래프 조회(Personal-First 활성 노드) 핵심
CREATE INDEX idx_whiteboard_nodes_tab
    ON whiteboard_nodes (subject_id, user_id, tab_id)
    WHERE deleted_at IS NULL;

CREATE TRIGGER whiteboard_nodes_updated_at
    BEFORE UPDATE ON whiteboard_nodes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 3) whiteboard_edges — 노드 사이 관계 화살표 (card_relations V4:26-28 미러)
--    DAG의 시간 강제·비순환 없음 — 순수 관계 시각화(사이클 허용).
-- ───────────────────────────────────────────────────────────
CREATE TABLE whiteboard_edges (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id      UUID        NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by      UUID        REFERENCES users(id),
    tab_id          UUID        NOT NULL REFERENCES tabs(id) ON DELETE CASCADE,
    from_node_id    UUID        NOT NULL REFERENCES whiteboard_nodes(id) ON DELETE CASCADE,
    to_node_id      UUID        NOT NULL REFERENCES whiteboard_nodes(id) ON DELETE CASCADE,
    relation_kind   TEXT        NOT NULL DEFAULT 'default',
    label           TEXT        CHECK (label IS NULL OR char_length(label) <= 500),
    attrs           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_whiteboard_edges_self CHECK (from_node_id <> to_node_id)
);

CREATE UNIQUE INDEX uq_whiteboard_edges_active
    ON whiteboard_edges (tab_id, from_node_id, to_node_id, relation_kind, label)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_whiteboard_edges_from
    ON whiteboard_edges (subject_id, user_id, tab_id, from_node_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_whiteboard_edges_to
    ON whiteboard_edges (tab_id, to_node_id)
    WHERE deleted_at IS NULL;

CREATE TRIGGER whiteboard_edges_updated_at
    BEFORE UPDATE ON whiteboard_edges
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
