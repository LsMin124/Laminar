package com.laminar.perpetual.repository;

import com.laminar.perpetual.domain.PerpetualColumnDefinitionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 영구노트 컬럼 정의 Repository — Personal-First. board 단위 시트형 컬럼 (board+user+name unique). */
public interface PerpetualColumnDefinitionRepository
    extends JpaRepository<PerpetualColumnDefinitionEntity, UUID> {

  List<PerpetualColumnDefinitionEntity> findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(
      UUID boardId);

  Optional<PerpetualColumnDefinitionEntity> findFirstByBoardIdAndDeletedAtIsNullOrderByPriorityDesc(
      UUID boardId);

  Optional<PerpetualColumnDefinitionEntity> findByBoardIdAndNameAndDeletedAtIsNull(
      UUID boardId, String name);
}
