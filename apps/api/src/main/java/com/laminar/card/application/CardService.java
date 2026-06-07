package com.laminar.card.application;

import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardImportance;
import com.laminar.card.domain.CardOrigin;
import com.laminar.card.repository.CardRepository;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.error.ConflictException;
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
 * 동일) - end_date - start_date <= 30 (DB chk_cards_max_span) - rrule 있으면 all_day OR start_time NN
 * (DB chk_cards_rrule_time)
 *
 * <p>DB CHECK가 최종 방어선이지만 application 단에서 명시 검증으로 사용자 친화 에러.
 */
@Service
public class CardService {

  private static final int PRIORITY_STEP = 100;
  private static final int MAX_SPAN_DAYS = 30;

  private final CardRepository cardRepo;
  private final CardDagService dagService;

  public CardService(CardRepository cardRepo, CardDagService dagService) {
    this.cardRepo = cardRepo;
    this.dagService = dagService;
  }

  @Transactional
  public CardEntity create(CreateInput input) {
    SubjectContext ctx = requirePersonalWritable();
    validateInvariants(input);

    int nextPriority =
        input.tabId() == null
            ? PRIORITY_STEP
            : cardRepo
                .findFirstByTabIdAndDeletedAtIsNullOrderByPriorityDesc(input.tabId())
                .map(c -> c.getPriority() + PRIORITY_STEP)
                .orElse(PRIORITY_STEP);

    CardEntity card = new CardEntity();
    card.setSubjectId(ctx.subjectId());
    card.setUserId(ctx.userId());
    card.setCreatedBy(ctx.userId());
    card.setTabId(input.tabId());
    card.setTitle(input.title());
    card.setSlug(input.slug());
    card.setBodyMd(input.bodyMd());
    card.setStartDate(input.startDate());
    card.setEndDate(input.endDate());
    card.setStartTime(input.startTime());
    card.setAllDay(input.allDay() == null ? true : input.allDay());
    card.setTimeZone(input.timeZone());
    card.setImportance(input.importance() == null ? CardImportance.NORMAL : input.importance());
    card.setRrule(input.rrule());
    card.setOrigin(input.origin() == null ? CardOrigin.MANUAL : input.origin());
    card.setPriority(nextPriority);
    card.setAttrs(input.attrs() == null ? new HashMap<>() : input.attrs());
    return cardRepo.save(card);
  }

  @Transactional(readOnly = true)
  public List<CardEntity> listByTab(UUID tabId) {
    SubjectContextHolder.requirePersonal();
    return cardRepo.findByTabIdAndDeletedAtIsNullOrderByPriorityAsc(tabId);
  }

  @Transactional(readOnly = true)
  public List<CardEntity> listByTabAndDateRange(UUID tabId, LocalDate from, LocalDate to) {
    SubjectContextHolder.requirePersonal();
    return cardRepo.findByTabIdAndStartDateBetweenAndDeletedAtIsNull(tabId, from, to);
  }

  @Transactional(readOnly = true)
  public Optional<CardEntity> findById(UUID cardId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonal();
    return cardRepo
        .findById(cardId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsPersonal(c.getSubjectId(), c.getUserId()));
  }

