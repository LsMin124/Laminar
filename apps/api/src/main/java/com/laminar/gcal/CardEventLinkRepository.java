package com.laminar.gcal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 카드 ↔ GCal event 1:1 매핑 Repository — workspace-shared (@Filter 자동). board_calendar_link_id 별 event
 * 목록이 hot path.
 */
public interface CardEventLinkRepository extends JpaRepository<CardEventLinkEntity, UUID> {

  Optional<CardEventLinkEntity> findByBoardCalendarLinkIdAndCardIdAndDeletedAtIsNull(
      UUID boardCalendarLinkId, UUID cardId);

  Optional<CardEventLinkEntity> findByBoardCalendarLinkIdAndGoogleEventIdAndDeletedAtIsNull(
      UUID boardCalendarLinkId, String googleEventId);

  List<CardEventLinkEntity> findByBoardCalendarLinkIdAndDeletedAtIsNull(UUID boardCalendarLinkId);
}
