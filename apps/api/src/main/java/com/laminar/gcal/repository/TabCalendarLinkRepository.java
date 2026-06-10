package com.laminar.gcal.repository;

import com.laminar.common.repository.PersonalOwnedRepository;
import com.laminar.gcal.domain.TabCalendarLinkEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** GCal 보드 ↔ 캘린더 매핑 Repository — Personal-First (@Filter 자동). */
public interface TabCalendarLinkRepository extends PersonalOwnedRepository<TabCalendarLinkEntity> {

  List<TabCalendarLinkEntity> findByTabIdAndDeletedAtIsNull(UUID tabId);

  Optional<TabCalendarLinkEntity> findByTabIdAndGoogleCalendarIdAndDeletedAtIsNull(
      UUID tabId, String googleCalendarId);

  List<TabCalendarLinkEntity> findByActiveTrueAndDeletedAtIsNull();
}
