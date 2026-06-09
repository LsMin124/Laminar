package com.laminar.tab.presentation;

import com.laminar.card.domain.CardEntity;
import com.laminar.card.presentation.CardDtos;
import com.laminar.datememo.domain.DateMemoEntity;
import com.laminar.tab.application.CalendarService;
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

/** /api/tabs/{tabId}/calendar — 월/주 range + 멀티데이 + date_memos 통합 응답. */
@RestController
@RequestMapping("/api/tabs")
public class CalendarController {

  private final CalendarService calendarService;

  public CalendarController(CalendarService calendarService) {
    this.calendarService = calendarService;
  }

  @GetMapping("/{tabId}/calendar")
  public ResponseEntity<CalendarViewResponse> view(
      @PathVariable UUID tabId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    CalendarService.CalendarView view = calendarService.getTabView(tabId, from, to);
    return ResponseEntity.ok(
        new CalendarViewResponse(
            view.tabId(),
            view.from(),
            view.to(),
            view.cards().stream().map(CalendarController::toCardResponse).toList(),
            view.dateMemos().stream().map(CalendarController::toDateMemoResponse).toList()));
  }

  public record CalendarViewResponse(
      UUID tabId,
      LocalDate from,
      LocalDate to,
      List<CardDtos.CardResponse> cards,
      List<DateMemoResponse> dateMemos) {}

  public record DateMemoResponse(
      UUID tabId, UUID userId, LocalDate date, String bodyMd, Map<String, Object> attrs) {}

  private static CardDtos.CardResponse toCardResponse(CardEntity c) {
    return new CardDtos.CardResponse(
        c.getId(),
        c.getSubjectId(),
        c.getUserId(),
        c.getTabId(),
        c.getTitle(),
        c.getSlug(),
        c.getBodyMd(),
        null,
        c.getStartDate(),
        c.getEndDate(),
        c.getStartTime(),
        c.isAllDay(),
        c.getTimeZone(),
        c.getImportance(),
        c.isCompleted(),
        c.getRrule(),
        c.getOrigin(),
        c.getPriority(),
        c.getAttrs(),
        c.getArchivedAt(),
        c.getCreatedAt(),
        c.getUpdatedAt(),
        c.getCanvasY());
  }

  private static DateMemoResponse toDateMemoResponse(DateMemoEntity m) {
    return new DateMemoResponse(
        m.getId().getTabId(),
        m.getId().getUserId(),
        m.getId().getDate(),
        m.getBodyMd(),
        m.getAttrs());
  }
}
