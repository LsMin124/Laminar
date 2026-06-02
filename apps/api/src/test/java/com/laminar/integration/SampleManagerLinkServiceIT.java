package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.board.BoardService;
import com.laminar.card.CardEntity;
import com.laminar.card.CardImportance;
import com.laminar.card.CardService;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.samplemanager.application.SampleManagerLinkService;
import com.laminar.samplemanager.domain.SampleManagerLinkEntity;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.UserEntity;
import com.laminar.workspace.WorkspaceEntity;
import com.laminar.workspace.WorkspaceMemberEntity;
import com.laminar.workspace.WorkspaceMemberId;
import com.laminar.workspace.WorkspaceMemberRepository;
import com.laminar.workspace.WorkspaceRepository;
import com.laminar.workspace.WorkspaceRole;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class SampleManagerLinkServiceIT extends IsolationIntegrationBase {

  @Autowired BoardService boardService;
  @Autowired CardService cardService;
  @Autowired SampleManagerLinkService linkService;
  @Autowired UserSystemRepository userRepo;
  @Autowired WorkspaceRepository workspaceRepo;
  @Autowired WorkspaceMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID workspaceId;
  private UUID userA;
  private UUID cardId;

  @BeforeEach
  void seed() {
    WorkspaceContextHolder.clear();
    UserEntity a = new UserEntity();
    a.setEmail("sm-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName("SM WS");
    ws.setSlug("sm-ws-" + UUID.randomUUID());
    ws.setOwnerUserId(userA);
    ws.setDefaultTimezone("Asia/Seoul");
    ws.setSettings(new HashMap<>());
    workspaceId = workspaceRepo.save(ws).getId();

    WorkspaceMemberEntity m = new WorkspaceMemberEntity();
    m.setId(new WorkspaceMemberId(workspaceId, userA));
    m.setRole(WorkspaceRole.OWNER);
    memberRepo.save(m);

    WorkspaceContextHolder.set(WorkspaceContext.personal(workspaceId, userA, WorkspaceRole.OWNER));
    filterActivator.activate();
    UUID boardId =
        boardService.create("B", "b-" + UUID.randomUUID(), null, null, null, null).getId();
    CardEntity card =
        cardService.create(
            new CardService.CreateInput(
                boardId,
                "Sample card",
                null,
                null,
                null,
                null,
                null,
                true,
                null,
                CardImportance.NORMAL,
                null,
                null,
                null,
                null));
    cardId = card.getId();
  }

  @AfterEach
  void cleanup() {
    WorkspaceContextHolder.clear();
  }

  @Test
  @Transactional
  void link_then_update_uses_same_row_idempotently() {
    SampleManagerLinkEntity first =
        linkService.linkOrUpdate(
            cardId,
            "SAMPLE-001",
            "step-1",
            "https://sm.example/SAMPLE-001/step-1",
            Map.of("status", "in_progress"));

    SampleManagerLinkEntity updated =
        linkService.linkOrUpdate(
            cardId,
            "SAMPLE-001",
            "step-1",
            "https://sm.example/SAMPLE-001/step-1",
            Map.of("status", "completed"));

    assertThat(updated.getId()).isEqualTo(first.getId());
    assertThat(updated.getPayloadSnapshot()).containsEntry("status", "completed");
  }

  @Test
  @Transactional
  void different_step_creates_new_row() {
    linkService.linkOrUpdate(cardId, "SAMPLE-001", "step-1", null, Map.of());
    linkService.linkOrUpdate(cardId, "SAMPLE-001", "step-2", null, Map.of());

    assertThat(linkService.listByCard(cardId)).hasSize(2);
  }

  @Test
  @Transactional
  void mark_synced_sets_synced_at() {
    SampleManagerLinkEntity link =
        linkService.linkOrUpdate(cardId, "SAMPLE-002", "step-1", null, Map.of());

    SampleManagerLinkEntity synced = linkService.markSynced(link.getId());

    assertThat(synced.getSyncedAt()).isNotNull();
  }
}
