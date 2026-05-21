-- V3 — cards (Phase 2 Task 2.2)
-- DataModel.md §3.5 cards (v4 + v5 priority) + Implementation.md Task 2.2 기준
-- Personal-First (user_id NN), importance 7종, RRULE, origin enum, priority INT (v5 직접)

CREATE TABLE cards (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id            UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id                 UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by              UUID        REFERENCES users(id),
    board_id                UUID        REFERENCES boards(id) ON DELETE SET NULL,
    title                   TEXT        NOT NULL CHECK (char_length(title) <= 200),
    slug                    TEXT,
    body_md                 TEXT,       -- 250KB 한도는 service layer (attrs.long_body_override 옵트인 위함)
    start_date              DATE,
    end_date                DATE,
    start_time              TIME,
    is_all_day              BOOLEAN     NOT NULL DEFAULT TRUE,
    time_zone               TEXT,       -- 카드별 override (NULL이면 workspace.default_timezone)
    importance              TEXT        NOT NULL DEFAULT 'normal',
    is_completed            BOOLEAN     NOT NULL DEFAULT FALSE,
    linked_perpetual_id     UUID,       -- FK는 V5(perpetual_notes 생성 후) ALTER로 추가
    rrule                   TEXT,       -- RFC 5545, NULL = 비반복
    origin                  TEXT        NOT NULL DEFAULT 'manual',
    priority                INTEGER     NOT NULL DEFAULT 0,
    attrs                   JSONB       NOT NULL DEFAULT '{}'::jsonb,
    archived_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,

    CONSTRAINT chk_cards_importance CHECK (
        importance IN ('normal', 'cf', 'urgent', 'purchase', 'perpetual-ver', 'article', 'process')
    ),
    CONSTRAINT chk_cards_end_date_order CHECK (
        end_date IS NULL OR start_date IS NULL OR end_date >= start_date
    ),
    CONSTRAINT chk_cards_max_span CHECK (
        end_date IS NULL OR start_date IS NULL OR (end_date - start_date) <= 30
    ),
    CONSTRAINT chk_cards_perpetual_link CHECK (
        (importance = 'perpetual-ver') = (linked_perpetual_id IS NOT NULL)
    ),
    CONSTRAINT chk_cards_rrule_time CHECK (
        rrule IS NULL OR is_all_day OR start_time IS NOT NULL
    ),
    CONSTRAINT chk_cards_rrule_origin CHECK (
        rrule IS NULL OR origin IN ('manual', 'rrule_expansion')
    ),
    CONSTRAINT chk_cards_origin CHECK (
        origin IN ('manual', 'rrule_expansion', 'gcal_pull', 'equipment_reservation')
    )
);

-- Personal-First 캘린더 뷰 핵심
CREATE INDEX idx_cards_workspace_user_board_date
    ON cards (workspace_id, user_id, board_id, start_date)
    WHERE deleted_at IS NULL;

-- 영구노트 → 결합 카드 역조회 (V5에서 perpetual_notes FK ADD)
CREATE INDEX idx_cards_linked_perpetual
    ON cards (linked_perpetual_id)
    WHERE deleted_at IS NULL AND linked_perpetual_id IS NOT NULL;

-- RRULE 마스터 카드 조회 (cron rrule-expand)
CREATE INDEX idx_cards_rrule
    ON cards (rrule, start_date)
    WHERE deleted_at IS NULL AND rrule IS NOT NULL;

-- origin별 통계·디버깅
CREATE INDEX idx_cards_origin
    ON cards (origin);

-- layout engine 정렬 + DnD reorder batch UPDATE 효율 (v5)
CREATE INDEX idx_cards_workspace_user_board_priority
    ON cards (workspace_id, user_id, board_id, priority)
    WHERE deleted_at IS NULL;

CREATE TRIGGER cards_updated_at
    BEFORE UPDATE ON cards
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
