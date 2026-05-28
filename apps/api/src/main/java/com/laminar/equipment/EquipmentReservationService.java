package com.laminar.equipment;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 장비 예약 — workspace-shared, 시간 겹침 차단.
 *
 * 검증 (V11 §2.10.6 + DB EXCLUDE):
 *   - start_at < end_at
 *   - 길이 ≤ 7일 (chk_er_max_span)
 *   - 같은 equipment + 같은 시간대 겹침 차단 (단일 예약, rrule IS NULL에 한정)
 *
 * RRULE 반복 예약은 DB EXCLUDE 미적용 — service에서 인스턴스 확장 후 개별 검증 책임.
 */
@Service
public class EquipmentReservationService {

    private static final Duration MAX_SPAN = Duration.ofDays(7);

    private final EquipmentReservationRepository reservationRepo;
    private final EquipmentRepository equipmentRepo;

    public EquipmentReservationService(
            EquipmentReservationRepository reservationRepo,
            EquipmentRepository equipmentRepo) {
        this.reservationRepo = reservationRepo;
        this.equipmentRepo = equipmentRepo;
    }

    @Transactional
    public EquipmentReservationEntity reserve(
            UUID equipmentId,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            String purpose,
            String rrule,
            UUID cardId) {
        WorkspaceContext ctx = requireWorkspaceWritable();
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("start_at and end_at required");
        }
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("end_at must be > start_at");
        }
        Duration span = Duration.between(startAt, endAt);
        if (span.compareTo(MAX_SPAN) > 0) {
            throw new IllegalArgumentException("reservation length exceeds 7 days");
        }
        equipmentRepo.findById(equipmentId)
                .filter(e -> e.getDeletedAt() == null)
                .filter(e -> ctx.ownsShared(e.getWorkspaceId()))
                .filter(EquipmentEntity::isActive)
                .orElseThrow(() -> new IllegalArgumentException("equipment not found or inactive"));

        // 단일 예약 (rrule null)만 겹침 사전 검증 — DB EXCLUDE도 같은 정책.
        if (rrule == null || rrule.isBlank()) {
            List<EquipmentReservationEntity> overlapping = reservationRepo
                    .findOverlapping(equipmentId, startAt, endAt).stream()
                    .filter(r -> r.getRrule() == null || r.getRrule().isBlank())
                    .toList();
            if (!overlapping.isEmpty()) {
                throw new IllegalStateException("reservation overlaps with existing booking");
            }
        }

        EquipmentReservationEntity reservation = new EquipmentReservationEntity();
        reservation.setWorkspaceId(ctx.workspaceId());
        reservation.setEquipmentId(equipmentId);
        reservation.setReservedBy(ctx.userId());
        reservation.setStartAt(startAt);
        reservation.setEndAt(endAt);
        reservation.setPurpose(purpose);
        reservation.setRrule(rrule);
        reservation.setCardId(cardId);
        return reservationRepo.save(reservation);
    }

    @Transactional
    public void cancel(UUID reservationId) {
        WorkspaceContext ctx = requireWorkspaceWritable();
        EquipmentReservationEntity reservation = reservationRepo.findById(reservationId)
                .filter(r -> r.getDeletedAt() == null)
                .filter(r -> ctx.ownsShared(r.getWorkspaceId()))
                .orElseThrow(() -> new IllegalArgumentException("reservation not found"));
        // 본인 예약 또는 OWNER만 취소 (OWNER override는 같은 workspace 한정 — ownsShared 선검증)
        if (!ctx.isOwner() && !reservation.getReservedBy().equals(ctx.userId())) {
            throw new IllegalStateException("can only cancel own reservation (OWNER override allowed)");
        }
        reservation.setDeletedAt(OffsetDateTime.now());
        reservationRepo.save(reservation);
    }

    @Transactional(readOnly = true)
    public List<EquipmentReservationEntity> listByEquipmentRange(
            UUID equipmentId, OffsetDateTime from, OffsetDateTime to) {
        WorkspaceContextHolder.require();
        return reservationRepo.findByEquipmentIdAndStartAtBetweenAndDeletedAtIsNull(equipmentId, from, to);
    }

    @Transactional(readOnly = true)
    public List<EquipmentReservationEntity> listMyReservations() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.userId() == null) {
            throw new IllegalStateException("PERSONAL scope required");
        }
        return reservationRepo.findByReservedByAndDeletedAtIsNullOrderByStartAtDesc(ctx.userId());
    }

    private WorkspaceContext requireWorkspaceWritable() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.workspaceId() == null) {
            throw new IllegalStateException("workspace scope required");
        }
        if (ctx.scope() == WorkspaceContext.Scope.PERSONAL && !ctx.canWrite()) {
            throw new IllegalStateException("VIEWER cannot reserve equipment");
        }
        return ctx;
    }
}
