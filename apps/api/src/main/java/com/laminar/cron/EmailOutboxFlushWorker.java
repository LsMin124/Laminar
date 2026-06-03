package com.laminar.cron;

import com.laminar.outbox.domain.EmailOutboxEntity;
import com.laminar.system.EmailOutboxSystemRepository;
import java.time.OffsetDateTime;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * email_outbox flush — 1분마다 미발송 메일 처리.
 *
 * <p>MVP placeholder: 실제 SMTP/SES 통합 전까지 logger 출력 + sent_at 마킹. Spec §12.3.1 v3: 도메인 미검증 시
 * last_error='no_domain_verified' 마킹 (후속 구현).
 */
@Component
public class EmailOutboxFlushWorker {

  private static final Logger log = LoggerFactory.getLogger(EmailOutboxFlushWorker.class);
  private static final int MAX_PER_BATCH = 50;

  private final EmailOutboxSystemRepository emailOutboxRepo;

  public EmailOutboxFlushWorker(EmailOutboxSystemRepository emailOutboxRepo) {
    this.emailOutboxRepo = emailOutboxRepo;
  }

  @Scheduled(fixedDelay = 60_000L)
  @SchedulerLock(name = "emailOutboxFlush", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
  @Transactional
  public void flush() {
    List<EmailOutboxEntity> pending = emailOutboxRepo.findBySentAtIsNullOrderByCreatedAtAsc();
    if (pending.isEmpty()) return;
    int processed = 0;
    for (EmailOutboxEntity email : pending) {
      if (processed >= MAX_PER_BATCH) break;
      try {
        send(email);
        email.setSentAt(OffsetDateTime.now());
        emailOutboxRepo.save(email);
        processed++;
      } catch (Exception e) {
        email.setAttemptCount(email.getAttemptCount() + 1);
        email.setLastError(e.getMessage());
        emailOutboxRepo.save(email);
        log.warn(
            "email_outbox: send failed id={} attempt={}: {}",
            email.getId(),
            email.getAttemptCount(),
            e.getMessage());
      }
    }
    log.info("email_outbox flush: sent {} of {} pending", processed, pending.size());
  }

  /** MVP placeholder — SMTP/SES 통합 전. 실제 발송 로직은 향후 외부 lib + 도메인 검증 결합. */
  private void send(EmailOutboxEntity email) {
    log.debug("email_outbox MOCK send to={} subject={}", email.getToEmail(), email.getSubject());
  }
}
