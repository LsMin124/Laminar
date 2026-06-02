package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.board.BoardEntity;
import com.laminar.board.BoardRepository;
import com.laminar.board.BoardService;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
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

/**
 * BoardService 통합 검증 (실제 PostgreSQL + Flyway + @Filter).
 *
 * <p>검증: - create: priority 자동 부여 + workspace/user 자동 set - listActive: priority 정렬 + soft-deleted
 * 제외 - reorder: 배치 priority 갱신 - 격리: 다른 user의 board는 listActive에 노출 안 됨 - VIEWER 권한은 create 차단
 */
class BoardServiceIT extends IsolationIntegrationBase {

  @Autowired BoardService boardService;
  @Autowired BoardRepository boardRepo;
  @Autowired UserSystemRepository userRepo;
  @Autowired WorkspaceRepository workspaceRepo;
  @Autowired WorkspaceMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID workspaceId;
  private UUID userA;
  private UUID userB;

  @BeforeEach
  void seed() {
    WorkspaceContextHolder.clear();

    UserEntity a = new UserEntity();
    a.setEmail("board-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    UserEntity b = new UserEntity();
    b.setEmail("board-b-" + UUID.randomUUID() + "@test.local");
    userB = userRepo.save(b).getId();

    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName("Test WS");
    ws.setSlug("test-ws-" + UUID.randomUUID());
    ws.setOwnerUserId(userA);
    ws.setDefaultTimezone("Asia/Seoul");
    ws.setSettings(new HashMap<>());
    workspaceId = workspaceRepo.save(ws).getId();

    addMember(workspaceId, userA, WorkspaceRole.OWNER);
    addMember(workspaceId, userB, WorkspaceRole.MEMBER);
  }

  @AfterEach
  void cleanup() {
    WorkspaceContextHolder.clear();
  }

  @Test
  @Transactional
  void create_assigns_priority_and_context_ownership() {
    enterAsOwner();

    BoardEntity first = boardService.create("First", "first", null, null, null, null);
    BoardEntity second = boardService.create("Second", "second", null, null, null, null);

    assertThat(first.getWorkspaceId()).isEqualTo(workspaceId);
    assertThat(first.getUserId()).isEqualTo(userA);
    assertThat(second.getPriority()).isEqualTo(first.getPriority() + 100);
  }

  @Test
  @Transactional
  void list_filters_cross_user_boards() {
    enterAsOwner();
    boardService.create("UserA Board", "a-board", null, null, null, null);

    WorkspaceContextHolder.set(WorkspaceContext.personal(workspaceId, userB, WorkspaceRole.MEMBER));
    filterActivator.activate();
    boardService.create("UserB Board", "b-board", null, null, null, null);

    WorkspaceContextHolder.set(WorkspaceContext.personal(workspaceId, userA, WorkspaceRole.OWNER));
    filterActivator.activate();
    List<BoardEntity> visibleToA = boardService.listActive();

    assertThat(visibleToA).extracting(BoardEntity::getName).containsExactly("UserA Board");
  }

  @Test
  @Transactional
  void reorder_applies_batch_priority() {
    enterAsOwner();
    BoardEntity b1 = boardService.create("B1", "b1", null, null, null, null);
    BoardEntity b2 = boardService.create("B2", "b2", null, null, null, null);
    BoardEntity b3 = boardService.create("B3", "b3", null, null, null, null);

    List<BoardEntity> reordered = boardService.reorder(List.of(b3.getId(), b1.getId(), b2.getId()));

    assertThat(reordered).hasSize(3);
    assertThat(reordered.get(0).getPriority()).isEqualTo(100);
    assertThat(reordered.get(1).getPriority()).isEqualTo(200);
    assertThat(reordered.get(2).getPriority()).isEqualTo(300);
  }

  @Test
  @Transactional
  void viewer_cannot_mutate() {
    WorkspaceContextHolder.set(WorkspaceContext.personal(workspaceId, userA, WorkspaceRole.VIEWER));
    filterActivator.activate();

    assertThatThrownBy(() -> boardService.create("X", "x", null, null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("VIEWER");
  }

  private void enterAsOwner() {
    WorkspaceContextHolder.set(WorkspaceContext.personal(workspaceId, userA, WorkspaceRole.OWNER));
    filterActivator.activate();
  }

  private void addMember(UUID wsId, UUID uId, WorkspaceRole role) {
    WorkspaceMemberEntity member = new WorkspaceMemberEntity();
    member.setId(new WorkspaceMemberId(wsId, uId));
    member.setRole(role);
    memberRepo.save(member);
  }
}
