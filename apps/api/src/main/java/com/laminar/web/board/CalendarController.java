package com.laminar.web.board;

import com.laminar.board.CalendarService;
import com.laminar.card.CardEntity;
import com.laminar.datememo.domain.DateMemoEntity;
import com.laminar.web.card.CardDtos;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** /api/boards/{boardId}/calendar — 월/주 range + 멀티데이 + date_memos 통합 응답. */
@RestController
@RequestMapping("/api/boards")
public class CalendarController {

  private final CalendarService calendarService;

  public CalendarController(CalendarService calendarService) {
    this.calendarService = calendarService;
  }

  @GetMapping("/{boardId}/calendar")
  public ResponseEntity<CalendarViewResponse> view(
      @PathVariable UUID boardId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    CalendarService.CalendarView view = calendarService.getBoardView(boardId, from, to);
    return ResponseEntity.ok(
        new CalendarViewResponse(
            view.boardId(),
            view.from(),
            view.to(),
            view.cards().stream().map(CalendarController::toCardResponse).toList(),
            view.dateMemos().stream().map(CalendarController::toDateMemoResponse).toList()));
  }

  public record CalendarViewResponse(
      UUID boardId,
      LocalDate from,
      LocalDate to,
      List<CardDtos.CardResponse> cards,
      List<DateMemoResponse> dateMemos) {}

  public record DateMemoResponse(
      UUID boardId, UUID userId, LocalDate date, String bodyMd, Map<String, Object> attrs) {}

  private static CardDtos.CardResponse toCardResponse(CardEntity c) {
    return new CardDtos.CardResponse(
        c.getId(),
        c.getWorkspaceId(),
        c.getUserId(),
        c.getBoardId(),
        c.getTitle(),
        c.getSlug(),
        c.getBodyMd(),
        c.getStartDate(),
        c.getEndDate(),
        c.getStartTime(),
        c.isAllDay(),
        c.getTimeZone(),
        c.getImportance(),
        c.isCompleted(),
        c.getLinkedPerpetualId(),
        c.getRrule(),
        c.getOrigin(),
        c.getPriority(),
        c.getAttrs(),
        c.getArchivedAt(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }

  private static DateMemoResponse toDateMemoResponse(DateMemoEntity m) {
    return new DateMemoResponse(
        m.getId().getBoardId(),
        m.getId().getUserId(),
        m.getId().getDate(),
        m.getBodyMd(),
        m.getAttrs());
  }
}
