package com.laminar.audit.application;

import com.laminar.audit.domain.AuditLogEntity;
import com.laminar.audit.repository.AuditLogRepository;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 — append-only, 90일 보존.
 *
 * <p>Spec §11.9.1: occurred_at 기준 90일 후 cleanup cron이 hard delete. actor_user_id는 현재 컨텍스트 user (시스템
 * 작업이면 null).
 */
@Service
public class AuditLogService {

  static final int RETENTION_DAYS = 90;

  private final AuditLogRepository auditRepo;

  public AuditLogService(AuditLogRepository auditRepo) {
    this.auditRepo = auditRepo;
  }

  /**
   * 감사 이벤트 append. subject 격리는 context.subjectId() 명시 set. SYSTEM scope 호출 시 subjectId 파라미터 명시 필요.
   */
  @Transactional
  public AuditLogEntity append(
      UUID subjectId,
      String action,
      String targetType,
      UUID targetId,
      String summary,
      Map<String, Object> payload) {
    SubjectContext ctx = SubjectContextHolder.require();
    UUID resolvedSubjectId = subjectId != null ? subjectId : ctx.subjectId();
    if (resolvedSubjectId == null) {
      throw new IllegalArgumentException("subjectId required (or PERSONAL/SUBJECT_SHARED scope)");
    }

    AuditLogEntity entry = new AuditLogEntity();
    entry.setSubjectId(resolvedSubjectId);
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
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.subjectId() == null) {
      throw new IllegalStateException("subject scope required for audit list");
    }
    return auditRepo.findBySubjectIdOrderByOccurredAtDesc(
        ctx.subjectId(), PageRequest.of(0, Math.min(limit, 500)));
  }

  @Transactional(readOnly = true)
  public List<AuditLogEntity> listInRange(OffsetDateTime from, OffsetDateTime to) {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.subjectId() == null) {
      throw new IllegalStateException("subject scope required for audit list");
    }
    return auditRepo.findBySubjectIdAndOccurredAtBetweenOrderByOccurredAtDesc(
        ctx.subjectId(), from, to);
  }

  /** Cleanup cron — 90일 이전 hard delete (Spec §11.9.1). */
  @Transactional
  public long purgeOlderThanRetention() {
    OffsetDateTime cutoff = OffsetDateTime.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
    return auditRepo.deleteByOccurredAtBefore(cutoff);
  }
}
