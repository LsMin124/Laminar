package com.laminar.gcal.repository;

import com.laminar.gcal.domain.CardEventLinkEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 카드 ↔ GCal event 1:1 매핑 Repository — subject-shared (@Filter 자동). board_calendar_link_id 별 event
 * 목록이 hot path.
 */
public interface CardEventLinkRepository extends JpaRepository<CardEventLinkEntity, UUID> {

  Optional<CardEventLinkEntity> findByTabCalendarLinkIdAndCardIdAndDeletedAtIsNull(
      UUID tabCalendarLinkId, UUID cardId);

  Optional<CardEventLinkEntity> findByTabCalendarLinkIdAndGoogleEventIdAndDeletedAtIsNull(
      UUID tabCalendarLinkId, String googleEventId);

  List<CardEventLinkEntity> findByTabCalendarLinkIdAndDeletedAtIsNull(UUID tabCalendarLinkId);
}
