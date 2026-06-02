package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.board.BoardService;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.system.UserSystemRepository;
import com.laminar.tab.TabEntity;
import com.laminar.tab.TabRelationService;
import com.laminar.tab.TabService;
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

class TabServiceIT extends IsolationIntegrationBase {

  @Autowired BoardService boardService;
  @Autowired TabService tabService;
  @Autowired TabRelationService tabRelationService;
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
    a.setEmail("tab-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName("Tab WS");
    ws.setSlug("tab-ws-" + UUID.randomUUID());
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
  void create_root_then_child_tree() {
    TabEntity root = tabService.create(boardId, null, "Root", null, null, null, null, null);
    TabEntity child =
        tabService.create(boardId, root.getId(), "Child", null, null, null, null, null);

    assertThat(child.getParentTabId()).isEqualTo(root.getId());
    assertThat(tabService.listRootsByBoard(boardId))
        .extracting(TabEntity::getId)
        .containsExactly(root.getId());
    assertThat(tabService.listChildren(root.getId()))
        .extracting(TabEntity::getId)
        .containsExactly(child.getId());
  }

  @Test
  @Transactional
  void tab_relation_cycle_rejected() {
    TabEntity t1 = tabService.create(boardId, null, "T1", null, null, null, null, null);
    TabEntity t2 = tabService.create(boardId, null, "T2", null, null, null, null, null);
    tabRelationService.create(t1.getId(), t2.getId(), null, null, null);

    assertThatThrownBy(() -> tabRelationService.create(t2.getId(), t1.getId(), null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cycle");
  }
}
