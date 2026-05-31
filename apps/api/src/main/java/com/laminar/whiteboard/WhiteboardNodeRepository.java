package com.laminar.whiteboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WhiteboardNodeRepository extends JpaRepository<WhiteboardNodeEntity, UUID> {
    List<WhiteboardNodeEntity> findByBoardId(UUID boardId);
}
