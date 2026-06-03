package com.laminar.tab.repository;

import com.laminar.tab.domain.TabEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tab Repository (Personal-First — personalFirstFilter 자동 적용).
 *
 * <p>HibernateFilterActivator가 subject_id + user_id를 SQL WHERE에 자동 추가하므로 메서드 시그니처는 도메인 의도만 표현. 격리
 * 책임은 필터·룰·테스트 매트릭스.
 */
public interface TabRepository extends JpaRepository<TabEntity, UUID> {

  List<TabEntity> findByDeletedAtIsNullOrderByPriorityAsc();

  Optional<TabEntity> findBySlugAndDeletedAtIsNull(String slug);

  Optional<TabEntity> findFirstByDeletedAtIsNullOrderByPriorityDesc();
}
