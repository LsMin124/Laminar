package com.laminar.tab;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** 탭-그룹 멤버십 복합키 (탭 멤버 = 그룹, 구상안 §3.3). */
@Embeddable
public class TabGroupMemberId implements Serializable {

    @Column(name = "tab_id", nullable = false)
    private UUID tabId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    public TabGroupMemberId() {
    }

    public TabGroupMemberId(UUID tabId, UUID groupId) {
        this.tabId = tabId;
        this.groupId = groupId;
    }

    public UUID getTabId() { return tabId; }
    public void setTabId(UUID tabId) { this.tabId = tabId; }

    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TabGroupMemberId that)) return false;
        return Objects.equals(tabId, that.tabId) && Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tabId, groupId);
    }
}
