package com.laminar.equipment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SharedCalendarAnnouncementRepository
        extends JpaRepository<SharedCalendarAnnouncementEntity, UUID> {

    List<SharedCalendarAnnouncementEntity>
            findBySharedCalendarIdAndStartAtBetweenAndDeletedAtIsNullOrderByStartAtAsc(
                    UUID sharedCalendarId, OffsetDateTime from, OffsetDateTime to);

    List<SharedCalendarAnnouncementEntity> findBySharedCalendarIdAndDeletedAtIsNullOrderByStartAtDesc(
            UUID sharedCalendarId);
}
