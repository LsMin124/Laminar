package com.laminar.equipment.domain;

import com.laminar.common.domain.SubjectScopedBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "equipment_log_columns")
@Filter(name = "ownerScopedFilter", condition = "created_by = :ctxUserId")
@Getter
@Setter
public class EquipmentLogColumnEntity extends SubjectScopedBaseEntity {

  @Column(name = "equipment_id", nullable = false)
  private UUID equipmentId;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "column_key", nullable = false)
  private String columnKey;

  @Column(name = "column_label", nullable = false)
  private String columnLabel;

  @Column(name = "column_type", nullable = false)
  private EquipmentLogColumnType columnType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "enum_values", columnDefinition = "jsonb")
  private List<String> enumValues;

  @Column(name = "is_required", nullable = false)
  private boolean required;

  @Column(name = "priority", nullable = false)
  private int priority;

  @Column(name = "default_value")
  private String defaultValue;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
