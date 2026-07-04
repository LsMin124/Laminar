package com.laminar.whiteboard.repository;

import com.laminar.common.repository.PersonalOwnedRepository;
import com.laminar.whiteboard.domain.WhiteboardEdgeEntity;
import java.util.List;
import java.util.UUID;

/** 화이트보드 엣지 Repository — Personal-First (personalFirstFilter 자동 적용). */
public interface WhiteboardEdgeRepository extends PersonalOwnedRepository<WhiteboardEdgeEntity> {

  List<WhiteboardEdgeEntity> findByTabIdAndDeletedAtIsNull(UUID tabId);

  List<WhiteboardEdgeEntity> findByFromNodeIdAndDeletedAtIsNull(UUID fromNodeId);

  List<WhiteboardEdgeEntity> findByToNodeIdAndDeletedAtIsNull(UUID toNodeId);
}
