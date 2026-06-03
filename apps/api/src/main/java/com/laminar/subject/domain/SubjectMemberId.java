package com.laminar.subject.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SubjectMemberId implements Serializable {

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  public SubjectMemberId() {}

  public SubjectMemberId(UUID subjectId, UUID userId) {
    this.subjectId = subjectId;
    this.userId = userId;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public void setSubjectId(UUID subjectId) {
    this.subjectId = subjectId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SubjectMemberId that)) return false;
    return Objects.equals(subjectId, that.subjectId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subjectId, userId);
  }
}
