package com.laminar.whiteboard.repository;

import com.laminar.whiteboard.domain.WhiteboardNodeEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhiteboardNodeRepository extends JpaRepository<WhiteboardNodeEntity, UUID> {
  List<WhiteboardNodeEntity> findByBoardId(UUID boardId);
}
