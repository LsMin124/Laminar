package com.laminar.group.repository;

import com.laminar.group.domain.GroupRelationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 그룹 관계 Repository — Personal-First (@Filter 자동). */
public interface GroupRelationRepository extends JpaRepository<GroupRelationEntity, UUID> {

  List<GroupRelationEntity> findByBoardIdAndDeletedAtIsNull(UUID boardId);

  List<GroupRelationEntity> findByFromGroupIdAndDeletedAtIsNull(UUID fromGroupId);

  List<GroupRelationEntity> findByToGroupIdAndDeletedAtIsNull(UUID toGroupId);
}
