package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.board.BoardEntity;
import com.laminar.board.BoardRepository;
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
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 격리 매트릭스 5종 — 실제 PostgreSQL + Flyway + Hibernate @Filter 검증.
 *
 * <p>Spec §3.2 격리 정책: 1. SYSTEM scope에서 personal-first read → 모든 row 보임 (필터 미활성) 2. PERSONAL
 * scope에서 자기 user board → 보임 3. PERSONAL scope에서 같은 workspace 다른 user board → 0건 (cross-user 누출 0)
 * 4. PERSONAL scope에서 다른 workspace board → 0건 (cross-workspace 누출 0) 5. SystemRepository
 * (UserSystemRepository)는 모든 사용자 read (격리 우회)
 */
class WorkspaceIsolationMatrixIT extends IsolationIntegrationBase {

  @Autowired UserSystemRepository userRepo;
  @Autowired WorkspaceRepository workspaceRepo;
  @Autowired WorkspaceMemberRepository memberRepo;
  @Autowired BoardRepository boardRepo;
  @Autowired HibernateFilterActivator filterActivator;
  @Autowired EntityManager entityManager;

  private UUID userA;
  private UUID userB;
  private UUID workspaceX;
  private UUID workspaceY;
  private UUID boardXAId;
  private UUID boardXBId;
  private UUID boardYAId;

  @BeforeEach
  void seed() {
    WorkspaceContextHolder.clear();

    UserEntity a = new UserEntity();
    a.setEmail("a-" + UUID.randomUUID() + "@test.local");
    a = userRepo.save(a);
    userA = a.getId();

    UserEntity b = new UserEntity();
    b.setEmail("b-" + UUID.randomUUID() + "@test.local");
    b = userRepo.save(b);
    userB = b.getId();

    WorkspaceEntity wsX = createWorkspace(userA, "ws-x-" + UUID.randomUUID());
    workspaceX = wsX.getId();
    WorkspaceEntity wsY = createWorkspace(userB, "ws-y-" + UUID.randomUUID());
    workspaceY = wsY.getId();

    addMember(workspaceX, userA, WorkspaceRole.OWNER);
    addMember(workspaceX, userB, WorkspaceRole.MEMBER);
    addMember(workspaceY, userB, WorkspaceRole.OWNER);

    boardXAId = createBoard(workspaceX, userA, "ws-x-userA-board").getId();
    boardXBId = createBoard(workspaceX, userB, "ws-x-userB-board").getId();
    boardYAId = createBoard(workspaceY, userB, "ws-y-userB-board").getId();
  }

  @AfterEach
  void cleanup() {
    WorkspaceContextHolder.clear();
  }

  @Test
  @Transactional
  void matrix_1_system_scope_sees_all_boards() {
    WorkspaceContextHolder.set(WorkspaceContext.system());
    filterActivator.activate();

    List<BoardEntity> all = boardRepo.findAll();

    assertThat(all)
        .extracting(BoardEntity::getId)
        .as("SYSTEM scope filters disabled — all 3 seed boards visible")
        .contains(boardXAId, boardXBId, boardYAId);
  }

  @Test
  @Transactional
  void matrix_2_personal_scope_userA_in_wsX_sees_only_own_board() {
    WorkspaceContextHolder.set(WorkspaceContext.personal(workspaceX, userA, WorkspaceRole.OWNER));
    filterActivator.activate();

    List<BoardEntity> visible = boardRepo.findAll();

    assertThat(visible)
        .extracting(BoardEntity::getId)
        .as("PERSONAL scope userA@wsX — only own board, cross-user X")
        .containsExactly(boardXAId);
  }

  @Test
  @Transactional
  void matrix_3_personal_scope_userB_in_wsX_does_not_see_userA_board() {
    WorkspaceContextHolder.set(WorkspaceContext.personal(workspaceX, userB, WorkspaceRole.MEMBER));
    filterActivator.activate();

    List<BoardEntity> visible = boardRepo.findAll();

    assertThat(visible)
        .extracting(BoardEntity::getId)
        .as("같은 workspace + 다른 user — cross-user 누출 0")
        .containsExactly(boardXBId)
        .doesNotContain(boardXAId);
  }

  @Test
  @Transactional
  void matrix_4_personal_scope_userB_in_wsY_does_not_see_wsX_board() {
    WorkspaceContextHolder.set(WorkspaceContext.personal(workspaceY, userB, WorkspaceRole.OWNER));
    filterActivator.activate();

    List<BoardEntity> visible = boardRepo.findAll();

    assertThat(visible)
        .extracting(BoardEntity::getId)
        .as("다른 workspace — cross-workspace 누출 0")
        .containsExactly(boardYAId)
        .doesNotContain(boardXAId, boardXBId);
  }

  @Test
  @Transactional
  void matrix_5_system_repository_bypasses_filter() {
    WorkspaceContextHolder.set(WorkspaceContext.personal(workspaceY, userB, WorkspaceRole.OWNER));
    filterActivator.activate();

    List<UserEntity> allUsers = userRepo.findAll();

    assertThat(allUsers)
        .extracting(UserEntity::getId)
        .as("UserSystemRepository는 SystemRepository 마커 → 격리 우회")
        .contains(userA, userB);
  }

  private WorkspaceEntity createWorkspace(UUID ownerId, String slug) {
    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName("Test " + slug);
    ws.setSlug(slug);
    ws.setOwnerUserId(ownerId);
    ws.setDefaultTimezone("Asia/Seoul");
    ws.setSettings(new HashMap<>());
    return workspaceRepo.save(ws);
  }

  private void addMember(UUID workspaceId, UUID userId, WorkspaceRole role) {
    WorkspaceMemberEntity member = new WorkspaceMemberEntity();
    member.setId(new WorkspaceMemberId(workspaceId, userId));
    member.setRole(role);
    memberRepo.save(member);
  }

  private BoardEntity createBoard(UUID workspaceId, UUID userId, String slug) {
    BoardEntity board = new BoardEntity();
    board.setWorkspaceId(workspaceId);
    board.setUserId(userId);
    board.setName(slug);
    board.setSlug(slug);
    board.setSettings(new HashMap<>());
    return boardRepo.save(board);
  }
}
