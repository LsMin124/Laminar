package com.laminar.context;

import com.laminar.workspace.WorkspaceRole;

import java.util.UUID;

/**
 * 요청 단위 격리 컨텍스트.
 *
 * 3계층:
 *   - SYSTEM: workspaceId·userId 둘 다 null (cron, shedlock, email_outbox, users-self)
 *   - WORKSPACE_SHARED: workspaceId만 (audit_log, equipment 시리즈, jobs_outbox)
 *   - PERSONAL: workspaceId + userId 모두 set (cards/boards/perpetual 등 Personal-First)
 *
 * scope()는 set된 필드 조합으로 즉시 도출.
 */
public record WorkspaceContext(
        UUID workspaceId,
        UUID userId,
        WorkspaceRole userRole
) {

    public enum Scope { SYSTEM, WORKSPACE_SHARED, PERSONAL }

    public static WorkspaceContext system() {
        return new WorkspaceContext(null, null, null);
    }

    public static WorkspaceContext workspace(UUID workspaceId) {
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspace context requires workspaceId");
        }
        return new WorkspaceContext(workspaceId, null, null);
    }

    public static WorkspaceContext personal(UUID workspaceId, UUID userId, WorkspaceRole role) {
        if (workspaceId == null || userId == null) {
            throw new IllegalArgumentException("personal context requires workspaceId and userId");
        }
        return new WorkspaceContext(workspaceId, userId, role);
    }

    public Scope scope() {
        if (workspaceId == null) return Scope.SYSTEM;
        if (userId == null) return Scope.WORKSPACE_SHARED;
        return Scope.PERSONAL;
    }

    public boolean isOwner() {
        return userRole == WorkspaceRole.OWNER;
    }

    public boolean canWrite() {
        return userRole == WorkspaceRole.OWNER || userRole == WorkspaceRole.MEMBER;
    }

    /**
     * Personal-First 엔티티(workspace_id + user_id) 소유권 검증.
     * Hibernate @Filter는 findById(PK 로드)에 적용되지 않으므로 단건 접근 시 명시 호출.
     * 컨텍스트가 PERSONAL이고 두 ID 모두 일치할 때만 true (fail-closed).
     */
    public boolean ownsPersonal(UUID entityWorkspaceId, UUID entityUserId) {
        return workspaceId != null
                && userId != null
                && workspaceId.equals(entityWorkspaceId)
                && userId.equals(entityUserId);
    }

    /**
     * Workspace-Shared 엔티티(workspace_id) 소유권 검증.
     * 컨텍스트 workspace와 엔티티 workspace 일치 시 true (fail-closed).
     */
    public boolean ownsShared(UUID entityWorkspaceId) {
        return workspaceId != null && workspaceId.equals(entityWorkspaceId);
    }
}
