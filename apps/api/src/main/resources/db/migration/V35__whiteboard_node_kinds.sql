-- V35 — 화이트보드 노드 종류 확장(WB-B): 스티키 · 도형 · 텍스트.
-- 색(color)·도형 종류(shape)는 attrs jsonb에 id 문자열로 저장 — 스키마 변경 없이 팔레트 진화 허용.
ALTER TABLE whiteboard_nodes DROP CONSTRAINT chk_whiteboard_nodes_kind;
ALTER TABLE whiteboard_nodes
    ADD CONSTRAINT chk_whiteboard_nodes_kind
    CHECK (kind IN ('md', 'image', 'sticky', 'shape', 'text'));
