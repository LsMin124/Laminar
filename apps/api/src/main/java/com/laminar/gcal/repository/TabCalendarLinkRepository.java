package com.laminar.gcal.repository;

import com.laminar.gcal.domain.TabCalendarLinkEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** GCal 보드 ↔ 캘린더 매핑 Repository — Personal-First (@Filter 자동). */
public interface TabCalendarLinkRepository extends JpaRepository<TabCalendarLinkEntity, UUID> {

  List<TabCalendarLinkEntity> findByTabIdAndDeletedAtIsNull(UUID tabId);

  Optional<TabCalendarLinkEntity> findByTabIdAndGoogleCalendarIdAndDeletedAtIsNull(
      UUID tabId, String googleCalendarId);

  List<TabCalendarLinkEntity> findByActiveTrueAndDeletedAtIsNull();
}
