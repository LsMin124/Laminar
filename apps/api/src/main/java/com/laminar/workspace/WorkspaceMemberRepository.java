package com.laminar.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Workspace 멤버십 Repository (workspace-shared scope).
 *
 * <p>활성 멤버 (removed_at IS NULL) 조회가 hot path — index idx_workspace_members_active.
 */
public interface WorkspaceMemberRepository
    extends JpaRepository<WorkspaceMemberEntity, WorkspaceMemberId> {

  Optional<WorkspaceMemberEntity> findByIdUserIdAndRemovedAtIsNull(UUID userId);

  /** 사용자의 모든 활성 워크스페이스 멤버십 — 워크스페이스 발견(가입 직후 진입)용. */
  List<WorkspaceMemberEntity> findAllByIdUserIdAndRemovedAtIsNull(UUID userId);

  Optional<WorkspaceMemberEntity> findByIdWorkspaceIdAndIdUserIdAndRemovedAtIsNull(
      UUID workspaceId, UUID userId);

  List<WorkspaceMemberEntity> findByIdWorkspaceIdAndRemovedAtIsNull(UUID workspaceId);
}
