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
 * BoardCalendarLink CRUD + sync state 관리.
 *
 * Spec §3.6: sync_direction=push/pull/two-way + sync_token (incremental sync).
 * 실제 GCal API 호출은 Phase 11 cron에서 (이 서비스는 메타 + 상태만).
 */
@Service
public class BoardCalendarLinkService {

    private final BoardCalendarLinkRepository linkRepo;

    public BoardCalendarLinkService(BoardCalendarLinkRepository linkRepo) {
        this.linkRepo = linkRepo;
    }

    @Transactional
    public BoardCalendarLinkEntity link(UUID boardId, String googleCalendarId, SyncDirection direction) {
        WorkspaceContext ctx = requirePersonalWritable();
        // 이미 있으면 reactivate
        Optional<BoardCalendarLinkEntity> existing = linkRepo
                .findByBoardIdAndGoogleCalendarIdAndDeletedAtIsNull(boardId, googleCalendarId);
        if (existing.isPresent()) {
            BoardCalendarLinkEntity link = existing.get();
            link.setActive(true);
            link.setSyncDirection(direction == null ? SyncDirection.TWO_WAY : direction);
            return linkRepo.save(link);
        }

        BoardCalendarLinkEntity link = new BoardCalendarLinkEntity();
        link.setWorkspaceId(ctx.workspaceId());
        link.setUserId(ctx.userId());
        link.setCreatedBy(ctx.userId());
        link.setBoardId(boardId);
        link.setGoogleCalendarId(googleCalendarId);
        link.setSyncDirection(direction == null ? SyncDirection.TWO_WAY : direction);
        link.setActive(true);
        return linkRepo.save(link);
    }

    @Transactional
    public BoardCalendarLinkEntity markSynced(UUID linkId, String syncToken) {
        requirePersonalWritable();
        BoardCalendarLinkEntity link = linkRepo.findById(linkId)
                .filter(l -> l.getDeletedAt() == null)
                .filter(l -> WorkspaceContextHolder.require().ownsPersonal(l.getWorkspaceId(), l.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("link not found"));
        link.setSyncToken(syncToken);
        link.setLastSyncAt(OffsetDateTime.now());
        link.setLastSyncError(null);
        return linkRepo.save(link);
    }

    @Transactional
    public BoardCalendarLinkEntity markError(UUID linkId, String error) {
        requirePersonalWritable();
        BoardCalendarLinkEntity link = linkRepo.findById(linkId)
                .filter(l -> l.getDeletedAt() == null)
                .filter(l -> WorkspaceContextHolder.require().ownsPersonal(l.getWorkspaceId(), l.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("link not found"));
        link.setLastSyncError(error);
        link.setLastSyncAt(OffsetDateTime.now());
        return linkRepo.save(link);
    }

    @Transactional
    public void revoke(UUID linkId) {
        WorkspaceContext ctx = requirePersonalWritable();
        linkRepo.findById(linkId)
                .filter(l -> l.getDeletedAt() == null)
                .filter(l -> ctx.ownsPersonal(l.getWorkspaceId(), l.getUserId()))
                .ifPresent(link -> {
                    link.setActive(false);
                    link.setDeletedAt(OffsetDateTime.now());
                    linkRepo.save(link);
                });
    }

    @Transactional(readOnly = true)
    public List<BoardCalendarLinkEntity> listByBoard(UUID boardId) {
        WorkspaceContextHolder.requirePersonal();
        return linkRepo.findByBoardIdAndDeletedAtIsNull(boardId);
    }

    private WorkspaceContext requirePersonalWritable() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
            throw new IllegalStateException("PERSONAL scope required");
        }
        if (!ctx.canWrite()) {
            throw new IllegalStateException("VIEWER cannot mutate calendar links");
        }
        return ctx;
    }
}
