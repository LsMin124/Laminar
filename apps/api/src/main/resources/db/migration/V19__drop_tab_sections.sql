-- V19: 구 Tab(섹션 트리) 폐기 — DAG 개편(docs/Laminar_DAG개편_재설계_2026-06-03.md).
-- Board가 Tab(DAG)으로 승격(1-5에서 boards→tabs 리네임)하면서, 스윔레인 섹션이던
-- 기존 tabs/tab_members/tab_groups/tab_relations 구조를 제거한다.

DROP TABLE IF EXISTS tab_relations CASCADE;
DROP TABLE IF EXISTS tab_groups CASCADE;
DROP TABLE IF EXISTS tab_members CASCADE;
DROP TABLE IF EXISTS tabs CASCADE;
