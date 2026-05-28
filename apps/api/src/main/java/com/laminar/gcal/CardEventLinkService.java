package com.laminar.gcal;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 카드 ↔ GCal event 1:1 매핑 + ETag·hash 추적.
 *
 * (board_calendar_link_id, card_id) unique + (link_id, google_event_id) unique — 양방향 매핑.
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
            UUID boardCalendarLinkId,
            UUID cardId,
            String googleEventId,
            String etag,
            String lastPushedHash) {
        WorkspaceContext ctx = requireWorkspaceWritable();

        CardEventLinkEntity link = eventLinkRepo
                .findByBoardCalendarLinkIdAndCardIdAndDeletedAtIsNull(boardCalendarLinkId, cardId)
                .orElseGet(() -> {
                    CardEventLinkEntity fresh = new CardEventLinkEntity();
                    fresh.setWorkspaceId(ctx.workspaceId());
                    fresh.setBoardCalendarLinkId(boardCalendarLinkId);
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
    public Optional<CardEventLinkEntity> findByCard(UUID boardCalendarLinkId, UUID cardId) {
        WorkspaceContextHolder.requirePersonal();
        return eventLinkRepo.findByBoardCalendarLinkIdAndCardIdAndDeletedAtIsNull(boardCalendarLinkId, cardId);
    }

    @Transactional(readOnly = true)
    public Optional<CardEventLinkEntity> findByGoogleEventId(UUID boardCalendarLinkId, String googleEventId) {
        WorkspaceContextHolder.requirePersonal();
        return eventLinkRepo.findByBoardCalendarLinkIdAndGoogleEventIdAndDeletedAtIsNull(
                boardCalendarLinkId, googleEventId);
    }

    @Transactional(readOnly = true)
    public List<CardEventLinkEntity> listByLink(UUID boardCalendarLinkId) {
        WorkspaceContextHolder.requirePersonal();
        return eventLinkRepo.findByBoardCalendarLinkIdAndDeletedAtIsNull(boardCalendarLinkId);
    }

    @Transactional
    public void softDelete(UUID linkId) {
        WorkspaceContext ctx = requireWorkspaceWritable();
        eventLinkRepo.findById(linkId)
                .filter(l -> l.getDeletedAt() == null)
                .filter(l -> ctx.ownsShared(l.getWorkspaceId()))
                .ifPresent(l -> {
                    l.setDeletedAt(OffsetDateTime.now());
                    eventLinkRepo.save(l);
                });
    }

    private WorkspaceContext requireWorkspaceWritable() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.workspaceId() == null) {
            throw new IllegalStateException("workspace scope required");
        }
        if (ctx.scope() == WorkspaceContext.Scope.PERSONAL && !ctx.canWrite()) {
            throw new IllegalStateException("VIEWER cannot mutate event links");
        }
        return ctx;
    }
}
