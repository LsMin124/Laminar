package com.laminar.audit;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 감사 로그 — append-only, 90일 보존.
 *
 * Spec §11.9.1: occurred_at 기준 90일 후 cleanup cron이 hard delete.
 * actor_user_id는 현재 컨텍스트 user (시스템 작업이면 null).
 */
@Service
public class AuditLogService {

    static final int RETENTION_DAYS = 90;

    private final AuditLogRepository auditRepo;

    public AuditLogService(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    /**
     * 감사 이벤트 append. workspace 격리는 context.workspaceId() 명시 set.
     * SYSTEM scope 호출 시 workspaceId 파라미터 명시 필요.
     */
    @Transactional
    public AuditLogEntity append(
            UUID workspaceId,
            String action,
            String targetType,
            UUID targetId,
            String summary,
            Map<String, Object> payload) {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        UUID resolvedWorkspaceId = workspaceId != null ? workspaceId : ctx.workspaceId();
        if (resolvedWorkspaceId == null) {
            throw new IllegalArgumentException("workspaceId required (or PERSONAL/WORKSPACE_SHARED scope)");
        }

        AuditLogEntity entry = new AuditLogEntity();
        entry.setWorkspaceId(resolvedWorkspaceId);
        entry.setActorUserId(ctx.userId());
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setSummary(summary);
        entry.setPayload(payload == null ? new HashMap<>() : payload);
        return auditRepo.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntity> listRecent(int limit) {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.workspaceId() == null) {
            throw new IllegalStateException("workspace scope required for audit list");
        }
        return auditRepo.findByWorkspaceIdOrderByOccurredAtDesc(
                ctx.workspaceId(),
                PageRequest.of(0, Math.min(limit, 500)));
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntity> listInRange(OffsetDateTime from, OffsetDateTime to) {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.workspaceId() == null) {
            throw new IllegalStateException("workspace scope required for audit list");
        }
        return auditRepo.findByWorkspaceIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                ctx.workspaceId(), from, to);
    }

    /**
     * Cleanup cron — 90일 이전 hard delete (Spec §11.9.1).
     */
    @Transactional
    public long purgeOlderThanRetention() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        return auditRepo.deleteByOccurredAtBefore(cutoff);
    }
}
