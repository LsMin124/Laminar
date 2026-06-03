package com.laminar.cron;

import com.laminar.outbox.application.JobsOutboxService;
import com.laminar.outbox.domain.JobsOutboxEntity;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * jobs_outbox 워커 — 30초마다 batch claim + dispatch.
 *
 * <p>MVP placeholder: kind별 dispatcher는 logger 출력 + complete. 실제 처리 로직은 RRULE expand · GCal sync ·
 * SM 동기 등 각 cron에서 별도 구현 (또는 본 worker에 dispatch table 추가).
 *
 * <p>ShedLock으로 다중 인스턴스 동시 실행 시 1개만 처리. SKIP LOCKED는 JobsOutboxService.claimBatch 내부에서 추가 안전망.
 */
@Component
public class JobsOutboxWorker {

  private static final Logger log = LoggerFactory.getLogger(JobsOutboxWorker.class);
  private static final int BATCH_SIZE = 20;
  private static final int MAX_ATTEMPTS = 5;

  private final JobsOutboxService outboxService;

  public JobsOutboxWorker(JobsOutboxService outboxService) {
    this.outboxService = outboxService;
  }

  @Scheduled(fixedDelay = 30_000L)
  @SchedulerLock(name = "jobsOutboxWorker", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
  public void runBatch() {
    List<JobsOutboxEntity> jobs = outboxService.claimBatch(BATCH_SIZE);
    if (jobs.isEmpty()) return;
    log.info("jobs_outbox: claimed {} jobs", jobs.size());

    for (JobsOutboxEntity job : jobs) {
      try {
        dispatch(job);
        outboxService.complete(job.getId());
      } catch (Exception e) {
        log.warn("jobs_outbox: job {} ({}) failed: {}", job.getId(), job.getKind(), e.getMessage());
        outboxService.fail(job.getId(), e.getMessage(), MAX_ATTEMPTS);
      }
    }
  }

  /** 실제 dispatch는 kind 별 handler를 등록한 후 라우팅. MVP는 logger 출력만. */
  private void dispatch(JobsOutboxEntity job) {
    log.debug("jobs_outbox dispatch: kind={}, payload={}", job.getKind(), job.getPayload());
    // TODO Phase 12+: kind switch — rrule.expand / gcal.sync.push / gcal.sync.pull / sm.import /
    // ...
  }
}
