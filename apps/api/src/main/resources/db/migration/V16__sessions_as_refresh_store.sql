-- V16: sessions 테이블을 refresh 토큰 저장소로 의미 전환 (JWT 인증 재구성).
-- access는 stateless JWT(무DB 검증)이고, refresh만 이 테이블에 SHA-256(base64url) 해시로 보관한다
-- (rotation·revoke의 SOR). 구조(id/user_id/session_token/expires_at)가 그대로 적합하므로 컬럼 변경 없이
-- COMMENT만 갱신한다.
--
-- 기존 행(구 bespoke 세션 토큰)은 JWT 전환으로 모두 무효 → 정리한다(사용자 재로그인 필요, 1회성).

DELETE FROM sessions;

COMMENT ON TABLE sessions IS
    'Refresh 토큰 저장소(JWT 재구성). access는 stateless JWT라 무DB; refresh만 SHA-256 해시로 보관 — rotation/revoke의 SOR.';
COMMENT ON COLUMN sessions.session_token IS
    'refresh 토큰의 SHA-256(base64url) 해시. raw 토큰은 httpOnly 쿠키(laminar-refresh)에만 존재.';
COMMENT ON COLUMN sessions.expires_at IS
    'refresh 만료(발급+28일). 만료 행은 cleanup cron(CleanupScheduler)이 hard delete.';
