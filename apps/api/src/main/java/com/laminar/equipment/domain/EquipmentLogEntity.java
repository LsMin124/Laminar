package com.laminar.equipment.domain;

import com.laminar.common.domain.SubjectScopedBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "equipment_logs")
@Filter(name = "ownerScopedFilter", condition = "logged_by = :ctxUserId")
@Getter
@Setter
public class EquipmentLogEntity extends SubjectScopedBaseEntity {

  @Column(name = "equipment_id", nullable = false)
  private UUID equipmentId;

  @Column(name = "logged_by", nullable = false)
  private UUID loggedBy;

  @Column(name = "logged_at", nullable = false)
  private OffsetDateTime loggedAt;

  @Column(name = "reservation_id")
  private UUID reservationId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "values", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> values = new HashMap<>();

  @Column(name = "notes")
  private String notes;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
