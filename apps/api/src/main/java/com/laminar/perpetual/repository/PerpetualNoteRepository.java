package com.laminar.perpetual.repository;

import com.laminar.perpetual.domain.PerpetualNoteEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 영구노트 Repository — Personal-First (@Filter 자동). tab별·tree root·children·max priority 조회. */
public interface PerpetualNoteRepository extends JpaRepository<PerpetualNoteEntity, UUID> {

  List<PerpetualNoteEntity> findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(UUID boardId);

  List<PerpetualNoteEntity> findByTabIdAndDeletedAtIsNullOrderByPriorityAsc(UUID tabId);

  List<PerpetualNoteEntity>
      findByTabIdAndParentPerpetualIdIsNullAndDeletedAtIsNullOrderByPriorityAsc(UUID tabId);

  List<PerpetualNoteEntity> findByParentPerpetualIdAndDeletedAtIsNullOrderByPriorityAsc(
      UUID parentId);

  Optional<PerpetualNoteEntity> findFirstByTabIdAndDeletedAtIsNullOrderByPriorityDesc(UUID tabId);
}
