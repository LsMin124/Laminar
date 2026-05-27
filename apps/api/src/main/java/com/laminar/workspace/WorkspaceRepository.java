package com.laminar.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Workspace Repository (workspace-shared scope — self-filter id = :ctxWorkspaceId).
 *
 * HibernateFilterActivator가 workspaceSharedFilter를 enable하면 자동으로 현재 컨텍스트
 * 워크스페이스만 노출. 워크스페이스 진입 전 (SYSTEM scope)에는 미필터.
 */
public interface WorkspaceRepository extends JpaRepository<WorkspaceEntity, UUID> {

    Optional<WorkspaceEntity> findBySlug(String slug);

    Optional<WorkspaceEntity> findBySlugAndDeletedAtIsNull(String slug);
}
