package com.laminar.outbox.application;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.outbox.domain.ImportJobEntity;
import com.laminar.outbox.domain.ImportJobStatus;
import com.laminar.outbox.repository.ImportJobRepository;
import com.laminar.web.error.ConflictException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 옵시디언 vault import 진행 상태.
 *
 * <p>Status 5종: pending → running → completed/failed/cancelled. import_token은 외부 시스템 (CLI 도구) 인증용 —
 * Personal-First 격리.
 */
@Service
public class ImportJobService {

  private static final int TOKEN_BYTES = 24;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ImportJobRepository importRepo;

  public ImportJobService(ImportJobRepository importRepo) {
    this.importRepo = importRepo;
  }

  @Transactional
  public ImportJobEntity createPending() {
    WorkspaceContext ctx = requirePersonalWritable();
    ImportJobEntity job = new ImportJobEntity();
    job.setWorkspaceId(ctx.workspaceId());
    job.setUserId(ctx.userId());
    job.setCreatedBy(ctx.userId());
    job.setStatus(ImportJobStatus.PENDING);
    job.setProgress(new HashMap<>());
    job.setImportToken(generateToken());
    return importRepo.save(job);
  }

  @Transactional
  public ImportJobEntity start(UUID jobId) {
    requirePersonalWritable();
    ImportJobEntity job = requirePending(jobId);
    job.setStatus(ImportJobStatus.RUNNING);
    job.setStartedAt(OffsetDateTime.now());
    return importRepo.save(job);
  }

  @Transactional
  public ImportJobEntity updateProgress(UUID jobId, Map<String, Object> progress) {
    requirePersonalWritable();
    ImportJobEntity job =
        importRepo
            .findById(jobId)
            .filter(j -> j.getDeletedAt() == null)
            .filter(
                j ->
                    WorkspaceContextHolder.require()
                        .ownsPersonal(j.getWorkspaceId(), j.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("import job not found"));
    if (job.getStatus() != ImportJobStatus.RUNNING) {
      throw new ConflictException(
          "progress update requires RUNNING status (got " + job.getStatus() + ")");
    }
    job.setProgress(progress == null ? new HashMap<>() : progress);
    return importRepo.save(job);
  }

  @Transactional
  public ImportJobEntity complete(UUID jobId, Map<String, Object> finalProgress) {
    requirePersonalWritable();
    ImportJobEntity job = requireRunning(jobId);
    if (finalProgress != null) {
      job.setProgress(finalProgress);
    }
    job.setStatus(ImportJobStatus.COMPLETED);
    job.setFinishedAt(OffsetDateTime.now());
    return importRepo.save(job);
  }

  @Transactional
  public ImportJobEntity fail(UUID jobId, String errorMessage) {
    requirePersonalWritable();
    ImportJobEntity job =
        importRepo
            .findById(jobId)
            .filter(j -> j.getDeletedAt() == null)
            .filter(
                j ->
                    WorkspaceContextHolder.require()
                        .ownsPersonal(j.getWorkspaceId(), j.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("import job not found"));
    job.setStatus(ImportJobStatus.FAILED);
    job.setLastError(errorMessage);
    job.setFinishedAt(OffsetDateTime.now());
    return importRepo.save(job);
  }

  @Transactional
  public ImportJobEntity cancel(UUID jobId) {
    requirePersonalWritable();
    ImportJobEntity job =
        importRepo
            .findById(jobId)
            .filter(j -> j.getDeletedAt() == null)
            .filter(
                j ->
                    WorkspaceContextHolder.require()
                        .ownsPersonal(j.getWorkspaceId(), j.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("import job not found"));
    if (job.getStatus() == ImportJobStatus.COMPLETED || job.getStatus() == ImportJobStatus.FAILED) {
      throw new ConflictException("cannot cancel terminal status: " + job.getStatus());
    }
    job.setStatus(ImportJobStatus.CANCELLED);
    job.setFinishedAt(OffsetDateTime.now());
    return importRepo.save(job);
  }

  @Transactional(readOnly = true)
  public List<ImportJobEntity> listRecent() {
    WorkspaceContextHolder.requirePersonal();
    return importRepo.findByDeletedAtIsNullOrderByCreatedAtDesc();
  }

  @Transactional(readOnly = true)
  public Optional<ImportJobEntity> findById(UUID jobId) {
    WorkspaceContext ctx = WorkspaceContextHolder.requirePersonal();
    return importRepo
        .findById(jobId)
        .filter(j -> j.getDeletedAt() == null)
        .filter(j -> ctx.ownsPersonal(j.getWorkspaceId(), j.getUserId()));
  }

  private ImportJobEntity requirePending(UUID jobId) {
    ImportJobEntity job =
        importRepo
            .findById(jobId)
            .filter(j -> j.getDeletedAt() == null)
            .filter(
                j ->
                    WorkspaceContextHolder.require()
                        .ownsPersonal(j.getWorkspaceId(), j.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("import job not found"));
    if (job.getStatus() != ImportJobStatus.PENDING) {
      throw new ConflictException("start requires PENDING status (got " + job.getStatus() + ")");
    }
    return job;
  }

  private ImportJobEntity requireRunning(UUID jobId) {
    ImportJobEntity job =
        importRepo
            .findById(jobId)
            .filter(j -> j.getDeletedAt() == null)
            .filter(
                j ->
                    WorkspaceContextHolder.require()
                        .ownsPersonal(j.getWorkspaceId(), j.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("import job not found"));
    if (job.getStatus() != ImportJobStatus.RUNNING) {
      throw new ConflictException("complete requires RUNNING status (got " + job.getStatus() + ")");
    }
    return job;
  }

  private WorkspaceContext requirePersonalWritable() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate import jobs");
    }
    return ctx;
  }

  private static String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
