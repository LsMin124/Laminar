package com.laminar.perpetual;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * 영구노트 컬럼 값 Repository — junction (workspace_id 없음, parent 격리 의존).
 */
public interface PerpetualColumnRepository extends JpaRepository<PerpetualColumnEntity, PerpetualColumnId> {

    List<PerpetualColumnEntity> findByIdPerpetualNoteId(UUID perpetualNoteId);

    List<PerpetualColumnEntity> findByIdColumnDefinitionId(UUID columnDefinitionId);
}
