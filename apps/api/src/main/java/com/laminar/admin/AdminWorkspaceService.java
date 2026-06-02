package com.laminar.admin;

import com.laminar.audit.AuditLogService;
import com.laminar.board.BoardEntity;
import com.laminar.board.BoardRepository;
import com.laminar.card.CardEntity;
import com.laminar.card.CardRepository;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 콘솔 강한 격리 (Spec §3 + DoD §10).
 *
 * <p>정책: - workspace OWNER만 호출 가능 - 모든 호출은 audit_log 자동 기록 (action=admin.*, actor + target) -
 * cross-user 메타 조회 허용, body_md 등 본문은 escape hatch 호출 + reason 명시 필요
 *
 * <p>구현: SYSTEM scope로 잠시 진입하여 Personal-First 필터 회피, workspace_id 일치 검증은 application 단에서 명시. 호출 끝에
 * OWNER context 복원 + audit append.
 */
@Service
public class AdminWorkspaceService {

  private final BoardRepository boardRepo;
  private final CardRepository cardRepo;
  private final AuditLogService auditService;
  private final HibernateFilterActivator filterActivator;

  public AdminWorkspaceService(
      BoardRepository boardRepo,
      CardRepository cardRepo,
      AuditLogService auditService,
      HibernateFilterActivator filterActivator) {
    this.boardRepo = boardRepo;
    this.cardRepo = cardRepo;
    this.auditService = auditService;
    this.filterActivator = filterActivator;
  }

  @Transactional(readOnly = true)
  public List<BoardEntity> listAllBoards() {
    WorkspaceContext owner = requireWorkspaceOwner();
    WorkspaceContextHolder.set(WorkspaceContext.system());
    filterActivator.activate();
    try {
      return boardRepo.findAll().stream()
          .filter(b -> b.getDeletedAt() == null)
          .filter(b -> b.getWorkspaceId().equals(owner.workspaceId()))
          .toList();
    } finally {
      WorkspaceContextHolder.set(owner);
      filterActivator.activate();
      auditService.append(
          owner.workspaceId(),
          "admin.boards.list",
          "board",
          null,
          "OWNER listed all boards in workspace",
          Map.of("scope", "workspace"));
    }
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listCardMetadataByBoard(UUID boardId) {
    WorkspaceContext owner = requireWorkspaceOwner();
    WorkspaceContextHolder.set(WorkspaceContext.system());
    filterActivator.activate();
    try {
      return cardRepo.findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(boardId).stream()
          .filter(c -> c.getWorkspaceId().equals(owner.workspaceId()))
          .map(AdminWorkspaceService::sanitize)
          .toList();
    } finally {
      WorkspaceContextHolder.set(owner);
      filterActivator.activate();
      auditService.append(
          owner.workspaceId(),
          "admin.cards.list_metadata",
          "board",
          boardId,
          "OWNER listed card metadata",
          Map.of("boardId", boardId.toString()));
    }
  }

  /** Escape hatch — 특정 카드 body 강제 노출. 사유 (reason) 필수 + audit 강한 기록. */
  @Transactional(readOnly = true)
  public Optional<CardEntity> revealCardBody(UUID cardId, String reason) {
    WorkspaceContext owner = requireWorkspaceOwner();
    if (reason == null || reason.isBlank() || reason.length() < 10) {
      throw new IllegalArgumentException("reason required (>= 10 chars) for body reveal");
    }
    WorkspaceContextHolder.set(WorkspaceContext.system());
    filterActivator.activate();
    try {
      return cardRepo
          .findById(cardId)
          .filter(c -> c.getDeletedAt() == null)
          .filter(c -> c.getWorkspaceId().equals(owner.workspaceId()));
    } finally {
      WorkspaceContextHolder.set(owner);
      filterActivator.activate();
      Map<String, Object> payload = new HashMap<>();
      payload.put("cardId", cardId.toString());
      payload.put("reason", reason);
      payload.put("severity", "high");
      auditService.append(
          owner.workspaceId(),
          "admin.card.reveal_body",
          "card",
          cardId,
          "ESCAPE HATCH: OWNER revealed card body",
          payload);
    }
  }

  private WorkspaceContext requireWorkspaceOwner() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope (OWNER) required for admin operations");
    }
    if (!ctx.isOwner()) {
      throw new IllegalStateException("OWNER role required for admin operations");
    }
    return ctx;
  }

  /** body_md, attrs 등 민감 필드 제거 — 운영 콘솔의 cross-user 메타뷰용. */
  private static Map<String, Object> sanitize(CardEntity card) {
    Map<String, Object> meta = new HashMap<>();
    meta.put("id", card.getId());
    meta.put("workspaceId", card.getWorkspaceId());
    meta.put("userId", card.getUserId());
    meta.put("boardId", card.getBoardId());
    meta.put("title", card.getTitle());
    meta.put("startDate", card.getStartDate());
    meta.put("endDate", card.getEndDate());
    meta.put("importance", card.getImportance());
    meta.put("origin", card.getOrigin());
    meta.put("priority", card.getPriority());
    meta.put("completed", card.isCompleted());
    meta.put("createdAt", card.getCreatedAt());
    meta.put("updatedAt", card.getUpdatedAt());
    return meta;
  }
}
