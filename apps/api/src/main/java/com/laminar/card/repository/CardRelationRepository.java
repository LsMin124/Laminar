package com.laminar.card.repository;

import com.laminar.card.domain.CardRelationEntity;
import com.laminar.common.repository.PersonalOwnedRepository;
import java.util.List;
import java.util.UUID;

/** 카드 관계 Repository — Personal-First (@Filter 자동). */
public interface CardRelationRepository extends PersonalOwnedRepository<CardRelationEntity> {

  List<CardRelationEntity> findByTabIdAndDeletedAtIsNull(UUID tabId);

  List<CardRelationEntity> findByFromCardIdAndDeletedAtIsNull(UUID fromCardId);

  List<CardRelationEntity> findByToCardIdAndDeletedAtIsNull(UUID toCardId);
}
