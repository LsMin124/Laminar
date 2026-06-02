package com.laminar.tab;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 탭 Repository — Personal-First (@Filter 자동). board별 priority 정렬·tree root 조회·max priority 조회가 hot
 * path.
 */
public interface TabRepository extends JpaRepository<TabEntity, UUID> {

  List<TabEntity> findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(UUID boardId);

  List<TabEntity> findByBoardIdAndParentTabIdIsNullAndDeletedAtIsNullOrderByPriorityAsc(
      UUID boardId);

  List<TabEntity> findByParentTabIdAndDeletedAtIsNullOrderByPriorityAsc(UUID parentTabId);

  Optional<TabEntity> findFirstByBoardIdAndDeletedAtIsNullOrderByPriorityDesc(UUID boardId);
}
