package com.laminar.subject.domain;

import com.laminar.context.SubjectKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "subjects")
@Filter(name = "subjectSharedFilter", condition = "id = :ctxSubjectId")
@Getter
@Setter
public class SubjectEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "slug", nullable = false, unique = true)
  private String slug;

  @Column(name = "owner_user_id", nullable = false)
  private UUID ownerUserId;

  /** 주제 종별 — personal(기본) | lab(승격, LAB재설계 §1.1). 강등 미지원. */
  @Column(name = "kind", nullable = false)
  private SubjectKind kind = SubjectKind.PERSONAL;

  @Column(name = "default_timezone", nullable = false)
  private String defaultTimezone = "Asia/Seoul";

  @Column(name = "body_md")
  private String bodyMd;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "settings", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> settings = new HashMap<>();

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private OffsetDateTime updatedAt;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SubjectEntity that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : getClass().hashCode();
  }
}
