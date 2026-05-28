package com.laminar.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 그룹 관계 Repository — Personal-First (@Filter 자동).
 */
public interface GroupRelationRepository extends JpaRepository<GroupRelationEntity, UUID> {

    List<GroupRelationEntity> findByBoardIdAndDeletedAtIsNull(UUID boardId);

    List<GroupRelationEntity> findByFromGroupIdAndDeletedAtIsNull(UUID fromGroupId);

    List<GroupRelationEntity> findByToGroupIdAndDeletedAtIsNull(UUID toGroupId);
}
