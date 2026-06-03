package com.laminar.perpetual.application;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.perpetual.domain.PerpetualVersionEntity;
import com.laminar.perpetual.repository.PerpetualNoteRepository;
import com.laminar.perpetual.repository.PerpetualVersionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 영구노트 버전 마커 (git commit 의미).
 *
 * <p>Spec §2.4.8 + v5.1: - version_number per note auto-increment
 * (uq_perpetual_versions_note_version 일치) - is_current_diff 정확히 1건 per note
 * (uq_perpetual_versions_current_diff partial unique) - card_id 1:1 매핑 (uq_perpetual_versions_card
 * partial unique on card_id IS NOT NULL)
 */
@Service
public class PerpetualVersionService {

  private final PerpetualVersionRepository versionRepo;
  private final PerpetualNoteRepository noteRepo;

  public PerpetualVersionService(
      PerpetualVersionRepository versionRepo, PerpetualNoteRepository noteRepo) {
    this.versionRepo = versionRepo;
    this.noteRepo = noteRepo;
  }

  /**
   * 버전 commit — version_number 자동 += 1, isCurrentDiff 옵션. isCurrentDiff=true 요청 시 기존 currentDiff
   * row를 false로 update + 새 row true.
   */
  @Transactional
  public PerpetualVersionEntity commit(
      UUID perpetualNoteId, UUID cardId, String summary, String bodyDiffMd, boolean markCurrent) {
    WorkspaceContext ctx = requirePersonalWritable();
    noteRepo
        .findById(perpetualNoteId)
        .filter(n -> n.getDeletedAt() == null)
        .filter(n -> ctx.ownsPersonal(n.getWorkspaceId(), n.getUserId()))
        .orElseThrow(() -> new IllegalArgumentException("perpetual note not found"));

    int nextVersion =
        versionRepo
            .findFirstByPerpetualNoteIdAndDeletedAtIsNullOrderByVersionNumberDesc(perpetualNoteId)
            .map(v -> v.getVersionNumber() + 1)
            .orElse(1);

    if (markCurrent) {
      versionRepo
          .findByPerpetualNoteIdAndCurrentDiffIsTrueAndDeletedAtIsNull(perpetualNoteId)
          .ifPresent(
              prev -> {
                prev.setCurrentDiff(false);
                versionRepo.save(prev);
              });
    }

    PerpetualVersionEntity version = new PerpetualVersionEntity();
    version.setWorkspaceId(ctx.workspaceId());
    version.setUserId(ctx.userId());
    version.setCreatedBy(ctx.userId());
    version.setPerpetualNoteId(perpetualNoteId);
    version.setCardId(cardId);
    version.setVersionNumber(nextVersion);
    version.setSummary(summary);
    version.setBodyDiffMd(bodyDiffMd);
    version.setCurrentDiff(markCurrent);
    version.setCommittedAt(OffsetDateTime.now());
    return versionRepo.save(version);
  }

  @Transactional(readOnly = true)
  public List<PerpetualVersionEntity> listByNote(UUID perpetualNoteId) {
    WorkspaceContextHolder.requirePersonal();
    return versionRepo.findByPerpetualNoteIdAndDeletedAtIsNullOrderByVersionNumberDesc(
        perpetualNoteId);
  }

  /** 특정 버전을 현재 diff로 마킹. 기존 currentDiff row는 false로. */
  @Transactional
  public PerpetualVersionEntity markCurrentDiff(UUID versionId) {
    WorkspaceContext ctx = requirePersonalWritable();
    PerpetualVersionEntity target =
        versionRepo
            .findById(versionId)
            .filter(v -> v.getDeletedAt() == null)
            .filter(v -> ctx.ownsPersonal(v.getWorkspaceId(), v.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("version not found"));
    versionRepo
        .findByPerpetualNoteIdAndCurrentDiffIsTrueAndDeletedAtIsNull(target.getPerpetualNoteId())
        .filter(prev -> !prev.getId().equals(versionId))
        .ifPresent(
            prev -> {
              prev.setCurrentDiff(false);
              versionRepo.save(prev);
            });
    target.setCurrentDiff(true);
    return versionRepo.save(target);
  }

  private WorkspaceContext requirePersonalWritable() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot commit versions");
    }
    return ctx;
  }
}
