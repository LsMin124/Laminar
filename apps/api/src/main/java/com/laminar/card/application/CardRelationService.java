package com.laminar.card.application;

import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardRelationEntity;
import com.laminar.card.repository.CardRelationRepository;
import com.laminar.card.repository.CardRepository;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.error.ConflictException;
import com.laminar.error.ErrorCode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 사이 화살표 (관계 시각화).
 *
 * <p>검증: - from_card_id != to_card_id (DB chk_card_relations_self 일치) - 같은 board에 속한 두 카드만 (tab_id
 * 일치 강제 — DB FK는 tab_id 명시지만 application 검증)
 */
@Service
public class CardRelationService {

  private final CardRelationRepository relationRepo;
  private final CardRepository cardRepo;
  private final CardDagService dagService;

  public CardRelationService(
      CardRelationRepository relationRepo, CardRepository cardRepo, CardDagService dagService) {
    this.relationRepo = relationRepo;
    this.cardRepo = cardRepo;
    this.dagService = dagService;
  }

  @Transactional
  public CardRelationEntity create(
      UUID fromCardId,
      UUID toCardId,
      String relationKind,
      String summary,
      String bodyMd,
      Map<String, Object> attrs) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("relations");
    if (Objects.equals(fromCardId, toCardId)) {
      throw new IllegalArgumentException("from_card_id == to_card_id is not allowed");
    }
    CardEntity from =
        cardRepo
            .findById(fromCardId)
            .filter(c -> c.getDeletedAt() == null)
            .filter(c -> ctx.ownsPersonal(c.getSubjectId(), c.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("from card not found"));
    CardEntity to =
        cardRepo
            .findById(toCardId)
            .filter(c -> c.getDeletedAt() == null)
            .filter(c -> ctx.ownsPersonal(c.getSubjectId(), c.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("to card not found"));
    UUID tabId = from.getTabId();
    if (tabId == null || !Objects.equals(tabId, to.getTabId())) {
      throw new IllegalArgumentException("from/to cards must share a tab");
    }
    if (dagService.wouldCreateCycle(tabId, fromCardId, toCardId)) {
      throw new ConflictException("relation would create a cycle", ErrorCode.CARD_CYCLE);
    }

    CardRelationEntity relation = new CardRelationEntity();
    relation.setSubjectId(ctx.subjectId());
    relation.setUserId(ctx.userId());
    relation.setCreatedBy(ctx.userId());
    relation.setTabId(tabId);
    relation.setFromCardId(fromCardId);
    relation.setToCardId(toCardId);
    relation.setRelationKind(
        relationKind == null || relationKind.isBlank() ? "default" : relationKind);
    relation.setSummary(summary);
    relation.setBodyMd(bodyMd);
    relation.setAttrs(attrs == null ? new HashMap<>() : attrs);
    CardRelationEntity saved = relationRepo.save(relation);
    // 시간 강제: 새 엣지 from→to ⟹ to.start ≥ from.start; 위반 시 to(및 후행) 연쇄 이동
    dagService.cascadeForward(tabId, fromCardId);
    return saved;
  }

  @Transactional(readOnly = true)
  public List<CardRelationEntity> listByTab(UUID tabId) {
    SubjectContextHolder.requirePersonal();
    return relationRepo.findByTabIdAndDeletedAtIsNull(tabId);
  }

  /**
   * 엣지 라벨(summary) 수정. summary 자체가 이 화살표가 나타내는 관계를 표현한다(별도 relation_kind 분류 없음). null/빈 값이면 라벨 제거.
   */
  @Transactional
  public CardRelationEntity update(UUID relationId, String summary) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("relations");
    CardRelationEntity relation =
        relationRepo
            .findById(relationId)
            .filter(r -> r.getDeletedAt() == null)
            .filter(r -> ctx.ownsPersonal(r.getSubjectId(), r.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("relation not found"));
    relation.setSummary(summary == null || summary.isBlank() ? null : summary);
    return relationRepo.save(relation);
  }

  @Transactional
  public void softDelete(UUID relationId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("relations");
    relationRepo
        .findById(relationId)
        .filter(r -> r.getDeletedAt() == null)
        .filter(r -> ctx.ownsPersonal(r.getSubjectId(), r.getUserId()))
        .ifPresent(
            r -> {
              r.setDeletedAt(OffsetDateTime.now());
              relationRepo.save(r);
            });
  }
}
