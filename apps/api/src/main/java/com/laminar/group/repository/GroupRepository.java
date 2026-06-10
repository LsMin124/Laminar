package com.laminar.group.repository;

import com.laminar.common.repository.PersonalOwnedRepository;
import com.laminar.group.domain.GroupEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 그룹 Repository — Personal-First (subject_id + user_id 자동 필터). board별 priority 정렬이 hot path. */
public interface GroupRepository extends PersonalOwnedRepository<GroupEntity> {

  List<GroupEntity> findByTabIdAndDeletedAtIsNullOrderByPriorityAsc(UUID tabId);

  Optional<GroupEntity> findFirstByTabIdAndDeletedAtIsNullOrderByPriorityDesc(UUID tabId);
}
