package com.laminar.samplemanager.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.samplemanager.domain.SampleManagerLinkEntity;
import com.laminar.samplemanager.repository.SampleManagerLinkRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sample Manager 외부 시스템 ↔ Laminar 카드 멱등 import.
 *
 * <p>Spec §2.5.3: (card_id, sample_id, step_id) unique — 같은 step 재호출 시 update. payload_snapshot은 외부
 * SM 응답 JSON 그대로 저장 (감사용).
 */
@Service
public class SampleManagerLinkService {

  private final SampleManagerLinkRepository linkRepo;

  public SampleManagerLinkService(SampleManagerLinkRepository linkRepo) {
    this.linkRepo = linkRepo;
  }

  /**
   * 멱등 import — (card, sample, step) 키로 존재하면 update, 없으면 insert. payload_snapshot은 새 값으로 덮어쓰기 (최신
   * SM 응답이 진실).
   */
  @Transactional
  public SampleManagerLinkEntity linkOrUpdate(
      UUID cardId,
      String sampleId,
      String stepId,
      String sampleManagerUrl,
      Map<String, Object> payloadSnapshot) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("SM links");
    if (cardId == null || sampleId == null || stepId == null) {
      throw new IllegalArgumentException("cardId/sampleId/stepId required");
    }

    SampleManagerLinkEntity link =
        linkRepo
            .findByCardIdAndSampleIdAndStepIdAndDeletedAtIsNull(cardId, sampleId, stepId)
            .orElseGet(
                () -> {
                  SampleManagerLinkEntity fresh = new SampleManagerLinkEntity();
                  fresh.setSubjectId(ctx.subjectId());
                  fresh.setUserId(ctx.userId());
                  fresh.setCreatedBy(ctx.userId());
                  fresh.setCardId(cardId);
                  fresh.setSampleId(sampleId);
                  fresh.setStepId(stepId);
                  return fresh;
                });
    link.setSampleManagerUrl(sampleManagerUrl);
    link.setPayloadSnapshot(payloadSnapshot);
    return linkRepo.save(link);
  }

  /** SM 동기화 완료 마킹 — synced_at = NOW. */
  @Transactional
  public SampleManagerLinkEntity markSynced(UUID linkId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("SM links");
    SampleManagerLinkEntity link =
        linkRepo
            .findById(linkId)
            .filter(l -> l.getDeletedAt() == null)
            .filter(l -> ctx.ownsPersonal(l.getSubjectId(), l.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("link not found"));
    link.setSyncedAt(OffsetDateTime.now());
    return linkRepo.save(link);
  }

  @Transactional(readOnly = true)
  public List<SampleManagerLinkEntity> listByCard(UUID cardId) {
    SubjectContextHolder.requirePersonal();
    return linkRepo.findByCardIdAndDeletedAtIsNull(cardId);
  }

  @Transactional(readOnly = true)
  public Optional<SampleManagerLinkEntity> findById(UUID linkId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonal();
    return linkRepo
        .findById(linkId)
        .filter(l -> l.getDeletedAt() == null)
        .filter(l -> ctx.ownsPersonal(l.getSubjectId(), l.getUserId()));
  }

  @Transactional
  public void softDelete(UUID linkId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("SM links");
    linkRepo
        .findById(linkId)
        .filter(l -> l.getDeletedAt() == null)
        .filter(l -> ctx.ownsPersonal(l.getSubjectId(), l.getUserId()))
        .ifPresent(
            l -> {
              l.setDeletedAt(OffsetDateTime.now());
              linkRepo.save(l);
            });
  }
}
