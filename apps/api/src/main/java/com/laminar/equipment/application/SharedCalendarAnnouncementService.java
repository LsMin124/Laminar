package com.laminar.equipment.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.equipment.domain.SharedCalendarAnnouncementEntity;
import com.laminar.equipment.repository.SharedCalendarAnnouncementRepository;
import com.laminar.equipment.repository.SharedCalendarRepository;
import com.laminar.error.NotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공용 캘린더 공지 — subject-shared. */
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
    SubjectContext ctx = requireSubjectWritable();
    if (startAt == null) {
      throw new IllegalArgumentException("start_at required");
    }
    if (endAt != null && endAt.isBefore(startAt)) {
      throw new IllegalArgumentException("end_at must be >= start_at");
    }
    calendarRepo
        .findById(sharedCalendarId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsUser(c.getCreatedBy()))
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
    SubjectContextHolder.requirePersonal();
    return announcementRepo
        .findBySharedCalendarIdAndStartAtBetweenAndDeletedAtIsNullOrderByStartAtAsc(
            sharedCalendarId, from, to);
  }

  @Transactional
  public void softDelete(UUID announcementId) {
    SubjectContext ctx = requireSubjectWritable();
    announcementRepo
        .findById(announcementId)
        .filter(a -> a.getDeletedAt() == null)
        .filter(a -> ctx.ownsUser(a.getPostedBy()))
        .ifPresent(
            a -> {
              if (!ctx.isOwner() && !a.getPostedBy().equals(ctx.userId())) {
                throw new IllegalStateException(
                    "can only delete own announcement (OWNER override)");
              }
              a.setDeletedAt(OffsetDateTime.now());
              announcementRepo.save(a);
            });
  }

  private SubjectContext requireSubjectWritable() {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.subjectId() == null) {
      throw new IllegalStateException("subject scope required");
    }
    if (ctx.scope() == SubjectContext.Scope.PERSONAL && !ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot post announcements");
    }
    return ctx;
  }
}
