package com.laminar.equipment.application;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.equipment.domain.SharedCalendarAnnouncementEntity;
import com.laminar.equipment.repository.SharedCalendarAnnouncementRepository;
import com.laminar.equipment.repository.SharedCalendarRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공용 캘린더 공지 — workspace-shared. */
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
    WorkspaceContext ctx = requireWorkspaceWritable();
    if (startAt == null) {
      throw new IllegalArgumentException("start_at required");
    }
    if (endAt != null && endAt.isBefore(startAt)) {
      throw new IllegalArgumentException("end_at must be >= start_at");
    }
    calendarRepo
        .findById(sharedCalendarId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsShared(c.getWorkspaceId()))
        .orElseThrow(() -> new IllegalArgumentException("shared calendar not found"));

    SharedCalendarAnnouncementEntity announcement = new SharedCalendarAnnouncementEntity();
    announcement.setWorkspaceId(ctx.workspaceId());
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
    WorkspaceContextHolder.requirePersonal();
    return announcementRepo
        .findBySharedCalendarIdAndStartAtBetweenAndDeletedAtIsNullOrderByStartAtAsc(
            sharedCalendarId, from, to);
  }

  @Transactional
  public void softDelete(UUID announcementId) {
    WorkspaceContext ctx = requireWorkspaceWritable();
    announcementRepo
        .findById(announcementId)
        .filter(a -> a.getDeletedAt() == null)
        .filter(a -> ctx.ownsShared(a.getWorkspaceId()))
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

  private WorkspaceContext requireWorkspaceWritable() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.workspaceId() == null) {
      throw new IllegalStateException("workspace scope required");
    }
    if (ctx.scope() == WorkspaceContext.Scope.PERSONAL && !ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot post announcements");
    }
    return ctx;
  }
}
