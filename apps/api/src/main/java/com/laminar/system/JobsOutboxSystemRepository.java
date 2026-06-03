package com.laminar.system;

import com.laminar.outbox.domain.JobsOutboxEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * jobs_outbox 시스템 Repository — 워커 polling (SKIP LOCKED).
 *
 * <p>workspace_id nullable + @Filter 미부착 → SYSTEM scope에서만 사용. SKIP LOCKED 힌트로 동시 워커가 같은 job 가져가지
 * 않게.
 */
public interface JobsOutboxSystemRepository
    extends JpaRepository<JobsOutboxEntity, UUID>, SystemRepository {

  /**
   * 실행 가능한 job 배치 claim — SKIP LOCKED + LIMIT. 호출자는 트랜잭션 안에서 lock 잡고 즉시 처리 (completed_at/failed_at
   * set).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints({
    @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2") // SKIP LOCKED
  })
  @Query(
      """
            SELECT j FROM JobsOutboxEntity j
            WHERE j.completedAt IS NULL
              AND j.failedAt IS NULL
              AND j.runAfter <= :now
            ORDER BY j.runAfter ASC
            """)
  List<JobsOutboxEntity> claimBatch(
      @Param("now") OffsetDateTime now, org.springframework.data.domain.Pageable pageable);

  List<JobsOutboxEntity> findByKindAndCompletedAtIsNullAndFailedAtIsNull(String kind);
}
