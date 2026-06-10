package com.laminar.subject.domain;

import com.laminar.context.SubjectRole;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "subject_members")
@Filter(name = "subjectSharedFilter", condition = "subject_id = :ctxSubjectId")
@Getter
@Setter
public class SubjectMemberEntity {

  @EmbeddedId private SubjectMemberId id;

  @Column(name = "role", nullable = false)
  private SubjectRole role;

  @Column(name = "joined_at", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private OffsetDateTime joinedAt;

  @Column(name = "removed_at")
  private OffsetDateTime removedAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private OffsetDateTime updatedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SubjectMemberEntity that)) return false;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : getClass().hashCode();
  }
}
