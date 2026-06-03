package com.laminar.board;

import com.laminar.common.domain.PersonalBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
    name = "boards",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_boards_ws_user_slug",
            columnNames = {"workspace_id", "user_id", "slug"}))
@Filter(
    name = "personalFirstFilter",
    condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class BoardEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "slug", nullable = false)
  private String slug;

  @Column(name = "default_view", nullable = false)
  private BoardDefaultView defaultView = BoardDefaultView.CALENDAR;

  @Column(name = "icon_name")
  private String iconName;

  @Column(name = "icon_color")
  private String iconColor;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "settings", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> settings = new HashMap<>();

  @Column(name = "priority", nullable = false)
  private int priority;

  @jakarta.persistence.Version
  @Column(name = "version", nullable = false)
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private long version;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
