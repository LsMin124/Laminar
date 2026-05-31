package com.laminar.whiteboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WhiteboardEdgeRepository extends JpaRepository<WhiteboardEdgeEntity, UUID> {
    List<WhiteboardEdgeEntity> findByBoardId(UUID boardId);
}
