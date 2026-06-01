package com.laminar.datememo;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "date_memos")
@Filter(name = "personalFirstFilter",
        condition = "workspace_id = :ctxWorkspaceId and user_id = :ctxUserId")
@Getter
@Setter
public class DateMemoEntity {

    @EmbeddedId
    private DateMemoId id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "body_md")
    private String bodyMd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attrs", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attrs = new HashMap<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DateMemoEntity that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : getClass().hashCode();
    }
}
