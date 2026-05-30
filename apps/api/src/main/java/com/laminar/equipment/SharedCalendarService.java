package com.laminar.equipment;

import com.laminar.board.BoardDefaultView;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.web.error.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 공용 캘린더 — 장비별 1:1 또는 일반 공지.
 *
 * Spec §2.10.4: equipment_id 1:1 unique (active rows) + is_announcement_only 플래그.
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
            BoardDefaultView defaultView,
            boolean announcementOnly) {
        WorkspaceContext ctx = requireWorkspaceWritable();
        if (equipmentId != null
                && calendarRepo.findByEquipmentIdAndDeletedAtIsNull(equipmentId).isPresent()) {
            throw new ConflictException("equipment already has a shared calendar");
        }

        SharedCalendarEntity cal = new SharedCalendarEntity();
        cal.setWorkspaceId(ctx.workspaceId());
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
        WorkspaceContextHolder.requirePersonal();
        return calendarRepo.findByDeletedAtIsNullOrderByName();
    }

    @Transactional(readOnly = true)
    public Optional<SharedCalendarEntity> findByEquipment(UUID equipmentId) {
        WorkspaceContextHolder.requirePersonal();
        return calendarRepo.findByEquipmentIdAndDeletedAtIsNull(equipmentId);
    }

    @Transactional
    public void softDelete(UUID calendarId) {
        WorkspaceContext ctx = requireWorkspaceWritable();
        calendarRepo.findById(calendarId)
                .filter(c -> c.getDeletedAt() == null)
                .filter(c -> ctx.ownsShared(c.getWorkspaceId()))
                .ifPresent(c -> {
                    c.setDeletedAt(OffsetDateTime.now());
                    calendarRepo.save(c);
                });
    }

    private WorkspaceContext requireWorkspaceWritable() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.workspaceId() == null) {
            throw new IllegalStateException("workspace scope required");
        }
        if (ctx.scope() == WorkspaceContext.Scope.PERSONAL && !ctx.canWrite()) {
            throw new IllegalStateException("VIEWER cannot mutate shared calendars");
        }
        return ctx;
    }
}
