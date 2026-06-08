package com.laminar.equipment.presentation;

import com.laminar.equipment.application.SharedCalendarAnnouncementService;
import com.laminar.equipment.application.SharedCalendarService;
import com.laminar.equipment.domain.SharedCalendarAnnouncementEntity;
import com.laminar.equipment.domain.SharedCalendarEntity;
import com.laminar.tab.domain.TabDefaultView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api — 공용 캘린더 + 공지. subject-shared.
 *
 * <p>모든 멤버 read·write, 공지 삭제는 본인 또는 OWNER (service 강제).
 */
@RestController
@RequestMapping("/api")
public class SharedCalendarController {

  private final SharedCalendarService calendarService;
  private final SharedCalendarAnnouncementService announcementService;

  public SharedCalendarController(
      SharedCalendarService calendarService,
      SharedCalendarAnnouncementService announcementService) {
    this.calendarService = calendarService;
    this.announcementService = announcementService;
  }

  // ── 캘린더 ─────────────────────────────────────────────────────────────

  @GetMapping("/shared-calendars")
  public ResponseEntity<List<CalendarResponse>> listCalendars() {
    return ResponseEntity.ok(
        calendarService.listAll().stream().map(SharedCalendarController::toCalendar).toList());
  }

  @PostMapping("/shared-calendars")
  public ResponseEntity<CalendarResponse> createCalendar(
      @Valid @RequestBody CreateCalendarRequest request) {
    SharedCalendarEntity cal =
        calendarService.create(
            request.equipmentId(),
            request.name(),
            request.color(),
            request.defaultView(),
            request.announcementOnly());
    return ResponseEntity.ok(toCalendar(cal));
  }

  @DeleteMapping("/shared-calendars/{calendarId}")
  public ResponseEntity<Void> deleteCalendar(@PathVariable UUID calendarId) {
    calendarService.softDelete(calendarId);
    return ResponseEntity.noContent().build();
  }

  // ── 공지 ───────────────────────────────────────────────────────────────

  @GetMapping("/shared-calendars/{calendarId}/announcements")
  public ResponseEntity<List<AnnouncementResponse>> listAnnouncements(
      @PathVariable UUID calendarId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
    return ResponseEntity.ok(
        announcementService.listInRange(calendarId, from, to).stream()
            .map(SharedCalendarController::toAnnouncement)
            .toList());
  }

  @PostMapping("/shared-calendars/{calendarId}/announcements")
  public ResponseEntity<AnnouncementResponse> post(
      @PathVariable UUID calendarId, @Valid @RequestBody PostAnnouncementRequest request) {
    SharedCalendarAnnouncementEntity announcement =
        announcementService.post(
            calendarId, request.startAt(), request.endAt(), request.title(), request.bodyMd());
    return ResponseEntity.ok(toAnnouncement(announcement));
  }

  @DeleteMapping("/announcements/{announcementId}")
  public ResponseEntity<Void> deleteAnnouncement(@PathVariable UUID announcementId) {
    announcementService.softDelete(announcementId);
    return ResponseEntity.noContent().build();
  }

  // ── DTO ────────────────────────────────────────────────────────────────

  public record CreateCalendarRequest(
      UUID equipmentId,
      @NotBlank @Size(max = 200) String name,
      @Size(max = 30) String color,
      TabDefaultView defaultView,
      boolean announcementOnly) {}

  public record CalendarResponse(
      UUID id,
      UUID equipmentId,
      String name,
      String color,
      TabDefaultView defaultView,
      boolean announcementOnly,
      OffsetDateTime createdAt) {}

  public record PostAnnouncementRequest(
      @NotNull OffsetDateTime startAt,
      OffsetDateTime endAt,
      @NotBlank @Size(max = 300) String title,
      @Size(max = 100000) String bodyMd) {}

  public record AnnouncementResponse(
      UUID id,
      UUID sharedCalendarId,
      UUID postedBy,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      String title,
      String bodyMd,
      OffsetDateTime createdAt) {}

  private static CalendarResponse toCalendar(SharedCalendarEntity c) {
    return new CalendarResponse(
        c.getId(),
        c.getEquipmentId(),
        c.getName(),
        c.getColor(),
        c.getDefaultView(),
        c.isAnnouncementOnly(),
        c.getCreatedAt());
  }

  private static AnnouncementResponse toAnnouncement(SharedCalendarAnnouncementEntity a) {
    return new AnnouncementResponse(
        a.getId(),
        a.getSharedCalendarId(),
        a.getPostedBy(),
        a.getStartAt(),
        a.getEndAt(),
        a.getTitle(),
        a.getBodyMd(),
        a.getCreatedAt());
  }
}
