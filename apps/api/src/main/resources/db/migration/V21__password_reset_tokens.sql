-- 비밀번호 재설정 토큰.
-- raw 토큰은 메일/링크로만 전달하고 DB엔 SHA-256 해시만 저장한다(세션·초대 토큰과 동일 정책).
-- 만료(기본 1시간)·1회용(used_at). 사용자 삭제 시 CASCADE.
CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);
