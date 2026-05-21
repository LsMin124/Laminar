-- V2 — 인증 기반 6 테이블 (Phase 2 Task 2.1)
-- DataModel.md §10.1·§3.5 + Implementation.md Task 2.1 기준
-- forward-only 마이그레이션 (down 스크립트 없음, 결함 시 V3+에서 정정)

-- 2.1.1 citext (Phase 0에서 활성됐지만 idempotent하게 재선언)
CREATE EXTENSION IF NOT EXISTS citext;

-- ───────────────────────────────────────────────────────────
-- 공통: updated_at 자동 갱신 트리거 함수 (2.1.8)
-- ───────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ───────────────────────────────────────────────────────────
-- 2.1.2 users (글로벌, Personal-First 아님 — 사용자 자체)
-- ───────────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email           CITEXT      NOT NULL UNIQUE,
    display_name    TEXT,
    password_hash   TEXT,
    avatar_url      TEXT,
    email_verified_at TIMESTAMPTZ,
    prefs           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE TRIGGER users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.1.3 sessions (Auth.js DB session adapter 호환)
-- DB 세션 이유: 멤버 제거 시 즉시 revoke (§5.4)
-- ───────────────────────────────────────────────────────────
CREATE TABLE sessions (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_token   TEXT        NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sessions_user_id ON sessions (user_id);
CREATE INDEX idx_sessions_expires_at ON sessions (expires_at);

CREATE TRIGGER sessions_updated_at
    BEFORE UPDATE ON sessions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.1.4 workspaces (조직 단위)
-- ───────────────────────────────────────────────────────────
CREATE TABLE workspaces (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                TEXT        NOT NULL,
    slug                TEXT        NOT NULL UNIQUE,
    owner_user_id       UUID        NOT NULL REFERENCES users(id),
    default_timezone    TEXT        NOT NULL DEFAULT 'Asia/Seoul',
    settings            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE TRIGGER workspaces_updated_at
    BEFORE UPDATE ON workspaces
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.1.5 workspace_members (워크스페이스 ↔ 사용자)
-- equipment_admin은 별도 테이블(2.10.3), 여기는 owner/member/viewer 3종
-- ───────────────────────────────────────────────────────────
CREATE TABLE workspace_members (
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            TEXT        NOT NULL CHECK (role IN ('owner', 'member', 'viewer')),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    removed_at      TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (workspace_id, user_id)
);

CREATE INDEX idx_workspace_members_active
    ON workspace_members (user_id, removed_at)
    WHERE removed_at IS NULL;

CREATE TRIGGER workspace_members_updated_at
    BEFORE UPDATE ON workspace_members
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.1.6 workspace_invitations (단일 사용 토큰, 7일 TTL)
-- ───────────────────────────────────────────────────────────
CREATE TABLE workspace_invitations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    email           CITEXT      NOT NULL,
    role            TEXT        NOT NULL CHECK (role IN ('owner', 'member', 'viewer')),
    invited_by      UUID        NOT NULL REFERENCES users(id),
    token_hash      TEXT        NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    accepted_at     TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_workspace_invitations_token_hash
    ON workspace_invitations (token_hash);

CREATE TRIGGER workspace_invitations_updated_at
    BEFORE UPDATE ON workspace_invitations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ───────────────────────────────────────────────────────────
-- 2.1.7 boards (Personal-First — 사용자별 보드)
-- display_order TEXT (v3/v4 spec) → V12에서 priority INTEGER 변환 예정
-- ───────────────────────────────────────────────────────────
CREATE TABLE boards (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_by      UUID        REFERENCES users(id),
    name            TEXT        NOT NULL,
    slug            TEXT        NOT NULL,
    default_view    TEXT        NOT NULL DEFAULT 'calendar'
                                CHECK (default_view IN ('calendar', 'list')),
    icon_name       TEXT,
    icon_color      TEXT,
    settings        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    display_order   TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    UNIQUE (workspace_id, user_id, slug)
);

CREATE INDEX idx_boards_workspace_user_order
    ON boards (workspace_id, user_id, display_order)
    WHERE deleted_at IS NULL;

CREATE TRIGGER boards_updated_at
    BEFORE UPDATE ON boards
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
