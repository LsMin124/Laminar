package com.laminar.group;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "group_members")
public class GroupMemberEntity {

    @EmbeddedId
    private GroupMemberId id;

    @Column(name = "added_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime addedAt;

    @Column(name = "added_by")
    private UUID addedBy;

    public GroupMemberId getId() { return id; }
    public void setId(GroupMemberId id) { this.id = id; }

    public OffsetDateTime getAddedAt() { return addedAt; }

    public UUID getAddedBy() { return addedBy; }
    public void setAddedBy(UUID addedBy) { this.addedBy = addedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupMemberEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
