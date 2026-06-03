package com.laminar.group.domain;

import com.laminar.common.domain.PersonalBaseEntity;
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
@Table(name = "groups")
@Filter(
    name = "personalFirstFilter",
    condition = "subject_id = :ctxSubjectId and user_id = :ctxUserId")
@Getter
@Setter
public class GroupEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "tab_id", nullable = false)
  private UUID tabId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "color")
  private String color;

  @Column(name = "priority", nullable = false)
  private int priority;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attrs = new HashMap<>();

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
