package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.outbox.application.JobsOutboxService;
import com.laminar.outbox.domain.JobsOutboxEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class JobsOutboxServiceIT extends IsolationIntegrationBase {

  @Autowired JobsOutboxService outboxService;

  @BeforeEach
  void enterSystemScope() {
    WorkspaceContextHolder.set(WorkspaceContext.system());
  }

  @AfterEach
  void cleanup() {
    WorkspaceContextHolder.clear();
  }

  @Test
  @Transactional
  void enqueue_then_claim_returns_job() {
    outboxService.enqueue(
        null, "test.kind.claim", Map.of("k", "v"), OffsetDateTime.now().minusSeconds(1));

    List<JobsOutboxEntity> claimed = outboxService.claimBatch(10);

    assertThat(claimed).extracting(JobsOutboxEntity::getKind).contains("test.kind.claim");
  }

  @Test
  @Transactional
  void future_run_after_is_not_claimed() {
    outboxService.enqueue(null, "test.kind.future", Map.of(), OffsetDateTime.now().plusMinutes(10));

    List<JobsOutboxEntity> claimed = outboxService.claimBatch(10);

    assertThat(claimed).extracting(JobsOutboxEntity::getKind).doesNotContain("test.kind.future");
  }

  @Test
  @Transactional
  void fail_with_retry_resets_run_after() {
    JobsOutboxEntity job =
        outboxService.enqueue(
            null, "test.kind.retry", Map.of(), OffsetDateTime.now().minusSeconds(1));

    outboxService.fail(job.getId(), "transient error", 5);

    JobsOutboxEntity refreshed =
        outboxService.findPendingByKind("test.kind.retry").stream()
            .filter(j -> j.getId().equals(job.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(refreshed.getAttemptCount()).isEqualTo(1);
    assertThat(refreshed.getLastError()).isEqualTo("transient error");
    assertThat(refreshed.getFailedAt()).isNull();
    assertThat(refreshed.getRunAfter()).isAfter(OffsetDateTime.now());
  }
}
