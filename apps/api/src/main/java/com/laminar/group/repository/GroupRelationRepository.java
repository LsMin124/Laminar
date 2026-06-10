package com.laminar.group.repository;

import com.laminar.common.repository.PersonalOwnedRepository;
import com.laminar.group.domain.GroupRelationEntity;
import java.util.List;
import java.util.UUID;

/** 그룹 관계 Repository — Personal-First (@Filter 자동). */
public interface GroupRelationRepository extends PersonalOwnedRepository<GroupRelationEntity> {

  List<GroupRelationEntity> findByTabIdAndDeletedAtIsNull(UUID tabId);

  List<GroupRelationEntity> findByFromGroupIdAndDeletedAtIsNull(UUID fromGroupId);

  List<GroupRelationEntity> findByToGroupIdAndDeletedAtIsNull(UUID toGroupId);
}
