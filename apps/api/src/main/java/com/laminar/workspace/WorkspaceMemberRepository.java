package com.laminar.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Workspace 멤버십 Repository (workspace-shared scope).
 *
 * 활성 멤버 (removed_at IS NULL) 조회가 hot path — index idx_workspace_members_active.
 */
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, WorkspaceMemberId> {

    Optional<WorkspaceMemberEntity> findByIdUserIdAndRemovedAtIsNull(UUID userId);

    Optional<WorkspaceMemberEntity> findByIdWorkspaceIdAndIdUserIdAndRemovedAtIsNull(
            UUID workspaceId, UUID userId);

    List<WorkspaceMemberEntity> findByIdWorkspaceIdAndRemovedAtIsNull(UUID workspaceId);
}
