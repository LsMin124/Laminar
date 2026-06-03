package com.laminar.audit.repository;

import com.laminar.audit.domain.AuditLogEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** audit_log Repository — workspace-shared (@Filter 자동). occurred_at DESC가 hot path (최근 활동). */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

  List<AuditLogEntity> findByWorkspaceIdOrderByOccurredAtDesc(UUID workspaceId, Pageable pageable);

  List<AuditLogEntity> findByWorkspaceIdAndOccurredAtBetweenOrderByOccurredAtDesc(
      UUID workspaceId, OffsetDateTime from, OffsetDateTime to);

  long deleteByOccurredAtBefore(OffsetDateTime cutoff);
}
