package com.laminar.whiteboard.repository;

import com.laminar.common.repository.PersonalOwnedRepository;
import com.laminar.whiteboard.domain.WhiteboardNodeEntity;
import java.util.List;
import java.util.UUID;

/** 화이트보드 노드 Repository — Personal-First (personalFirstFilter 자동 적용). */
public interface WhiteboardNodeRepository extends PersonalOwnedRepository<WhiteboardNodeEntity> {

  List<WhiteboardNodeEntity> findByTabIdAndDeletedAtIsNull(UUID tabId);
}
