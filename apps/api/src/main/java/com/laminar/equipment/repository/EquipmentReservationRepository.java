package com.laminar.equipment.repository;

import com.laminar.equipment.domain.EquipmentReservationEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 장비 예약 Repository — subject-shared (@Filter 자동).
 *
 * <p>overlap 쿼리는 service pre-check + DB EXCLUDE (V11 partial constraint). DB constraint는 rrule IS
 * NULL인 단일 예약만 적용 — RRULE은 인스턴스 확장 후 별도.
 */
public interface EquipmentReservationRepository
    extends JpaRepository<EquipmentReservationEntity, UUID> {

  @Query(
      """
            SELECT r FROM EquipmentReservationEntity r
            WHERE r.equipmentId = :equipmentId
              AND r.deletedAt IS NULL
              AND r.startAt < :endAt
              AND r.endAt > :startAt
            """)
  List<EquipmentReservationEntity> findOverlapping(
      @Param("equipmentId") UUID equipmentId,
      @Param("startAt") OffsetDateTime startAt,
      @Param("endAt") OffsetDateTime endAt);

  List<EquipmentReservationEntity> findByEquipmentIdAndStartAtBetweenAndDeletedAtIsNull(
      UUID equipmentId, OffsetDateTime from, OffsetDateTime to);

  List<EquipmentReservationEntity> findByReservedByAndDeletedAtIsNullOrderByStartAtDesc(
      UUID userId);
}
