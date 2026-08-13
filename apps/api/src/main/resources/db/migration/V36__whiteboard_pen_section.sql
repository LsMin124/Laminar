-- V36 — 화이트보드 노드 종류 확장(WB-E): 펜 스트로크 · 섹션.
-- 펜 점 좌표(attrs.points 평탄 배열)·색은 attrs jsonb — 스키마 변경 없이 저장.
ALTER TABLE whiteboard_nodes DROP CONSTRAINT chk_whiteboard_nodes_kind;
ALTER TABLE whiteboard_nodes
    ADD CONSTRAINT chk_whiteboard_nodes_kind
    CHECK (kind IN ('md', 'image', 'sticky', 'shape', 'text', 'pen', 'section'));
