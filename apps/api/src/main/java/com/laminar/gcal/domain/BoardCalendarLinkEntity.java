package com.laminar.gcal.domain;

import com.laminar.common.domain.PersonalBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(
    name = "board_calendar_links",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_bcl_board_user_gcal",
            columnNames = {"board_id", "user_id", "google_calendar_id"}))
@Filter(
    name = "personalFirstFilter",
    condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class BoardCalendarLinkEntity extends PersonalBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "board_id", nullable = false)
  private UUID boardId;

  @Column(name = "google_calendar_id", nullable = false)
  private String googleCalendarId;

  @Column(name = "sync_direction", nullable = false)
  private SyncDirection syncDirection = SyncDirection.TWO_WAY;

  @Column(name = "sync_token")
  private String syncToken;

  @Column(name = "last_sync_at")
  private OffsetDateTime lastSyncAt;

  @Column(name = "last_sync_error")
  private String lastSyncError;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
