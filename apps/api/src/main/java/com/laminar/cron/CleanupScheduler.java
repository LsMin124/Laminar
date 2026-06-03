package com.laminar.cron;

import com.laminar.audit.application.AuditLogService;
import com.laminar.user.application.SessionService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 데일리 cleanup — 매일 03:00 KST.
 *
 * <p>- 만료 세션 hard delete (SessionService.purgeExpired) - 90일 이전 audit_log hard delete
 * (AuditLogService.purgeOlderThanRetention)
 *
 * <p>ShedLock으로 다중 인스턴스에서 1회만 실행.
 */
@Component
public class CleanupScheduler {

  private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

  private final SessionService sessionService;
  private final AuditLogService auditLogService;

  public CleanupScheduler(SessionService sessionService, AuditLogService auditLogService) {
    this.sessionService = sessionService;
    this.auditLogService = auditLogService;
  }

  @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
  @SchedulerLock(name = "dailyCleanup", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
  public void runDailyCleanup() {
    long expiredSessions = sessionService.purgeExpired();
    long staleAudit = auditLogService.purgeOlderThanRetention();
    log.info("daily cleanup: sessions={}, audit_log={}", expiredSessions, staleAudit);
  }
}
