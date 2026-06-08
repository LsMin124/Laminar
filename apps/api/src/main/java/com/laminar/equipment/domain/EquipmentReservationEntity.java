package com.laminar.equipment.domain;

import com.laminar.common.domain.SubjectScopedBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "equipment_reservations")
@Filter(name = "ownerScopedFilter", condition = "reserved_by = :ctxUserId")
@Getter
@Setter
public class EquipmentReservationEntity extends SubjectScopedBaseEntity {

  @Column(name = "equipment_id", nullable = false)
  private UUID equipmentId;

  @Column(name = "reserved_by", nullable = false)
  private UUID reservedBy;

  @Column(name = "start_at", nullable = false)
  private OffsetDateTime startAt;

  @Column(name = "end_at", nullable = false)
  private OffsetDateTime endAt;

  @Column(name = "purpose")
  private String purpose;

  @Column(name = "rrule")
  private String rrule;

  @Column(name = "card_id")
  private UUID cardId;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
