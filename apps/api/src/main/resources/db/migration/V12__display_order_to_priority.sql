-- V12 — display_order TEXT → priority INTEGER 일괄 변환 (Phase 2 Task 2.10b, v5)
-- DataModel.md §3.5 정렬 컬럼 정책 v5
--
-- 변환 대상 3 테이블: boards (V2) / groups (V4) / equipment_log_columns (V11)
-- ※ tabs / tab_members / perpetual_notes / perpetual_column_definitions는
--   V5에서 priority INTEGER로 직접 생성 → 변환 대상 아님
-- ※ cards는 v3에 display_order 없었음 (v5에서 priority 직접 추가)
--
-- 신규 DB 빈 테이블이라 ROW_NUMBER UPDATE는 no-op이지만 spec 충실 + 향후
-- 기존 데이터 있는 환경 reapply 대비.

-- ───────────────────────────────────────────────────────────
-- boards
-- ───────────────────────────────────────────────────────────
ALTER TABLE boards ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;

WITH numbered AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY workspace_id, user_id
               ORDER BY display_order
           ) * 100 AS new_priority
    FROM boards
    WHERE deleted_at IS NULL
)
UPDATE boards
SET priority = numbered.new_priority
FROM numbered
WHERE boards.id = numbered.id;

DROP INDEX idx_boards_workspace_user_order;
ALTER TABLE boards DROP COLUMN display_order;

CREATE INDEX idx_boards_workspace_user_priority
    ON boards (workspace_id, user_id, priority)
    WHERE deleted_at IS NULL;

-- ───────────────────────────────────────────────────────────
-- groups
-- ───────────────────────────────────────────────────────────
ALTER TABLE groups ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;

WITH numbered AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY board_id
               ORDER BY display_order
           ) * 100 AS new_priority
    FROM groups
    WHERE deleted_at IS NULL
)
UPDATE groups
SET priority = numbered.new_priority
FROM numbered
WHERE groups.id = numbered.id;

DROP INDEX idx_groups_workspace_user_board_order;
ALTER TABLE groups DROP COLUMN display_order;

CREATE INDEX idx_groups_workspace_user_board_priority
    ON groups (workspace_id, user_id, board_id, priority)
    WHERE deleted_at IS NULL;

-- ───────────────────────────────────────────────────────────
-- equipment_log_columns
-- ───────────────────────────────────────────────────────────
ALTER TABLE equipment_log_columns ADD COLUMN priority INTEGER NOT NULL DEFAULT 0;

WITH numbered AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY equipment_id
               ORDER BY display_order
           ) * 100 AS new_priority
    FROM equipment_log_columns
    WHERE deleted_at IS NULL
)
UPDATE equipment_log_columns
SET priority = numbered.new_priority
FROM numbered
WHERE equipment_log_columns.id = numbered.id;

DROP INDEX idx_equipment_log_columns_order;
ALTER TABLE equipment_log_columns DROP COLUMN display_order;

CREATE INDEX idx_equipment_log_columns_priority
    ON equipment_log_columns (equipment_id, priority)
    WHERE deleted_at IS NULL;
