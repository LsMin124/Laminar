package com.laminar.card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cards")
public class CardEntity {

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

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getBoardId() { return boardId; }
    public void setBoardId(UUID boardId) { this.boardId = boardId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getBodyMd() { return bodyMd; }
    public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public boolean isAllDay() { return allDay; }
    public void setAllDay(boolean allDay) { this.allDay = allDay; }

    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }

    public CardImportance getImportance() { return importance; }
    public void setImportance(CardImportance importance) { this.importance = importance; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public UUID getLinkedPerpetualId() { return linkedPerpetualId; }
    public void setLinkedPerpetualId(UUID linkedPerpetualId) { this.linkedPerpetualId = linkedPerpetualId; }

    public String getRrule() { return rrule; }
    public void setRrule(String rrule) { this.rrule = rrule; }

    public CardOrigin getOrigin() { return origin; }
    public void setOrigin(CardOrigin origin) { this.origin = origin; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public Map<String, Object> getAttrs() { return attrs; }
    public void setAttrs(Map<String, Object> attrs) { this.attrs = attrs; }

    public OffsetDateTime getArchivedAt() { return archivedAt; }
    public void setArchivedAt(OffsetDateTime archivedAt) { this.archivedAt = archivedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CardEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
