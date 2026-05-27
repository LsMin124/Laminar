package com.laminar.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 초대 토큰 조회 — accept 시 token_hash로 찾고, 멤버 관리 화면에서 워크스페이스별 목록.
 */
public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitationEntity, UUID> {

    Optional<WorkspaceInvitationEntity> findByTokenHash(String tokenHash);

    List<WorkspaceInvitationEntity> findByWorkspaceIdAndAcceptedAtIsNullAndRevokedAtIsNull(
            UUID workspaceId);
}
