package com.laminar.tab.domain;

import com.laminar.common.domain.PersonalBaseEntity;
import com.laminar.common.domain.SoftDeletable;
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
    name = "tabs",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_boards_ws_user_slug",
            columnNames = {"subject_id", "user_id", "slug"}))
@Filter(
    name = "personalFirstFilter",
    condition = "subject_id = :ctxSubjectId and user_id = :ctxUserId")
@Getter
@Setter
public class TabEntity extends PersonalBaseEntity implements SoftDeletable {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "slug", nullable = false)
  private String slug;

  @Column(name = "default_view", nullable = false)
  private TabDefaultView defaultView = TabDefaultView.CALENDAR;

  /** 탭 종류 — DAG 캔버스 vs 화이트보드(자유 배치 노드). 기존 탭은 전부 DAG(V33 default 'dag'). */
  @Column(name = "kind", nullable = false)
  private TabKind kind = TabKind.DAG;

  @Column(name = "icon_name")
  private String iconName;

  @Column(name = "icon_color")
  private String iconColor;

  @Column(name = "body_md")
  private String bodyMd;

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
