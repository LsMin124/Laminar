package com.laminar.tab;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tab_members")
public class TabMemberEntity {

    @EmbeddedId
    private TabMemberId id;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "added_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime addedAt;

    @Column(name = "added_by")
    private UUID addedBy;

    public TabMemberId getId() { return id; }
    public void setId(TabMemberId id) { this.id = id; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public OffsetDateTime getAddedAt() { return addedAt; }

    public UUID getAddedBy() { return addedBy; }
    public void setAddedBy(UUID addedBy) { this.addedBy = addedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TabMemberEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
