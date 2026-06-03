package com.laminar.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

/**
 * 모든 영속 엔티티의 공통 식별자·감사 시각 베이스.
 *
 * <p>스키마 변경은 Flyway 단독 책임이며, {@code created_at}/{@code updated_at}은 DB 기본값·트리거가 채우므로 {@code
 * insertable=false, updatable=false}로 읽기 전용 매핑한다(원본 엔티티 매핑과 동일).
 *
 * <p>{@code equals}/{@code hashCode}는 식별자 기반이며 Hibernate 프록시를 고려해 {@link Hibernate#getClass}로 실클래스를
 * 비교한다.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  @Setter(AccessLevel.NONE)
  private OffsetDateTime updatedAt;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
    BaseEntity that = (BaseEntity) o;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : getClass().hashCode();
  }
}
