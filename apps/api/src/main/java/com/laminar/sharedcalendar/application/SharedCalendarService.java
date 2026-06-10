package com.laminar.sharedcalendar.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.error.ConflictException;
import com.laminar.sharedcalendar.domain.SharedCalendarEntity;
import com.laminar.sharedcalendar.repository.SharedCalendarRepository;
import com.laminar.tab.domain.TabDefaultView;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공용 캘린더 — 장비별 1:1 또는 일반 공지.
 *
 * <p>Spec §2.10.4: equipment_id 1:1 unique (active rows) + is_announcement_only 플래그.
 */
@Service
public class SharedCalendarService {

  private final SharedCalendarRepository calendarRepo;

  public SharedCalendarService(SharedCalendarRepository calendarRepo) {
    this.calendarRepo = calendarRepo;
  }

  @Transactional
  public SharedCalendarEntity create(
      UUID equipmentId,
      String name,
      String color,
      TabDefaultView defaultView,
      boolean announcementOnly) {
    SubjectContext ctx = requireSubjectWritable();
    if (equipmentId != null
        && calendarRepo.findByEquipmentIdAndDeletedAtIsNull(equipmentId).isPresent()) {
      throw new ConflictException("equipment already has a shared calendar");
    }

    SharedCalendarEntity cal = new SharedCalendarEntity();
    cal.setSubjectId(ctx.subjectId());
    cal.setCreatedBy(ctx.userId());
    cal.setEquipmentId(equipmentId);
    cal.setName(name);
    cal.setColor(color);
    cal.setDefaultView(defaultView);
    cal.setAnnouncementOnly(announcementOnly);
    return calendarRepo.save(cal);
  }

  @Transactional(readOnly = true)
  public List<SharedCalendarEntity> listAll() {
    SubjectContextHolder.requirePersonal();
    return calendarRepo.findByDeletedAtIsNullOrderByName();
  }

  @Transactional(readOnly = true)
  public Optional<SharedCalendarEntity> findByEquipment(UUID equipmentId) {
    SubjectContextHolder.requirePersonal();
    return calendarRepo.findByEquipmentIdAndDeletedAtIsNull(equipmentId);
  }

  @Transactional
  public void softDelete(UUID calendarId) {
    SubjectContext ctx = requireSubjectWritable();
    calendarRepo
        .findById(calendarId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsUser(c.getCreatedBy()))
        .ifPresent(
            c -> {
              c.setDeletedAt(OffsetDateTime.now());
              calendarRepo.save(c);
            });
  }

  private SubjectContext requireSubjectWritable() {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.subjectId() == null) {
      throw new IllegalStateException("subject scope required");
    }
    if (ctx.scope() == SubjectContext.Scope.PERSONAL && !ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate shared calendars");
    }
    return ctx;
  }
}
