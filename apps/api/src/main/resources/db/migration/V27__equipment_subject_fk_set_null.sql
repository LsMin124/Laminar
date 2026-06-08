-- V27 — 장비 시리즈의 subject_id FK를 ON DELETE CASCADE → SET NULL 로 전환(footgun 제거).
-- 장비는 owner(사용자) 스코프로 통합됐으므로(V26), 생성 당시 주제가 삭제돼도 장비·예약·로그·공지는
-- 보존돼야 한다. subject_id는 nullable로 풀고, 주제 삭제 시 NULL로 끊는다(created_by 등 소유 컬럼은 유지).
--
-- FK 제약은 V11 인라인 자동명명(+V20 컬럼 리네임)으로 이름이 일정치 않아, 컬럼 기준으로 동적 조회해 교체.
-- 엔티티 매핑(subject_id nullable=false)은 Hibernate ddl-validate가 nullability를 검사하지 않으므로 무변경.

DO $$
DECLARE
  t text;
  cname text;
  tables text[] := ARRAY[
    'equipment',
    'equipment_reservations',
    'equipment_logs',
    'equipment_log_columns',
    'shared_calendars',
    'shared_calendar_announcements'
  ];
BEGIN
  FOREACH t IN ARRAY tables LOOP
    EXECUTE format('ALTER TABLE %I ALTER COLUMN subject_id DROP NOT NULL', t);

    SELECT con.conname INTO cname
      FROM pg_constraint con
      JOIN pg_class rel ON rel.oid = con.conrelid
     WHERE rel.relname = t
       AND con.contype = 'f'
       AND (SELECT attname FROM pg_attribute
             WHERE attrelid = con.conrelid AND attnum = con.conkey[1]) = 'subject_id'
     LIMIT 1;

    IF cname IS NOT NULL THEN
      EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', t, cname);
    END IF;

    EXECUTE format(
      'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE SET NULL',
      t, t || '_subject_id_fkey');
  END LOOP;
END $$;
