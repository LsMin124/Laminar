package com.laminar.gcal.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.gcal.domain.SyncDirection;
import com.laminar.gcal.domain.TabCalendarLinkEntity;
import com.laminar.gcal.repository.TabCalendarLinkRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TabCalendarLink CRUD + sync state 관리.
 *
 * <p>Spec §3.6: sync_direction=push/pull/two-way + sync_token (incremental sync). 실제 GCal API 호출은
 * Phase 11 cron에서 (이 서비스는 메타 + 상태만).
 */
@Service
public class TabCalendarLinkService {

  private final TabCalendarLinkRepository linkRepo;

  public TabCalendarLinkService(TabCalendarLinkRepository linkRepo) {
    this.linkRepo = linkRepo;
  }

  @Transactional
  public TabCalendarLinkEntity link(UUID tabId, String googleCalendarId, SyncDirection direction) {
    SubjectContext ctx = requirePersonalWritable();
    // 이미 있으면 reactivate
    Optional<TabCalendarLinkEntity> existing =
        linkRepo.findByTabIdAndGoogleCalendarIdAndDeletedAtIsNull(tabId, googleCalendarId);
    if (existing.isPresent()) {
      TabCalendarLinkEntity link = existing.get();
      link.setActive(true);
      link.setSyncDirection(direction == null ? SyncDirection.TWO_WAY : direction);
      return linkRepo.save(link);
    }

    TabCalendarLinkEntity link = new TabCalendarLinkEntity();
    link.setSubjectId(ctx.subjectId());
    link.setUserId(ctx.userId());
    link.setCreatedBy(ctx.userId());
    link.setTabId(tabId);
    link.setGoogleCalendarId(googleCalendarId);
    link.setSyncDirection(direction == null ? SyncDirection.TWO_WAY : direction);
    link.setActive(true);
    return linkRepo.save(link);
  }

  @Transactional
  public TabCalendarLinkEntity markSynced(UUID linkId, String syncToken) {
    requirePersonalWritable();
    TabCalendarLinkEntity link =
        linkRepo
            .findById(linkId)
            .filter(l -> l.getDeletedAt() == null)
            .filter(
                l -> SubjectContextHolder.require().ownsPersonal(l.getSubjectId(), l.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("link not found"));
    link.setSyncToken(syncToken);
    link.setLastSyncAt(OffsetDateTime.now());
    link.setLastSyncError(null);
    return linkRepo.save(link);
  }

  @Transactional
  public TabCalendarLinkEntity markError(UUID linkId, String error) {
    requirePersonalWritable();
    TabCalendarLinkEntity link =
        linkRepo
            .findById(linkId)
            .filter(l -> l.getDeletedAt() == null)
            .filter(
                l -> SubjectContextHolder.require().ownsPersonal(l.getSubjectId(), l.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("link not found"));
    link.setLastSyncError(error);
    link.setLastSyncAt(OffsetDateTime.now());
    return linkRepo.save(link);
  }

  @Transactional
  public void revoke(UUID linkId) {
    SubjectContext ctx = requirePersonalWritable();
    linkRepo
        .findById(linkId)
        .filter(l -> l.getDeletedAt() == null)
        .filter(l -> ctx.ownsPersonal(l.getSubjectId(), l.getUserId()))
        .ifPresent(
            link -> {
              link.setActive(false);
              link.setDeletedAt(OffsetDateTime.now());
              linkRepo.save(link);
            });
  }

  @Transactional(readOnly = true)
  public List<TabCalendarLinkEntity> listByTab(UUID tabId) {
    SubjectContextHolder.requirePersonal();
    return linkRepo.findByTabIdAndDeletedAtIsNull(tabId);
  }

  private SubjectContext requirePersonalWritable() {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.scope() != SubjectContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate calendar links");
    }
    return ctx;
  }
}
