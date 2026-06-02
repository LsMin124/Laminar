package com.laminar.card;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 카드 관계 Repository — Personal-First (@Filter 자동). */
public interface CardRelationRepository extends JpaRepository<CardRelationEntity, UUID> {

  List<CardRelationEntity> findByBoardIdAndDeletedAtIsNull(UUID boardId);

  List<CardRelationEntity> findByFromCardIdAndDeletedAtIsNull(UUID fromCardId);

  List<CardRelationEntity> findByToCardIdAndDeletedAtIsNull(UUID toCardId);
}
