package com.laminar.equipment.domain;

import com.laminar.common.domain.SubjectScopedBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "equipment")
@Filter(name = "ownerScopedFilter", condition = "created_by = :ctxUserId")
@Getter
@Setter
public class EquipmentEntity extends SubjectScopedBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "description")
  private String description;

  @Column(name = "location")
  private String location;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "default_log_columns", nullable = false, columnDefinition = "jsonb")
  private List<Map<String, Object>> defaultLogColumns = new ArrayList<>();

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
