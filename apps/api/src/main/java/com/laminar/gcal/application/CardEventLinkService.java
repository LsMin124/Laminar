package com.laminar.gcal.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.gcal.domain.CardEventLinkEntity;
import com.laminar.gcal.repository.CardEventLinkRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 ↔ GCal event 1:1 매핑 + ETag·hash 추적.
 *
 * <p>(board_calendar_link_id, card_id) unique + (link_id, google_event_id) unique — 양방향 매핑.
 * last_pushed_hash로 conflict 검출 (pull 후 변경 감지).
 */
@Service
public class CardEventLinkService {

  private final CardEventLinkRepository eventLinkRepo;

  public CardEventLinkService(CardEventLinkRepository eventLinkRepo) {
    this.eventLinkRepo = eventLinkRepo;
  }

  @Transactional
  public CardEventLinkEntity linkOrUpdate(
      UUID tabCalendarLinkId,
      UUID cardId,
      String googleEventId,
      String etag,
      String lastPushedHash) {
    SubjectContext ctx = requireSubjectWritable();

    CardEventLinkEntity link =
        eventLinkRepo
            .findByTabCalendarLinkIdAndCardIdAndDeletedAtIsNull(tabCalendarLinkId, cardId)
            .orElseGet(
                () -> {
                  CardEventLinkEntity fresh = new CardEventLinkEntity();
                  fresh.setSubjectId(ctx.subjectId());
                  fresh.setTabCalendarLinkId(tabCalendarLinkId);
                  fresh.setCardId(cardId);
                  return fresh;
                });
    link.setGoogleEventId(googleEventId);
    link.setEtag(etag);
    link.setLastPushedHash(lastPushedHash);
    link.setLastSyncedAt(OffsetDateTime.now());
    return eventLinkRepo.save(link);
  }

  @Transactional(readOnly = true)
  public Optional<CardEventLinkEntity> findByCard(UUID tabCalendarLinkId, UUID cardId) {
    SubjectContextHolder.requirePersonal();
    return eventLinkRepo.findByTabCalendarLinkIdAndCardIdAndDeletedAtIsNull(
        tabCalendarLinkId, cardId);
  }

  @Transactional(readOnly = true)
  public Optional<CardEventLinkEntity> findByGoogleEventId(
      UUID tabCalendarLinkId, String googleEventId) {
    SubjectContextHolder.requirePersonal();
    return eventLinkRepo.findByTabCalendarLinkIdAndGoogleEventIdAndDeletedAtIsNull(
        tabCalendarLinkId, googleEventId);
  }

  @Transactional(readOnly = true)
  public List<CardEventLinkEntity> listByLink(UUID tabCalendarLinkId) {
    SubjectContextHolder.requirePersonal();
    return eventLinkRepo.findByTabCalendarLinkIdAndDeletedAtIsNull(tabCalendarLinkId);
  }

  @Transactional
  public void softDelete(UUID linkId) {
    SubjectContext ctx = requireSubjectWritable();
    eventLinkRepo
        .findById(linkId)
        .filter(l -> l.getDeletedAt() == null)
        .filter(l -> ctx.ownsShared(l.getSubjectId()))
        .ifPresent(
            l -> {
              l.setDeletedAt(OffsetDateTime.now());
              eventLinkRepo.save(l);
            });
  }

  private SubjectContext requireSubjectWritable() {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.subjectId() == null) {
      throw new IllegalStateException("subject scope required");
    }
    if (ctx.scope() == SubjectContext.Scope.PERSONAL && !ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate event links");
    }
    return ctx;
  }
}
