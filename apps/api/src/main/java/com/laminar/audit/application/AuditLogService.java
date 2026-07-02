package com.laminar.audit.application;

import com.laminar.audit.domain.AuditLogEntity;
import com.laminar.audit.repository.AuditLogRepository;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.error.ForbiddenException;
import java.time.Duration;
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
    // app clock으로 기록 — DB default(now())는 트랜잭션 내 고정이라 연속 append의 순서가 뭉개지고,
    // 반환 엔티티의 occurredAt도 null이 된다(엔티티 매핑 주석 참조).
    entry.setOccurredAt(OffsetDateTime.now());
    return auditRepo.save(entry);
  }

  @Transactional(readOnly = true)
  public List<AuditLogEntity> listRecent(int limit) {
    SubjectContext ctx = requireAuditReader();
    return auditRepo.findBySubjectIdOrderByOccurredAtDesc(
        ctx.subjectId(), PageRequest.of(0, Math.min(limit, 500)));
  }

  @Transactional(readOnly = true)
  public List<AuditLogEntity> listInRange(OffsetDateTime from, OffsetDateTime to) {
    SubjectContext ctx = requireAuditReader();
    // Q4: 무한정 범위로 전 기간을 훑는 것을 차단 — 보존기간(90일)과 동일 상한.
    if (Duration.between(from, to).toDays() > RETENTION_DAYS) {
      throw new IllegalArgumentException("감사 로그 조회 기간은 최대 " + RETENTION_DAYS + "일입니다");
    }
    return auditRepo.findBySubjectIdAndOccurredAtBetweenOrderByOccurredAtDesc(
        ctx.subjectId(), from, to);
  }

  /**
   * 감사 로그 조회 가드 (Q4) — subject scope + ADMIN+ 강제. 감사 로그 payload는 관리 행위 사유(예:
   * admin.card.reveal_body의 reason)를 담아 LAB MEMBER에게 노출되면 안 된다. personal 주제는 소유자(OWNER)만 자기 로그를 본다.
   */
  private SubjectContext requireAuditReader() {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.subjectId() == null) {
      throw new IllegalStateException("subject scope required for audit list");
    }
    if (!ctx.isAdmin()) {
      throw new ForbiddenException("감사 로그는 관리자만 조회할 수 있습니다");
    }
    return ctx;
  }

  /** Cleanup cron — 90일 이전 hard delete (Spec §11.9.1). */
  @Transactional
  public long purgeOlderThanRetention() {
    OffsetDateTime cutoff = OffsetDateTime.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
    return auditRepo.deleteByOccurredAtBefore(cutoff);
  }
}
