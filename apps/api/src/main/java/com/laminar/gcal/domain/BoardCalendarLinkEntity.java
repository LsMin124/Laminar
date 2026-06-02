package com.laminar.gcal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
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
public class BoardCalendarLinkEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

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

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BoardCalendarLinkEntity that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : getClass().hashCode();
  }
}
