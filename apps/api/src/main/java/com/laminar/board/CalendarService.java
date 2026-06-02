package com.laminar.board;

import com.laminar.card.CardEntity;
import com.laminar.card.CardRepository;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.datememo.DateMemoEntity;
import com.laminar.datememo.DateMemoRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캘린더 뷰 — cards overlap + date_memos 통합 응답.
 *
 * <p>멀티데이 카드는 (start_date <= to AND end_date >= from) 또는 종료 미설정 인 경우 단일일. RRULE 카드의 expand는 별도
 * cron이 미리 row를 생성 (origin=rrule_expansion) — 본 메서드는 unrolled rows만.
 */
@Service
public class CalendarService {

  private static final int MAX_RANGE_DAYS = 92;

  private final CardRepository cardRepo;
  private final DateMemoRepository memoRepo;

  public CalendarService(CardRepository cardRepo, DateMemoRepository memoRepo) {
    this.cardRepo = cardRepo;
    this.memoRepo = memoRepo;
  }

  @Transactional(readOnly = true)
  public CalendarView getBoardView(UUID boardId, LocalDate from, LocalDate to) {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required for calendar view");
    }
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to required (ISO date)");
    }
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to must be >= from");
    }
    long span = ChronoUnit.DAYS.between(from, to);
    if (span > MAX_RANGE_DAYS) {
      throw new IllegalArgumentException("range exceeds " + MAX_RANGE_DAYS + " days");
    }

    List<CardEntity> cards = cardRepo.findOverlappingByBoardId(boardId, from, to);
    List<DateMemoEntity> memos = memoRepo.findByIdBoardIdAndIdDateBetween(boardId, from, to);

    return new CalendarView(boardId, from, to, cards, memos);
  }

  public record CalendarView(
      UUID boardId,
      LocalDate from,
      LocalDate to,
      List<CardEntity> cards,
      List<DateMemoEntity> dateMemos) {}
}
