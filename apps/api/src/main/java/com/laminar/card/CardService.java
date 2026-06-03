package com.laminar.card;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 CRUD — Personal-First 격리 + 도메인 invariant 강제.
 *
 * <p>검증 (V3 cards 테이블 CHECK 제약 + Spec §3.5): - end_date >= start_date (DB chk_cards_end_date_order와
 * 동일) - end_date - start_date <= 30 (DB chk_cards_max_span) - importance=perpetual-ver ↔
 * linked_perpetual_id NN (DB chk_cards_perpetual_link) - rrule 있으면 all_day OR start_time NN (DB
 * chk_cards_rrule_time)
 *
 * <p>DB CHECK가 최종 방어선이지만 application 단에서 명시 검증으로 사용자 친화 에러.
 */
@Service
public class CardService {

  private static final int PRIORITY_STEP = 100;
  private static final int MAX_SPAN_DAYS = 30;

  private final CardRepository cardRepo;
  private final com.laminar.perpetual.application.PerpetualVersionService perpetualVersionService;

  public CardService(
      CardRepository cardRepo,
      com.laminar.perpetual.application.PerpetualVersionService perpetualVersionService) {
    this.cardRepo = cardRepo;
    this.perpetualVersionService = perpetualVersionService;
  }

  @Transactional
  public CardEntity create(CreateInput input) {
    WorkspaceContext ctx = requirePersonalWritable();
    validateInvariants(input);

    int nextPriority =
        input.boardId() == null
            ? PRIORITY_STEP
            : cardRepo
                .findFirstByBoardIdAndDeletedAtIsNullOrderByPriorityDesc(input.boardId())
                .map(c -> c.getPriority() + PRIORITY_STEP)
                .orElse(PRIORITY_STEP);

    CardEntity card = new CardEntity();
    card.setWorkspaceId(ctx.workspaceId());
    card.setUserId(ctx.userId());
    card.setCreatedBy(ctx.userId());
    card.setBoardId(input.boardId());
    card.setTitle(input.title());
    card.setSlug(input.slug());
    card.setBodyMd(input.bodyMd());
    card.setStartDate(input.startDate());
    card.setEndDate(input.endDate());
    card.setStartTime(input.startTime());
    card.setAllDay(input.allDay() == null ? true : input.allDay());
    card.setTimeZone(input.timeZone());
    card.setImportance(input.importance() == null ? CardImportance.NORMAL : input.importance());
    card.setLinkedPerpetualId(input.linkedPerpetualId());
    card.setRrule(input.rrule());
    card.setOrigin(input.origin() == null ? CardOrigin.MANUAL : input.origin());
    card.setPriority(nextPriority);
    card.setAttrs(input.attrs() == null ? new HashMap<>() : input.attrs());
    CardEntity saved = cardRepo.save(card);

    // Spec §3.5 카드 ↔ 영구노트: importance=perpetual-ver + linked_perpetual_id 있으면
    // 영구노트 새 버전 자동 commit + card_id 1:1 매핑 (uq_perpetual_versions_card).
    if (saved.getImportance() == CardImportance.PERPETUAL_VER
        && saved.getLinkedPerpetualId() != null) {
      perpetualVersionService.commit(
          saved.getLinkedPerpetualId(), saved.getId(), saved.getTitle(), saved.getBodyMd(), true);
    }
    return saved;
  }

  @Transactional(readOnly = true)
  public List<CardEntity> listByBoard(UUID boardId) {
    WorkspaceContextHolder.requirePersonal();
    return cardRepo.findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(boardId);
  }

  @Transactional(readOnly = true)
  public List<CardEntity> listByBoardAndDateRange(UUID boardId, LocalDate from, LocalDate to) {
    WorkspaceContextHolder.requirePersonal();
    return cardRepo.findByBoardIdAndStartDateBetweenAndDeletedAtIsNull(boardId, from, to);
  }

  @Transactional(readOnly = true)
  public Optional<CardEntity> findById(UUID cardId) {
    WorkspaceContext ctx = WorkspaceContextHolder.requirePersonal();
    return cardRepo
        .findById(cardId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsPersonal(c.getWorkspaceId(), c.getUserId()));
  }

