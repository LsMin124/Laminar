-- V34 — attachments.parent_type에 'whiteboard_node' 허용 (화이트보드 이미지 노드 첨부, Phase 3).
-- 'perpetual'은 구(폐기된 영구노트) 잔존 허용값 — 제거하면 프로덕션에 잔존 행이 있을 경우 제약
-- 적용이 실패할 위험이 있어 무해한 레거시로 유지한다(자바 enum엔 없음).
ALTER TABLE attachments DROP CONSTRAINT chk_attachments_parent_type;
ALTER TABLE attachments
    ADD CONSTRAINT chk_attachments_parent_type
    CHECK (parent_type IN ('card', 'perpetual', 'whiteboard_node'));
