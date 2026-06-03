-- V17: perpetual(영구노트) 폐기 — DAG 개편(docs/Laminar_DAG개편_재설계_2026-06-03.md)에 따라 전면 제거.
-- 기능 자체는 추후 새 모델로 재설계 예정. 현 시점 데이터·스키마는 제거한다.
--
-- cards.linked_perpetual_id 의 의존(FK fk_cards_linked_perpetual · CHECK chk_cards_perpetual_link ·
-- INDEX idx_cards_linked_perpetual)을 CASCADE 로 함께 정리한 뒤 perpetual 4테이블을 drop 한다.
-- CardImportance.PERPETUAL_VER enum 값은 코드에 의미만 남기고 DB CHECK 연동만 제거한다.

ALTER TABLE cards DROP COLUMN IF EXISTS linked_perpetual_id CASCADE;

DROP TABLE IF EXISTS perpetual_versions CASCADE;
DROP TABLE IF EXISTS perpetual_column_definitions CASCADE;
DROP TABLE IF EXISTS perpetual_columns CASCADE;
DROP TABLE IF EXISTS perpetual_notes CASCADE;
