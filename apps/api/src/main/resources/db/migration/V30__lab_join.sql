-- V30 — LAB 가입 흐름 (L2, docs/Laminar_LAB재설계_2026-06-11.md §2)
-- 초대코드(lab 단위, 회전식) + 가입 신청(관리자 승인 큐). 이메일 초대는 기존 subject_invitations 재사용.

CREATE TABLE lab_invite_codes (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id  UUID        NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    code        TEXT        NOT NULL UNIQUE CHECK (char_length(code) <= 32),
    created_by  UUID        REFERENCES users(id) ON DELETE SET NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lab_invite_codes_subject ON lab_invite_codes(subject_id);

CREATE TRIGGER lab_invite_codes_updated_at
    BEFORE UPDATE ON lab_invite_codes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE lab_join_requests (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id  UUID        NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status      TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    decided_by  UUID        REFERENCES users(id) ON DELETE SET NULL,
    decided_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 같은 lab에 중복 pending 신청 차단 (승인/거절 이력은 다수 허용 — 재신청 가능)
CREATE UNIQUE INDEX uq_lab_join_requests_pending
    ON lab_join_requests(subject_id, user_id) WHERE status = 'pending';
CREATE INDEX idx_lab_join_requests_subject_status ON lab_join_requests(subject_id, status);

CREATE TRIGGER lab_join_requests_updated_at
    BEFORE UPDATE ON lab_join_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
