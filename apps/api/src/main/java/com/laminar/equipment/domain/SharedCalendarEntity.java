package com.laminar.equipment.domain;

import com.laminar.board.BoardDefaultView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "shared_calendars")
@Filter(name = "workspaceSharedFilter", condition = "workspace_id = :ctxWorkspaceId")
@Getter
@Setter
public class SharedCalendarEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "equipment_id")
  private UUID equipmentId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "color")
  private String color;

  @Column(name = "default_view")
  private BoardDefaultView defaultView;

  @Column(name = "is_announcement_only", nullable = false)
  private boolean announcementOnly;

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
    if (!(o instanceof SharedCalendarEntity that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : getClass().hashCode();
  }
}
