-- V26 — 장비 시리즈를 주제(subject) 격리에서 사용자(소유자) 스코프로 전환하기 위한 보조.
-- 장비/예약/로그/공지는 연구실 자원이므로 주제별이 아니라 사용자 전체에 통합돼야 한다
-- (추후 커뮤니티 기능과 함께 연구실(lab) 단위로 재분리 예정 — 그때 user → lab).
--
-- 대부분 엔티티는 기존 사용자 컬럼(created_by/reserved_by/logged_by/posted_by)을 소유자 필터에 재사용한다.
-- equipment_log_columns만 사용자 컬럼이 없어 created_by를 추가하고 부모 장비의 created_by로 backfill.

ALTER TABLE equipment_log_columns
    ADD COLUMN created_by UUID REFERENCES users(id);

UPDATE equipment_log_columns c
   SET created_by = e.created_by
  FROM equipment e
 WHERE e.id = c.equipment_id;
