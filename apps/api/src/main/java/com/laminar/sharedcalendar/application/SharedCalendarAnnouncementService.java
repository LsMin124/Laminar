package com.laminar.sharedcalendar.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.error.NotFoundException;
import com.laminar.sharedcalendar.domain.SharedCalendarAnnouncementEntity;
import com.laminar.sharedcalendar.repository.SharedCalendarAnnouncementRepository;
import com.laminar.sharedcalendar.repository.SharedCalendarRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공용 캘린더 공지 — LAB 스코프 (L3). §1.3 매트릭스: 조회는 lab 멤버 전원, 작성/삭제는 ADMIN+(공지는 lab 공식 채널 — 작성자가 전부 관리자이므로
 * 본인 확인은 불요, 관리자 간 상호 삭제 허용).
 */
@Service
public class SharedCalendarAnnouncementService {

  private final SharedCalendarAnnouncementRepository announcementRepo;
  private final SharedCalendarRepository calendarRepo;

  public SharedCalendarAnnouncementService(
      SharedCalendarAnnouncementRepository announcementRepo,
      SharedCalendarRepository calendarRepo) {
    this.announcementRepo = announcementRepo;
    this.calendarRepo = calendarRepo;
  }

  @Transactional
  public SharedCalendarAnnouncementEntity post(
      UUID sharedCalendarId,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      String title,
      String bodyMd) {
    SubjectContext ctx = SubjectContextHolder.requireLabAdmin("announcements");
    if (startAt == null) {
      throw new IllegalArgumentException("start_at required");
    }
    if (endAt != null && endAt.isBefore(startAt)) {
      throw new IllegalArgumentException("end_at must be >= start_at");
    }
    calendarRepo
        .findById(sharedCalendarId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsShared(c.getSubjectId()))
        .orElseThrow(() -> new NotFoundException("shared calendar not found"));

    SharedCalendarAnnouncementEntity announcement = new SharedCalendarAnnouncementEntity();
    announcement.setSubjectId(ctx.subjectId());
    announcement.setSharedCalendarId(sharedCalendarId);
    announcement.setPostedBy(ctx.userId());
    announcement.setStartAt(startAt);
    announcement.setEndAt(endAt);
    announcement.setTitle(title);
    announcement.setBodyMd(bodyMd);
    return announcementRepo.save(announcement);
  }

  @Transactional(readOnly = true)
  public List<SharedCalendarAnnouncementEntity> listInRange(
      UUID sharedCalendarId, OffsetDateTime from, OffsetDateTime to) {
    SubjectContextHolder.requireLabMember("announcements");
    return announcementRepo
        .findBySharedCalendarIdAndStartAtBetweenAndDeletedAtIsNullOrderByStartAtAsc(
            sharedCalendarId, from, to);
  }

  @Transactional
  public void softDelete(UUID announcementId) {
    SubjectContext ctx = SubjectContextHolder.requireLabAdmin("announcements");
    announcementRepo
        .findById(announcementId)
        .filter(a -> a.getDeletedAt() == null)
        .filter(a -> ctx.ownsShared(a.getSubjectId()))
        .ifPresent(
            a -> {
              a.setDeletedAt(OffsetDateTime.now());
              announcementRepo.save(a);
            });
  }
}
