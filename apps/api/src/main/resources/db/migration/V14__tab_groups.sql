-- V14: 탭-그룹 멤버십 (탭 멤버 = 그룹, 구상안 §3.3·§3.4).
-- 기존 tab_members(tab→card 직접)와 별개로, 탭이 그룹을 멤버로 갖는 정합 모델을 추가한다.
-- workspace_id/user_id 컬럼 없음 — 부모(tab/group)의 Personal-First 격리에 의존(group_members 패턴).
CREATE TABLE tab_groups (
    tab_id    UUID        NOT NULL REFERENCES tabs(id) ON DELETE CASCADE,
    group_id  UUID        NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    added_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    added_by  UUID        REFERENCES users(id),
    PRIMARY KEY (tab_id, group_id)
);

CREATE INDEX idx_tab_groups_group ON tab_groups (group_id);
