package com.laminar.card.domain;

import com.laminar.common.domain.PersonalBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
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
@Table(name = "cards")
@Filter(
    name = "personalFirstFilter",
    condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class CardEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "board_id")
  private UUID boardId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "slug")
  private String slug;

  @Column(name = "body_md")
  private String bodyMd;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "start_time")
  private LocalTime startTime;

  @Column(name = "is_all_day", nullable = false)
  private boolean allDay = true;

  @Column(name = "time_zone")
  private String timeZone;

  @Column(name = "importance", nullable = false)
  private CardImportance importance = CardImportance.NORMAL;

  @Column(name = "is_completed", nullable = false)
  private boolean completed;

  @Column(name = "linked_perpetual_id")
  private UUID linkedPerpetualId;

  @Column(name = "rrule")
  private String rrule;

  @Column(name = "origin", nullable = false)
  private CardOrigin origin = CardOrigin.MANUAL;

  @Column(name = "priority", nullable = false)
  private int priority;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> attrs = new HashMap<>();

  @Column(name = "archived_at")
  private OffsetDateTime archivedAt;

  @jakarta.persistence.Version
  @Column(name = "version", nullable = false)
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private long version;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
