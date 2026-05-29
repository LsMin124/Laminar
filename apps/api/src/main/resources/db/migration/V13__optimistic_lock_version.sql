-- M-8: 낙관적 락 (@Version) — 협업 워크스페이스의 동시 편집/리오더 시 갱신 유실(lost update) 방지.
-- 변경 빈번 엔티티에만 적용. 기존 행은 0으로 시작, Hibernate가 UPDATE 시 자동 증가.
ALTER TABLE boards ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cards ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE perpetual_notes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
