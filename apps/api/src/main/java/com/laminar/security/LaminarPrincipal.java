package com.laminar.security;

import java.util.UUID;

/**
 * SecurityContext에 저장되는 인증된 사용자 정보 (불변).
 *
 * UserId + email + displayName만 보유. password_hash 등 민감 정보는 미포함 — 직렬화 위험 차단.
 * WorkspaceContext 결합 시 userId를 키로 WorkspaceMemberRepository에서 role 도출.
 */
public record LaminarPrincipal(
        UUID userId,
        String email,
        String displayName
) {
    public LaminarPrincipal {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email required");
        }
    }
}
