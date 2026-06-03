package com.laminar.perpetual.repository;

import com.laminar.perpetual.domain.PerpetualColumnEntity;
import com.laminar.perpetual.domain.PerpetualColumnId;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 영구노트 컬럼 값 Repository — junction (workspace_id 없음, parent 격리 의존). */
public interface PerpetualColumnRepository
    extends JpaRepository<PerpetualColumnEntity, PerpetualColumnId> {

  List<PerpetualColumnEntity> findByIdPerpetualNoteId(UUID perpetualNoteId);

  List<PerpetualColumnEntity> findByIdColumnDefinitionId(UUID columnDefinitionId);
}
