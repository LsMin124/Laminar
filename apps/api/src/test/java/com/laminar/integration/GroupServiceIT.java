package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.board.application.BoardService;
import com.laminar.card.application.CardService;
import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardImportance;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class GroupServiceIT extends IsolationIntegrationBase {

  @Autowired BoardService boardService;
  @Autowired GroupService groupService;
  @Autowired CardService cardService;
  @Autowired UserSystemRepository userRepo;
  @Autowired WorkspaceRepository workspaceRepo;
  @Autowired WorkspaceMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID workspaceId;
  private UUID userA;
  private UUID boardId;

  @BeforeEach
  void seed() {
    WorkspaceContextHolder.clear();
    UserEntity a = new UserEntity();
    a.setEmail("group-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName("Group WS");
    ws.setSlug("group-ws-" + UUID.randomUUID());
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
  void create_and_list_by_board() {
    groupService.create(boardId, "G1", "#ff0000", null);
    groupService.create(boardId, "G2", "#00ff00", null);

    List<GroupEntity> groups = groupService.listByBoard(boardId);

    assertThat(groups).hasSize(2);
    assertThat(groups.get(0).getPriority()).isEqualTo(100);
    assertThat(groups.get(1).getPriority()).isEqualTo(200);
  }

  @Test
  @Transactional
  void add_member_links_card_to_group() {
    GroupEntity group = groupService.create(boardId, "G", null, null);
    CardEntity card =
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

    groupService.addMember(group.getId(), card.getId());

    assertThat(groupService.listCardIdsInGroup(group.getId())).containsExactly(card.getId());
    assertThat(groupService.listGroupIdsForCard(card.getId())).containsExactly(group.getId());
  }

  @Test
  @Transactional
  void remove_member_clears_link() {
    GroupEntity group = groupService.create(boardId, "G", null, null);
    CardEntity card =
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
    groupService.addMember(group.getId(), card.getId());

    groupService.removeMember(group.getId(), card.getId());

    assertThat(groupService.listCardIdsInGroup(group.getId())).isEmpty();
  }
}
