package com.laminar.group;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "group_members")
@Getter
@Setter
public class GroupMemberEntity {

    @EmbeddedId
    private GroupMemberId id;

    @Column(name = "added_at", nullable = false, insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime addedAt;

    @Column(name = "added_by")
    private UUID addedBy;

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
