-- V18: whiteboard 폐기 — DAG 개편(docs/Laminar_DAG개편_재설계_2026-06-03.md).
-- 독립 자유노드 화이트보드(V15)를 카드 노드 DAG로 흡수하며 전면 제거.

DROP TABLE IF EXISTS whiteboard_edges CASCADE;
DROP TABLE IF EXISTS whiteboard_nodes CASCADE;
