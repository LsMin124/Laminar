package com.laminar.equipment.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.equipment.domain.EquipmentEntity;
import com.laminar.equipment.domain.EquipmentReservationEntity;
import com.laminar.equipment.repository.EquipmentRepository;
import com.laminar.equipment.repository.EquipmentReservationRepository;
import com.laminar.error.ConflictException;
import com.laminar.error.ForbiddenException;
import com.laminar.error.NotFoundException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장비 예약 — LAB 스코프 (L3), 시간 겹침 차단.
 *
 * <p>§1.3 매트릭스: 예약은 lab 멤버 전원(MEMBER+), 취소는 본인 또는 ADMIN+. 검증 (V11 §2.10.6 + DB EXCLUDE): start_at <
 * end_at, 길이 ≤ 7일(chk_er_max_span), 같은 equipment+시간대 겹침 차단(단일 예약, rrule IS NULL에 한정). RRULE 반복 예약은
 * DB EXCLUDE 미적용 — service에서 인스턴스 확장 후 개별 검증 책임.
 */
@Service
public class EquipmentReservationService {

  private static final Duration MAX_SPAN = Duration.ofDays(7);

  private final EquipmentReservationRepository reservationRepo;
  private final EquipmentRepository equipmentRepo;

  public EquipmentReservationService(
      EquipmentReservationRepository reservationRepo, EquipmentRepository equipmentRepo) {
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
    SubjectContext ctx = SubjectContextHolder.requireLabMember("equipment reservations");
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
    equipmentRepo
        .findById(equipmentId)
        .filter(e -> e.getDeletedAt() == null)
        .filter(e -> ctx.ownsShared(e.getSubjectId()))
        .filter(EquipmentEntity::isActive)
        .orElseThrow(() -> new NotFoundException("equipment not found or inactive"));

    // 단일 예약 (rrule null)만 겹침 사전 검증 — DB EXCLUDE도 같은 정책.
    if (rrule == null || rrule.isBlank()) {
      List<EquipmentReservationEntity> overlapping =
          reservationRepo.findOverlapping(equipmentId, startAt, endAt).stream()
              .filter(r -> r.getRrule() == null || r.getRrule().isBlank())
              .toList();
      if (!overlapping.isEmpty()) {
        throw new ConflictException("reservation overlaps with existing booking");
      }
    }

    EquipmentReservationEntity reservation = new EquipmentReservationEntity();
    reservation.setSubjectId(ctx.subjectId());
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
    SubjectContext ctx = SubjectContextHolder.requireLabMember("equipment reservations");
    EquipmentReservationEntity reservation =
        reservationRepo
            .findById(reservationId)
            .filter(r -> r.getDeletedAt() == null)
            .filter(r -> ctx.ownsShared(r.getSubjectId()))
            .orElseThrow(() -> new NotFoundException("reservation not found"));
    // 본인 예약 또는 ADMIN+만 취소 (§1.3 — 타인 예약 정리는 관리자 권한)
    if (!ctx.isAdmin() && !reservation.getReservedBy().equals(ctx.userId())) {
      throw new ForbiddenException("can only cancel own reservation (ADMIN override allowed)");
    }
    reservation.setDeletedAt(OffsetDateTime.now());
    reservationRepo.save(reservation);
  }

  @Transactional(readOnly = true)
  public List<EquipmentReservationEntity> listByEquipmentRange(
      UUID equipmentId, OffsetDateTime from, OffsetDateTime to) {
    SubjectContextHolder.requireLabMember("equipment reservations");
    return reservationRepo.findByEquipmentIdAndStartAtBetweenAndDeletedAtIsNull(
        equipmentId, from, to);
  }

  /** 내 예약 — 현재 lab 내 본인 예약(컨텍스트 필터가 lab을, reservedBy가 본인을 한정). */
  @Transactional(readOnly = true)
  public List<EquipmentReservationEntity> listMyReservations() {
    SubjectContext ctx = SubjectContextHolder.requireLabMember("equipment reservations");
    return reservationRepo.findByReservedByAndDeletedAtIsNullOrderByStartAtDesc(ctx.userId());
  }
}
