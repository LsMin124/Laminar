package com.laminar.perpetual;

import com.laminar.common.domain.PersonalBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "perpetual_notes")
@Filter(
    name = "personalFirstFilter",
    condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class PerpetualNoteEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "board_id")
  private UUID boardId;

  @Column(name = "tab_id")
  private UUID tabId;

  @Column(name = "parent_perpetual_id")
  private UUID parentPerpetualId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "body_md")
  private String bodyMd;

  @Column(name = "priority", nullable = false)
  private int priority;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attrs = new HashMap<>();

  @jakarta.persistence.Version
  @Column(name = "version", nullable = false)
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private long version;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
