package com.laminar.samplemanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "sample_manager_api_keys")
@Filter(name = "subjectSharedFilter", condition = "subject_id = :ctxSubjectId")
@Getter
@Setter
public class SampleManagerApiKeyEntity {

  @Id
  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

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
    return subjectId != null && Objects.equals(subjectId, that.subjectId);
  }

  @Override
  public int hashCode() {
    return subjectId != null ? subjectId.hashCode() : getClass().hashCode();
  }
}
