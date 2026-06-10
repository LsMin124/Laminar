package com.laminar.sharedcalendar.repository;

import com.laminar.sharedcalendar.domain.SharedCalendarAnnouncementEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedCalendarAnnouncementRepository
    extends JpaRepository<SharedCalendarAnnouncementEntity, UUID> {

  List<SharedCalendarAnnouncementEntity>
      findBySharedCalendarIdAndStartAtBetweenAndDeletedAtIsNullOrderByStartAtAsc(
          UUID sharedCalendarId, OffsetDateTime from, OffsetDateTime to);

  List<SharedCalendarAnnouncementEntity> findBySharedCalendarIdAndDeletedAtIsNullOrderByStartAtDesc(
      UUID sharedCalendarId);
}
