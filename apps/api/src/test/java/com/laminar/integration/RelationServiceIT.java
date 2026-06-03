package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.board.application.BoardService;
import com.laminar.card.CardEntity;
import com.laminar.card.CardImportance;
import com.laminar.card.CardRelationService;
import com.laminar.card.CardService;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.group.application.GroupRelationService;
import com.laminar.group.application.GroupService;
import com.laminar.group.domain.GroupEntity;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.UserEntity;
import com.laminar.workspace.WorkspaceEntity;
import com.laminar.workspace.WorkspaceMemberEntity;
import com.laminar.workspace.WorkspaceMemberId;
import com.laminar.workspace.WorkspaceMemberRepository;
import com.laminar.workspace.WorkspaceRepository;
import com.laminar.workspace.WorkspaceRole;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class RelationServiceIT extends IsolationIntegrationBase {

  @Autowired BoardService boardService;
  @Autowired CardService cardService;
  @Autowired GroupService groupService;
  @Autowired CardRelationService cardRelationService;
  @Autowired GroupRelationService groupRelationService;
  @Autowired UserSystemRepository userRepo;
  @Autowired WorkspaceRepository workspaceRepo;
  @Autowired WorkspaceMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID userA;
  private UUID workspaceId;
  private UUID boardId;

  @BeforeEach
  void seed() {
    WorkspaceContextHolder.clear();
    UserEntity a = new UserEntity();
    a.setEmail("rel-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName("Rel WS");
    ws.setSlug("rel-ws-" + UUID.randomUUID());
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
    boardId = boardService.create("B", "b-" + UUID.randomUUID(), null, null, null, null).getId();
  }

  @AfterEach
  void cleanup() {
    WorkspaceContextHolder.clear();
  }

  @Test
  @Transactional
  void card_relation_self_loop_rejected() {
    CardEntity c =
        cardService.create(
            new CardService.CreateInput(
                boardId,
                "C",
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

    assertThatThrownBy(
            () -> cardRelationService.create(c.getId(), c.getId(), null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("from_card_id == to_card_id");
  }

  @Test
  @Transactional
  void card_relation_persists_and_lists_by_board() {
    CardEntity c1 = cardService.create(simpleCard("c1"));
    CardEntity c2 = cardService.create(simpleCard("c2"));

    cardRelationService.create(c1.getId(), c2.getId(), "implements", "summary", null, null);

    assertThat(cardRelationService.listByBoard(boardId)).hasSize(1);
  }

  @Test
  @Transactional
  void group_relation_requires_same_board() {
    GroupEntity g1 = groupService.create(boardId, "G1", null, null);
    UUID otherBoardId =
        boardService.create("B2", "b2-" + UUID.randomUUID(), null, null, null, null).getId();
    GroupEntity g2 = groupService.create(otherBoardId, "G2", null, null);

    assertThatThrownBy(
            () -> groupRelationService.create(g1.getId(), g2.getId(), null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("same board");
  }

  private CardService.CreateInput simpleCard(String title) {
    return new CardService.CreateInput(
        boardId,
        title,
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
        null);
  }
}
