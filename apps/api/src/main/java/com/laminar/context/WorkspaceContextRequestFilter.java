package com.laminar.context;

import com.laminar.workspace.WorkspaceRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 진입 시 SecurityContext + 헤더에서 WorkspaceContext 도출 → ThreadLocal set,
 * 응답 후 clear. Spring Security 필터 체인 뒤에서 동작 (Order 50).
 *
 * 인증 없는 요청 (health/login)은 SYSTEM scope.
 * 인증 후 워크스페이스 미선택 상태는 SYSTEM scope (워크스페이스 진입 전).
 * X-Laminar-Workspace-Id 헤더로 워크스페이스 진입 시 PERSONAL/WORKSPACE_SHARED scope.
 *
 * 실제 user/role 조회는 Spring Security AuthN 통합 시점에 주입 (Phase 4).
 * 이 클래스는 헤더+세션 기반 placeholder.
 */
@Component
@Order(50)
public class WorkspaceContextRequestFilter extends OncePerRequestFilter {

    private static final String HEADER_WORKSPACE_ID = "X-Laminar-Workspace-Id";
    private static final String HEADER_USER_ID = "X-Laminar-User-Id";
    private static final String HEADER_USER_ROLE = "X-Laminar-User-Role";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        WorkspaceContext context = resolve(request);
        WorkspaceContextHolder.set(context);
        try {
            chain.doFilter(request, response);
        } finally {
            WorkspaceContextHolder.clear();
        }
    }

    private WorkspaceContext resolve(HttpServletRequest request) {
        UUID workspaceId = parseUuid(request.getHeader(HEADER_WORKSPACE_ID));
        UUID userId = parseUuid(request.getHeader(HEADER_USER_ID));
        WorkspaceRole role = parseRole(request.getHeader(HEADER_USER_ROLE));

        if (workspaceId == null) {
            return WorkspaceContext.system();
        }
        if (userId == null) {
            return WorkspaceContext.workspace(workspaceId);
        }
        return WorkspaceContext.personal(workspaceId, userId, role);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private WorkspaceRole parseRole(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return WorkspaceRole.fromDbValue(value.trim().toLowerCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
