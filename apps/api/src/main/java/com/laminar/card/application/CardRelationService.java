package com.laminar.card.application;

import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardRelationEntity;
import com.laminar.card.repository.CardRelationRepository;
import com.laminar.card.repository.CardRepository;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
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
 * <p>검증: - from_card_id != to_card_id (DB chk_card_relations_self 일치) - 같은 board에 속한 두 카드만
 * (board_id 일치 강제 — DB FK는 board_id 명시지만 application 검증)
 */
@Service
public class CardRelationService {

  private final CardRelationRepository relationRepo;
  private final CardRepository cardRepo;

  public CardRelationService(CardRelationRepository relationRepo, CardRepository cardRepo) {
    this.relationRepo = relationRepo;
    this.cardRepo = cardRepo;
  }

  @Transactional
  public CardRelationEntity create(
      UUID fromCardId,
      UUID toCardId,
      String relationKind,
      String summary,
      String bodyMd,
      Map<String, Object> attrs) {
    WorkspaceContext ctx = requirePersonalWritable();
    if (Objects.equals(fromCardId, toCardId)) {
      throw new IllegalArgumentException("from_card_id == to_card_id is not allowed");
    }
    CardEntity from =
        cardRepo
            .findById(fromCardId)
            .filter(c -> c.getDeletedAt() == null)
            .filter(c -> ctx.ownsPersonal(c.getWorkspaceId(), c.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("from card not found"));
    CardEntity to =
        cardRepo
            .findById(toCardId)
            .filter(c -> c.getDeletedAt() == null)
            .filter(c -> ctx.ownsPersonal(c.getWorkspaceId(), c.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("to card not found"));
    UUID boardId = from.getBoardId();
    if (boardId == null || !Objects.equals(boardId, to.getBoardId())) {
      throw new IllegalArgumentException("from/to cards must share a board");
    }

    CardRelationEntity relation = new CardRelationEntity();
    relation.setWorkspaceId(ctx.workspaceId());
    relation.setUserId(ctx.userId());
    relation.setCreatedBy(ctx.userId());
    relation.setBoardId(boardId);
    relation.setFromCardId(fromCardId);
    relation.setToCardId(toCardId);
    relation.setRelationKind(
        relationKind == null || relationKind.isBlank() ? "default" : relationKind);
    relation.setSummary(summary);
    relation.setBodyMd(bodyMd);
    relation.setAttrs(attrs == null ? new HashMap<>() : attrs);
    return relationRepo.save(relation);
  }

  @Transactional(readOnly = true)
  public List<CardRelationEntity> listByBoard(UUID boardId) {
    WorkspaceContextHolder.requirePersonal();
    return relationRepo.findByBoardIdAndDeletedAtIsNull(boardId);
  }

  @Transactional
  public void softDelete(UUID relationId) {
    WorkspaceContext ctx = requirePersonalWritable();
    relationRepo
        .findById(relationId)
        .filter(r -> r.getDeletedAt() == null)
        .filter(r -> ctx.ownsPersonal(r.getWorkspaceId(), r.getUserId()))
        .ifPresent(
            r -> {
              r.setDeletedAt(OffsetDateTime.now());
              relationRepo.save(r);
            });
  }

  private WorkspaceContext requirePersonalWritable() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate relations");
    }
    return ctx;
  }
}
