-- V1 — Flyway 활성화 검증용 자리 차지 마이그레이션 (Task 1.4.2)
-- Phase 2가 V2부터 실제 스키마(users, workspaces, ...) 시작
-- 본 테이블은 Flyway가 정상 작동하는지 확인하기 위한 단순 placeholder
CREATE TABLE flyway_init_check (
    id INT
);
