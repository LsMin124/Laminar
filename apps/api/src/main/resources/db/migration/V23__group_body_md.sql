-- V23 — groups 본문(body_md) 추가
-- 그룹도 카드처럼 간략한 마크다운 문서를 가질 수 있다(서브그래프 클러스터의 목표·메모).
-- card_relations / group_relations 의 body_md(V4)와 동일한 길이 제약.

ALTER TABLE groups
    ADD COLUMN body_md TEXT CHECK (body_md IS NULL OR char_length(body_md) <= 100000);
