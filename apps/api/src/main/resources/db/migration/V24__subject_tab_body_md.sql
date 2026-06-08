-- V24 — subjects / tabs 본문(body_md) 추가
-- 카드·그룹과 마찬가지로 주제(워크스페이스)와 탭(보드)도 독립 마크다운 문서를 가질 수 있다
-- (주제 전반 개요, 탭별 메모). groups.body_md(V23)와 동일한 길이 제약.

ALTER TABLE subjects
    ADD COLUMN body_md TEXT CHECK (body_md IS NULL OR char_length(body_md) <= 100000);

ALTER TABLE tabs
    ADD COLUMN body_md TEXT CHECK (body_md IS NULL OR char_length(body_md) <= 100000);
