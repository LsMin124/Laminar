-- V31 — 장비 시리즈 user→lab 재스코프 (L3, docs/Laminar_LAB재설계_2026-06-11.md §3)
-- V26(사용자 통합)·V27(subject FK SET NULL)을 의도적으로 되돌린다: "장비는 항상 LAB 소속"이 새 불변식.
--
-- ① 기존 장비/공용캘린더 보유 사용자마다 lab 주제 자동 생성("연구실", kind=lab) + OWNER 멤버십.
-- ② 장비는 소유자의 lab으로, 자식(예약·로그·로그컬럼·장비연결 캘린더·공지)은 부모를 따라 이관.
-- ③ 이관 불가 고아(소유 정보 없음)는 삭제 — V26 백필 이후 정상 행엔 존재하지 않는 잔재.
-- ④ subject_id NOT NULL 복귀 + FK ON DELETE CASCADE(주제 hard-delete 시 장비도 소멸).
-- ⑤ equipment_admins 데드 표면 제거(컨트롤러·FE 소비 0 — lab 역할(ADMIN)이 대체).

-- ① 소유 사용자별 lab 자동 생성 (장비 또는 독립 공용캘린더를 가진 사용자)
CREATE TEMP TABLE _lab_owner_map (user_id UUID PRIMARY KEY, lab_id UUID NOT NULL);

DO $$
DECLARE
  owner RECORD;
  new_lab_id UUID;
BEGIN
  FOR owner IN (
    SELECT DISTINCT created_by AS user_id FROM equipment WHERE created_by IS NOT NULL
    UNION
    SELECT DISTINCT created_by FROM shared_calendars WHERE created_by IS NOT NULL
  ) LOOP
    INSERT INTO subjects (name, slug, owner_user_id, kind, default_timezone, settings)
    VALUES ('연구실', 'lab-' || substr(md5(random()::text || clock_timestamp()::text), 1, 10),
            owner.user_id, 'lab', 'Asia/Seoul', '{}'::jsonb)
    RETURNING id INTO new_lab_id;

    INSERT INTO subject_members (subject_id, user_id, role)
    VALUES (new_lab_id, owner.user_id, 'owner');

    INSERT INTO _lab_owner_map (user_id, lab_id) VALUES (owner.user_id, new_lab_id);
  END LOOP;
END $$;

-- ② 이관: 장비 → 소유자의 lab, 자식 → 부모 기준
UPDATE equipment e SET subject_id = m.lab_id
  FROM _lab_owner_map m WHERE e.created_by = m.user_id;

UPDATE equipment_reservations r SET subject_id = e.subject_id
  FROM equipment e WHERE e.id = r.equipment_id;

UPDATE equipment_logs l SET subject_id = e.subject_id
  FROM equipment e WHERE e.id = l.equipment_id;

UPDATE equipment_log_columns c SET subject_id = e.subject_id
  FROM equipment e WHERE e.id = c.equipment_id;

UPDATE shared_calendars sc SET subject_id = e.subject_id
  FROM equipment e WHERE e.id = sc.equipment_id;

-- 장비 미연결(공지 전용) 캘린더는 생성자의 lab으로
UPDATE shared_calendars sc SET subject_id = m.lab_id
  FROM _lab_owner_map m
 WHERE sc.equipment_id IS NULL AND sc.created_by = m.user_id;

UPDATE shared_calendar_announcements a SET subject_id = sc.subject_id
  FROM shared_calendars sc WHERE sc.id = a.shared_calendar_id;

-- ③ 이관 불가 고아 제거 (자식 → 부모 순서의 역순으로 삭제)
DELETE FROM shared_calendar_announcements WHERE subject_id IS NULL;
DELETE FROM shared_calendars WHERE subject_id IS NULL;
DELETE FROM equipment_log_columns WHERE subject_id IS NULL;
DELETE FROM equipment_logs WHERE subject_id IS NULL;
DELETE FROM equipment_reservations WHERE subject_id IS NULL;
DELETE FROM equipment WHERE subject_id IS NULL;

-- ④ NOT NULL 복귀 + FK CASCADE (V27의 동적 FK 교체 패턴 재사용)
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
    EXECUTE format('ALTER TABLE %I ALTER COLUMN subject_id SET NOT NULL', t);

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
      'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE',
      t, t || '_subject_id_fkey');
  END LOOP;
END $$;

DROP TABLE _lab_owner_map;

-- ⑤ 장비별 관리자 지정 — lab 역할 3등급이 대체하는 데드 표면
DROP TABLE IF EXISTS equipment_admins;
