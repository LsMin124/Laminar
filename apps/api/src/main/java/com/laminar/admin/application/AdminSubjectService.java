package com.laminar.admin.application;

import com.laminar.audit.application.AuditLogService;
import com.laminar.card.domain.CardEntity;
import com.laminar.card.repository.CardRepository;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.tab.domain.TabEntity;
import com.laminar.tab.repository.TabRepository;
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
 * <p>정책: - subject OWNER만 호출 가능 - 모든 호출은 audit_log 자동 기록 (action=admin.*, actor + target) -
 * cross-user 메타 조회 허용, body_md 등 본문은 escape hatch 호출 + reason 명시 필요
 *
 * <p>구현: SYSTEM scope로 잠시 진입하여 Personal-First 필터 회피, subject_id 일치 검증은 application 단에서 명시. 호출 끝에
 * OWNER context 복원 + audit append.
 */
@Service
public class AdminSubjectService {

  private final TabRepository tabRepo;
  private final CardRepository cardRepo;
  private final AuditLogService auditService;
  private final HibernateFilterActivator filterActivator;

  public AdminSubjectService(
      TabRepository tabRepo,
      CardRepository cardRepo,
      AuditLogService auditService,
      HibernateFilterActivator filterActivator) {
    this.tabRepo = tabRepo;
    this.cardRepo = cardRepo;
    this.auditService = auditService;
    this.filterActivator = filterActivator;
  }

  @Transactional(readOnly = true)
  public List<TabEntity> listAllTabs() {
    SubjectContext owner = requireSubjectOwner();
    SubjectContextHolder.set(SubjectContext.system());
    filterActivator.activate();
    try {
      return tabRepo.findAll().stream()
          .filter(b -> b.getDeletedAt() == null)
          .filter(b -> b.getSubjectId().equals(owner.subjectId()))
          .toList();
    } finally {
      SubjectContextHolder.set(owner);
      filterActivator.activate();
      auditService.append(
          owner.subjectId(),
          "admin.boards.list",
          "tab",
          null,
          "OWNER listed all boards in subject",
          Map.of("scope", "subject"));
    }
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listCardMetadataByTab(UUID tabId) {
    SubjectContext owner = requireSubjectOwner();
    SubjectContextHolder.set(SubjectContext.system());
    filterActivator.activate();
    try {
      return cardRepo.findByTabIdAndDeletedAtIsNullOrderByPriorityAsc(tabId).stream()
          .filter(c -> c.getSubjectId().equals(owner.subjectId()))
          .map(AdminSubjectService::sanitize)
          .toList();
    } finally {
      SubjectContextHolder.set(owner);
      filterActivator.activate();
      auditService.append(
          owner.subjectId(),
          "admin.cards.list_metadata",
          "tab",
          tabId,
          "OWNER listed card metadata",
          Map.of("tabId", tabId.toString()));
    }
  }

  /** Escape hatch — 특정 카드 body 강제 노출. 사유 (reason) 필수 + audit 강한 기록. */
  @Transactional(readOnly = true)
  public Optional<CardEntity> revealCardBody(UUID cardId, String reason) {
    SubjectContext owner = requireSubjectOwner();
    if (reason == null || reason.isBlank() || reason.length() < 10) {
      throw new IllegalArgumentException("reason required (>= 10 chars) for body reveal");
    }
    SubjectContextHolder.set(SubjectContext.system());
    filterActivator.activate();
    try {
      return cardRepo
          .findById(cardId)
          .filter(c -> c.getDeletedAt() == null)
          .filter(c -> c.getSubjectId().equals(owner.subjectId()));
    } finally {
      SubjectContextHolder.set(owner);
      filterActivator.activate();
      Map<String, Object> payload = new HashMap<>();
      payload.put("cardId", cardId.toString());
      payload.put("reason", reason);
      payload.put("severity", "high");
      auditService.append(
          owner.subjectId(),
          "admin.card.reveal_body",
          "card",
          cardId,
          "ESCAPE HATCH: OWNER revealed card body",
          payload);
    }
  }

  private SubjectContext requireSubjectOwner() {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.scope() != SubjectContext.Scope.PERSONAL) {
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
    meta.put("subjectId", card.getSubjectId());
    meta.put("userId", card.getUserId());
    meta.put("tabId", card.getTabId());
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
