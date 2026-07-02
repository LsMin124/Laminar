-- V32 — LAB 초대코드 활성 유일성 강제 (전체리뷰 5차 Q2)
-- rotateInviteCode의 read→revoke→insert가 잠금 없이 동시 실행되면 lab당 활성 코드(revoked_at IS NULL)가
-- 2개 이상 공존할 수 있다 — "lab당 활성 1개(회전식)" 불변식 위반, 유출된 구 코드 계열 잔존.
-- 부분 유니크 인덱스를 최종 방어선으로 세운다: 동시 회전의 두 번째 커밋은 23505로 실패하고
-- GlobalExceptionHandler가 409로 변환한다(관리자 재시도). 가입 신청 쪽 uq_lab_join_requests_pending과 대칭.

-- 인덱스 생성 전 기존 중복 활성 코드 정리 — lab별 최신(created_at, id) 하나만 남기고 나머지 revoke.
UPDATE lab_invite_codes t
SET revoked_at = NOW()
WHERE t.revoked_at IS NULL
  AND EXISTS (
    SELECT 1 FROM lab_invite_codes o
    WHERE o.subject_id = t.subject_id
      AND o.revoked_at IS NULL
      AND (o.created_at > t.created_at
           OR (o.created_at = t.created_at AND o.id > t.id))
  );

CREATE UNIQUE INDEX uq_lab_invite_codes_active
    ON lab_invite_codes (subject_id) WHERE revoked_at IS NULL;
