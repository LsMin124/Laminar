package com.laminar.gcal.domain;

import com.laminar.common.domain.WorkspaceScopedBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "card_event_links")
@Filter(name = "workspaceSharedFilter", condition = "workspace_id = :ctxWorkspaceId")
@Getter
@Setter
public class CardEventLinkEntity extends WorkspaceScopedBaseEntity {

  @Column(name = "card_id", nullable = false)
  private UUID cardId;

  @Column(name = "board_calendar_link_id", nullable = false)
  private UUID boardCalendarLinkId;

  @Column(name = "google_event_id", nullable = false)
  private String googleEventId;

  @Column(name = "etag")
  private String etag;

  @Column(name = "last_synced_at", nullable = false)
  private OffsetDateTime lastSyncedAt;

  @Column(name = "last_pushed_hash")
  private String lastPushedHash;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
