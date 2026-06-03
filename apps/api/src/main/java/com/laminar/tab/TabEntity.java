package com.laminar.tab;

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
@Table(name = "tabs")
@Filter(
    name = "personalFirstFilter",
    condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class TabEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "board_id", nullable = false)
  private UUID boardId;

  @Column(name = "parent_tab_id")
  private UUID parentTabId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "priority", nullable = false)
  private int priority;

  @Column(name = "is_visible", nullable = false)
  private boolean visible = true;

  @Column(name = "is_collapsed", nullable = false)
  private boolean collapsed;

  @Column(name = "show_label", nullable = false)
  private boolean showLabel;

  @Column(name = "label_color")
  private String labelColor;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attrs = new HashMap<>();

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