  /** 카드 카테고리 지정/해제 — categoryId null이면 미분류. (FK가 실재 카테고리를 보장.) */
  @Transactional
  public CardEntity setCategory(UUID cardId, UUID categoryId) {
    SubjectContext ctx = requirePersonalWritable();
    CardEntity card =
        cardRepo
            .findById(cardId)
            .filter(c -> c.getDeletedAt() == null)
            .filter(c -> ctx.ownsPersonal(c.getSubjectId(), c.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("card not found: " + cardId));
    card.setCategoryId(categoryId);
    return cardRepo.save(card);
  }

  @Transactional
  public CardEntity update(UUID cardId, UpdateInput input) {
    SubjectContext ctx = requirePersonalWritable();
    CardEntity card =
        cardRepo
            .findById(cardId)
            .filter(c -> c.getDeletedAt() == null)
            .filter(c -> ctx.ownsPersonal(c.getSubjectId(), c.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("card not found: " + cardId));

    if (input.title() != null && !input.title().isBlank()) card.setTitle(input.title());
    if (input.bodyMd() != null) card.setBodyMd(input.bodyMd());
    if (input.startDate() != null) card.setStartDate(input.startDate());
    if (input.endDate() != null) card.setEndDate(input.endDate());
    if (input.startTime() != null) card.setStartTime(input.startTime());
    if (input.allDay() != null) card.setAllDay(input.allDay());
    if (input.timeZone() != null) card.setTimeZone(input.timeZone());
    if (input.importance() != null) card.setImportance(input.importance());
    if (input.rrule() != null) card.setRrule(input.rrule());
    if (input.completed() != null) card.setCompleted(input.completed());
    if (input.attrs() != null) card.setAttrs(input.attrs());
    if (input.canvasY() != null) card.setCanvasY(input.canvasY());

    validateInvariants(card);

    // 시간 강제 (DAG): startDate 변경 시 선행 카드보다 앞당길 수 없음 (상류 불변식 B.start ≥ A.start)
    if (input.startDate() != null && card.getTabId() != null && card.getStartDate() != null) {
      LocalDate maxPredecessorStart = dagService.maxPredecessorStart(card.getTabId(), card.getId());
      if (maxPredecessorStart != null && card.getStartDate().isBefore(maxPredecessorStart)) {
        throw new ConflictException(
            "cannot move card before its predecessor (predecessor starts "
                + maxPredecessorStart
                + ")");
      }
    }
    CardEntity saved = cardRepo.save(card);
    // 후행 노드 연쇄 이동 (B.start ≥ A.start)
    if (input.startDate() != null && card.getTabId() != null) {
      dagService.cascadeForward(card.getTabId(), card.getId());
    }
    return saved;
  }

  /** DnD reorder — tabId 일치 검증 + priority = (index+1) * 100 배치. */
  @Transactional
  public List<CardEntity> reorder(UUID tabId, List<UUID> orderedCardIds) {
    SubjectContext ctx = requirePersonalWritable();
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
          .filter(c -> ctx.ownsPersonal(c.getSubjectId(), c.getUserId()))
          .filter(c -> tabId == null || tabId.equals(c.getTabId()))
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
    SubjectContext ctx = requirePersonalWritable();
    cardRepo
        .findById(cardId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsPersonal(c.getSubjectId(), c.getUserId()))
        .filter(c -> c.getArchivedAt() == null)
        .ifPresent(
            card -> {
              card.setArchivedAt(OffsetDateTime.now());
              cardRepo.save(card);
            });
  }

  @Transactional
  public void softDelete(UUID cardId) {
    SubjectContext ctx = requirePersonalWritable();
    cardRepo
        .findById(cardId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsPersonal(c.getSubjectId(), c.getUserId()))
        .ifPresent(
            card -> {
              card.setDeletedAt(OffsetDateTime.now());
              cardRepo.save(card);
            });
  }

  private SubjectContext requirePersonalWritable() {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.scope() != SubjectContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate cards");
    }
    return ctx;
  }

  private void validateInvariants(CreateInput input) {
    validateDateRange(input.startDate(), input.endDate());
    validateRruleTime(input.rrule(), input.allDay(), input.startTime());
  }

  private void validateInvariants(CardEntity card) {
    validateDateRange(card.getStartDate(), card.getEndDate());
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

  private void validateRruleTime(String rrule, Boolean allDay, LocalTime startTime) {
    if (rrule == null || rrule.isBlank()) return;
    boolean allDayOn = allDay == null ? true : allDay;
    if (!allDayOn && startTime == null) {
      throw new IllegalArgumentException("rrule requires all_day or start_time");
    }
  }

  public record CreateInput(
      UUID tabId,
      String title,
      String slug,
      String bodyMd,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      Boolean allDay,
      String timeZone,
      CardImportance importance,
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
      String rrule,
      Boolean completed,
      Map<String, Object> attrs,
      Double canvasY) {}
}
