package com.laminar.tab;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 탭 관계 Repository — Personal-First (@Filter 자동). */
public interface TabRelationRepository extends JpaRepository<TabRelationEntity, UUID> {

  List<TabRelationEntity> findByBoardIdAndDeletedAtIsNull(UUID boardId);

  List<TabRelationEntity> findByFromTabIdAndDeletedAtIsNull(UUID fromTabId);

  List<TabRelationEntity> findByToTabIdAndDeletedAtIsNull(UUID toTabId);
}
