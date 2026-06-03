package com.laminar.perpetual.domain;

import com.laminar.common.domain.PersonalBaseEntity;
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
@Table(name = "perpetual_column_definitions")
@Filter(
    name = "personalFirstFilter",
    condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class PerpetualColumnDefinitionEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "board_id", nullable = false)
  private UUID boardId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "type", nullable = false)
  private PerpetualColumnType type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "enum_values", columnDefinition = "jsonb")
  private List<String> enumValues;

  @Column(name = "priority", nullable = false)
  private int priority;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
