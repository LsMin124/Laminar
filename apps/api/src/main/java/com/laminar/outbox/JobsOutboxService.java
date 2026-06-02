package com.laminar.outbox;

import com.laminar.system.JobsOutboxSystemRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워커 polling 인프라 — outbox 패턴.
 *
 * <p>Spec §3.7: enqueue는 비즈니스 트랜잭션 안에서, polling은 별도 cron 워커. SKIP LOCKED로 다중 워커 동시 실행 시 같은 job 중복
 * 처리 차단.
 *
 * <p>시스템 컨텍스트만 호출 — workspace 무관 또는 명시 workspaceId.
 */
@Service
public class JobsOutboxService {

  private static final int MAX_BATCH = 50;

  private final JobsOutboxSystemRepository outboxRepo;

  public JobsOutboxService(JobsOutboxSystemRepository outboxRepo) {
    this.outboxRepo = outboxRepo;
  }

  @Transactional
  public JobsOutboxEntity enqueue(
      UUID workspaceId, String kind, Map<String, Object> payload, OffsetDateTime runAfter) {
    if (kind == null || kind.isBlank()) {
      throw new IllegalArgumentException("kind required");
    }
    JobsOutboxEntity job = new JobsOutboxEntity();
    job.setWorkspaceId(workspaceId);
    job.setKind(kind);
    job.setPayload(payload == null ? new HashMap<>() : payload);
    job.setRunAfter(runAfter == null ? OffsetDateTime.now() : runAfter);
    return outboxRepo.save(job);
  }

  /** 실행 가능한 job 배치 claim — 트랜잭션 안에서 lock + 즉시 complete/fail. */
  @Transactional
  public List<JobsOutboxEntity> claimBatch(int batchSize) {
    int limit = Math.min(Math.max(batchSize, 1), MAX_BATCH);
    return outboxRepo.claimBatch(OffsetDateTime.now(), PageRequest.of(0, limit));
  }

  @Transactional
  public void complete(UUID jobId) {
    Optional<JobsOutboxEntity> maybeJob = outboxRepo.findById(jobId);
    maybeJob.ifPresent(
        job -> {
          job.setCompletedAt(OffsetDateTime.now());
          outboxRepo.save(job);
        });
  }

  /** 실패 처리 + 재시도 backoff (attempt^2 분 후 run_after). MAX_ATTEMPTS 초과 시 failed_at 영구 마킹. */
  @Transactional
  public void fail(UUID jobId, String errorMessage, int maxAttempts) {
    Optional<JobsOutboxEntity> maybeJob = outboxRepo.findById(jobId);
    maybeJob.ifPresent(
        job -> {
          int attempt = job.getAttemptCount() + 1;
          job.setAttemptCount(attempt);
          job.setLastError(errorMessage);
          if (attempt >= maxAttempts) {
            job.setFailedAt(OffsetDateTime.now());
          } else {
            long backoffMinutes = (long) attempt * attempt;
            job.setRunAfter(OffsetDateTime.now().plusMinutes(backoffMinutes));
          }
          outboxRepo.save(job);
        });
  }

  @Transactional(readOnly = true)
  public List<JobsOutboxEntity> findPendingByKind(String kind) {
    return outboxRepo.findByKindAndCompletedAtIsNullAndFailedAtIsNull(kind);
  }
}
