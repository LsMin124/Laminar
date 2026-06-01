package com.laminar.samplemanager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sample_manager_api_keys")
@Filter(name = "workspaceSharedFilter", condition = "workspace_id = :ctxWorkspaceId")
@Getter
@Setter
public class SampleManagerApiKeyEntity {

    @Id
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime createdAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SampleManagerApiKeyEntity that)) return false;
        return workspaceId != null && Objects.equals(workspaceId, that.workspaceId);
    }

    @Override
    public int hashCode() {
        return workspaceId != null ? workspaceId.hashCode() : getClass().hashCode();
    }
}