  @Transactional
  public CardEntity update(UUID cardId, UpdateInput input) {
    WorkspaceContext ctx = requirePersonalWritable();
    CardEntity card =
        cardRepo
            .findById(cardId)
            .filter(c -> c.getDeletedAt() == null)
            .filter(c -> ctx.ownsPersonal(c.getWorkspaceId(), c.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("card not found: " + cardId));

    if (input.title() != null && !input.title().isBlank()) card.setTitle(input.title());
    if (input.bodyMd() != null) card.setBodyMd(input.bodyMd());
    if (input.startDate() != null) card.setStartDate(input.startDate());
    if (input.endDate() != null) card.setEndDate(input.endDate());
    if (input.startTime() != null) card.setStartTime(input.startTime());
    if (input.allDay() != null) card.setAllDay(input.allDay());
    if (input.timeZone() != null) card.setTimeZone(input.timeZone());
    if (input.importance() != null) card.setImportance(input.importance());
    if (input.linkedPerpetualId() != null) card.setLinkedPerpetualId(input.linkedPerpetualId());
    if (input.rrule() != null) card.setRrule(input.rrule());
    if (input.completed() != null) card.setCompleted(input.completed());
    if (input.attrs() != null) card.setAttrs(input.attrs());

    validateInvariants(card);
    return cardRepo.save(card);
  }

  /** DnD reorder — boardId 일치 검증 + priority = (index+1) * 100 배치. */
  @Transactional
  public List<CardEntity> reorder(UUID boardId, List<UUID> orderedCardIds) {
    WorkspaceContext ctx = requirePersonalWritable();
    if (orderedCardIds == null || orderedCardIds.isEmpty()) {
      return List.of();
    }
    List<CardEntity> result = new java.util.ArrayList<>(orderedCardIds.size());
    for (int i = 0; i < orderedCardIds.size(); i++) {
      UUID cardId = orderedCardIds.get(i);
      int newPriority = (i + 1) * PRIORITY_STEP;
      cardRepo
          .findById(cardId)
          .filter(c -> c.getDeletedAt() == null)
          .filter(c -> ctx.ownsPersonal(c.getWorkspaceId(), c.getUserId()))
          .filter(c -> boardId == null || boardId.equals(c.getBoardId()))
          .ifPresent(
              c -> {
                c.setPriority(newPriority);
                result.add(cardRepo.save(c));
              });
    }
    return result;
  }

  @Transactional
  public void archive(UUID cardId) {
    WorkspaceContext ctx = requirePersonalWritable();
    cardRepo
        .findById(cardId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsPersonal(c.getWorkspaceId(), c.getUserId()))
        .filter(c -> c.getArchivedAt() == null)
        .ifPresent(
            card -> {
              card.setArchivedAt(OffsetDateTime.now());
              cardRepo.save(card);
            });
  }

  @Transactional
  public void softDelete(UUID cardId) {
    WorkspaceContext ctx = requirePersonalWritable();
    cardRepo
        .findById(cardId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsPersonal(c.getWorkspaceId(), c.getUserId()))
        .ifPresent(
            card -> {
              card.setDeletedAt(OffsetDateTime.now());
              cardRepo.save(card);
            });
  }

  private WorkspaceContext requirePersonalWritable() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate cards");
    }
    return ctx;
  }

  private void validateInvariants(CreateInput input) {
    validateDateRange(input.startDate(), input.endDate());
    validatePerpetualLink(input.importance(), input.linkedPerpetualId());
    validateRruleTime(input.rrule(), input.allDay(), input.startTime());
  }

  private void validateInvariants(CardEntity card) {
    validateDateRange(card.getStartDate(), card.getEndDate());
    validatePerpetualLink(card.getImportance(), card.getLinkedPerpetualId());
    validateRruleTime(card.getRrule(), card.isAllDay(), card.getStartTime());
  }

  private void validateDateRange(LocalDate start, LocalDate end) {
    if (start == null || end == null) return;
    if (end.isBefore(start)) {
      throw new IllegalArgumentException("end_date must be >= start_date");
    }
    long days = ChronoUnit.DAYS.between(start, end);
    if (days > MAX_SPAN_DAYS) {
      throw new IllegalArgumentException("date span exceeds " + MAX_SPAN_DAYS + " days");
    }
  }

  private void validatePerpetualLink(CardImportance importance, UUID linkedPerpetualId) {
    if (importance == null) return;
    boolean isPerpetualVer = importance == CardImportance.PERPETUAL_VER;
    boolean linkPresent = linkedPerpetualId != null;
    if (isPerpetualVer != linkPresent) {
      throw new IllegalArgumentException(
          "importance=perpetual-ver requires linked_perpetual_id (and vice versa)");
    }
  }

  private void validateRruleTime(String rrule, Boolean allDay, LocalTime startTime) {
    if (rrule == null || rrule.isBlank()) return;
    boolean allDayOn = allDay == null ? true : allDay;
    if (!allDayOn && startTime == null) {
      throw new IllegalArgumentException("rrule requires all_day or start_time");
    }
  }

  public record CreateInput(
      UUID boardId,
      String title,
      String slug,
      String bodyMd,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      Boolean allDay,
      String timeZone,
      CardImportance importance,
      UUID linkedPerpetualId,
      String rrule,
      CardOrigin origin,
      Map<String, Object> attrs) {}

  public record UpdateInput(
      String title,
      String bodyMd,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      Boolean allDay,
      String timeZone,
      CardImportance importance,
      UUID linkedPerpetualId,
      String rrule,
      Boolean completed,
      Map<String, Object> attrs) {}
}
