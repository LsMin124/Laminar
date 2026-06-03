package com.laminar.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * 워크스페이스 단위로 격리되는 엔티티의 베이스 (SUBJECT_SHARED 계층).
 *
 * <p>{@code subject_id}로 테넌트를 구분하며 {@code subjectSharedFilter} Hibernate 필터의 대상이 된다.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class SubjectScopedBaseEntity extends BaseEntity {

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;
}
