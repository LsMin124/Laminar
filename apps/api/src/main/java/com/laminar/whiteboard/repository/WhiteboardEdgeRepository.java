package com.laminar.whiteboard.repository;

import com.laminar.whiteboard.domain.WhiteboardEdgeEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhiteboardEdgeRepository extends JpaRepository<WhiteboardEdgeEntity, UUID> {
  List<WhiteboardEdgeEntity> findByBoardId(UUID boardId);
}
