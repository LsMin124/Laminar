package com.laminar.group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 그룹 Repository — Personal-First (workspace_id + user_id 자동 필터). board별 priority 정렬이 hot path. */
public interface GroupRepository extends JpaRepository<GroupEntity, UUID> {

  List<GroupEntity> findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(UUID boardId);

  Optional<GroupEntity> findFirstByBoardIdAndDeletedAtIsNullOrderByPriorityDesc(UUID boardId);
}
