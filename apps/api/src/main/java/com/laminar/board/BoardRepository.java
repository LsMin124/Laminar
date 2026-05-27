package com.laminar.board;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Board Repository (Personal-First — personalFirstFilter 자동 적용).
 *
 * HibernateFilterActivator가 workspace_id + user_id를 SQL WHERE에 자동 추가하므로
 * 메서드 시그니처는 도메인 의도만 표현. 격리 책임은 필터·룰·테스트 매트릭스.
 */
public interface BoardRepository extends JpaRepository<BoardEntity, UUID> {

    List<BoardEntity> findByDeletedAtIsNullOrderByPriorityAsc();

    Optional<BoardEntity> findBySlugAndDeletedAtIsNull(String slug);
}
