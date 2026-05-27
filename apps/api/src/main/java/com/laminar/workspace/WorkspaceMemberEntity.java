package com.laminar.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(name = "workspace_members")
public class WorkspaceMemberEntity {

    @EmbeddedId
    private WorkspaceMemberId id;

    @Column(name = "role", nullable = false)
    private WorkspaceRole role;

    @Column(name = "joined_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public WorkspaceMemberId getId() { return id; }
    public void setId(WorkspaceMemberId id) { this.id = id; }

    public WorkspaceRole getRole() { return role; }
    public void setRole(WorkspaceRole role) { this.role = role; }

    public OffsetDateTime getJoinedAt() { return joinedAt; }

    public OffsetDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(OffsetDateTime removedAt) { this.removedAt = removedAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkspaceMemberEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
