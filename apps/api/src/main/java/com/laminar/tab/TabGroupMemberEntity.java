package com.laminar.tab;

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

/** 탭-그룹 멤버십 (탭 멤버 = 그룹, 구상안 §3.3). group_members와 동형 — 부모 격리 의존. */
@Entity
@Table(name = "tab_groups")
@Getter
@Setter
public class TabGroupMemberEntity {

    @EmbeddedId
    private TabGroupMemberId id;

    @Column(name = "added_at", nullable = false, insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime addedAt;

    @Column(name = "added_by")
    private UUID addedBy;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TabGroupMemberEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
