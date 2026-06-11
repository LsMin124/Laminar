-- V29 — LAB 재설계 L1 (docs/Laminar_LAB재설계_2026-06-11.md)
-- ① 역할 3등급(소유자/관리자/일반멤버): viewer 퇴역(기존 행은 member로 흡수), admin 신설.
-- ② subjects.kind: personal(기본) | lab — "특정 주제를 LAB으로 승격" 모델.
--
-- ⚠ role CHECK 제약명은 V2 인라인 정의 + V20 테이블 리네임(workspace_*→subject_*)으로 일정치 않다
-- (제약명은 테이블 리네임을 따라가지 않음) — V27 선례대로 정의 내용('viewer' 포함)으로 동적 조회해 교체.

UPDATE subject_members SET role = 'member' WHERE role = 'viewer';
UPDATE subject_invitations SET role = 'member' WHERE role = 'viewer';

DO $$
DECLARE
  t text;
  cname text;
BEGIN
  FOREACH t IN ARRAY ARRAY['subject_members', 'subject_invitations'] LOOP
    SELECT con.conname INTO cname
      FROM pg_constraint con
      JOIN pg_class rel ON rel.oid = con.conrelid
     WHERE rel.relname = t
       AND con.contype = 'c'
       AND pg_get_constraintdef(con.oid) LIKE '%viewer%'
     LIMIT 1;

    IF cname IS NOT NULL THEN
      EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', t, cname);
    END IF;

    EXECUTE format(
      'ALTER TABLE %I ADD CONSTRAINT %I CHECK (role IN (''owner'', ''admin'', ''member''))',
      t, t || '_role_check');
  END LOOP;
END $$;

ALTER TABLE subjects ADD COLUMN kind TEXT NOT NULL DEFAULT 'personal';
ALTER TABLE subjects ADD CONSTRAINT subjects_kind_check CHECK (kind IN ('personal', 'lab'));
