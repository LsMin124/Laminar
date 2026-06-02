package com.laminar.gcal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** GCal 보드 ↔ 캘린더 매핑 Repository — Personal-First (@Filter 자동). */
public interface BoardCalendarLinkRepository extends JpaRepository<BoardCalendarLinkEntity, UUID> {

  List<BoardCalendarLinkEntity> findByBoardIdAndDeletedAtIsNull(UUID boardId);

  Optional<BoardCalendarLinkEntity> findByBoardIdAndGoogleCalendarIdAndDeletedAtIsNull(
      UUID boardId, String googleCalendarId);

  List<BoardCalendarLinkEntity> findByActiveTrueAndDeletedAtIsNull();
}
