-- DAG 개편 Phase 1 (1-4~1-7): Workspace→Subject, Board→Tab 리네임 + cards.canvas_y 추가
-- 설계: docs/Laminar_DAG개편_재설계_2026-06-03.md §3
--
-- 테이블/컬럼 RENAME은 데이터·FK·인덱스를 보존한다 (Postgres는 RENAME 시 의존 객체를 자동 추종).
-- 제약/인덱스/트리거 이름에 남는 구 명칭(workspace/board)은 cosmetic이며 ddl-validate와 무관하다.

-- 1) 주제(Subject) 테이블군 리네임 (구 workspace)
ALTER TABLE workspaces RENAME TO subjects;
ALTER TABLE workspace_members RENAME TO subject_members;
ALTER TABLE workspace_invitations RENAME TO subject_invitations;

-- 2) 탭(Tab) 테이블군 리네임 (구 board)
ALTER TABLE boards RENAME TO tabs;
ALTER TABLE board_calendar_links RENAME TO tab_calendar_links;

-- 3) workspace_id → subject_id (보유한 모든 테이블 일괄)
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT table_name
    FROM information_schema.columns
    WHERE table_schema = 'public' AND column_name = 'workspace_id'
  LOOP
    EXECUTE format('ALTER TABLE %I RENAME COLUMN workspace_id TO subject_id', r.table_name);
  END LOOP;
END $$;

-- 4) board_id → tab_id (보유한 모든 테이블 일괄)
DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT table_name
    FROM information_schema.columns
    WHERE table_schema = 'public' AND column_name = 'board_id'
  LOOP
    EXECUTE format('ALTER TABLE %I RENAME COLUMN board_id TO tab_id', r.table_name);
  END LOOP;
END $$;

-- 5) card_event_links.board_calendar_link_id → tab_calendar_link_id (탭↔GCal 링크 FK)
ALTER TABLE card_event_links RENAME COLUMN board_calendar_link_id TO tab_calendar_link_id;

-- 6) cards.canvas_y 추가 — DAG 자유 y좌표 (x는 시간 속성에서 파생되어 미저장, NULL=미배치)
ALTER TABLE cards ADD COLUMN canvas_y DOUBLE PRECISION;
