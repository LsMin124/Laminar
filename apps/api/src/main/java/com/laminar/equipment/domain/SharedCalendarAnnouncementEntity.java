package com.laminar.equipment.domain;

import com.laminar.common.domain.WorkspaceScopedBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "shared_calendar_announcements")
@Filter(name = "workspaceSharedFilter", condition = "workspace_id = :ctxWorkspaceId")
@Getter
@Setter
public class SharedCalendarAnnouncementEntity extends WorkspaceScopedBaseEntity {

  @Column(name = "shared_calendar_id", nullable = false)
  private UUID sharedCalendarId;

  @Column(name = "posted_by", nullable = false)
  private UUID postedBy;

  @Column(name = "start_at", nullable = false)
  private OffsetDateTime startAt;

  @Column(name = "end_at")
  private OffsetDateTime endAt;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "body_md")
  private String bodyMd;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
