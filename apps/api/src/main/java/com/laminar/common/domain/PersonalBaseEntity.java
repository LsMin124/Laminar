package com.laminar.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * 워크스페이스 + 개인(Personal-First) 단위로 격리되는 엔티티의 베이스.
 *
 * <p>{@code subject_id} + {@code user_id} 복합으로 소유권을 가지며 {@code personalFirstFilter} Hibernate 필터의
 * 대상이 된다.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class PersonalBaseEntity extends SubjectScopedBaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;
}
