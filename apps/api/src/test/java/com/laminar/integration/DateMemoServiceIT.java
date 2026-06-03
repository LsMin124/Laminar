package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.board.application.BoardService;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.datememo.application.DateMemoService;
import com.laminar.datememo.domain.DateMemoEntity;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.UserEntity;
import com.laminar.workspace.domain.WorkspaceEntity;
import com.laminar.workspace.domain.WorkspaceMemberEntity;
import com.laminar.workspace.domain.WorkspaceMemberId;
import com.laminar.workspace.domain.WorkspaceRole;
import com.laminar.workspace.repository.WorkspaceMemberRepository;
import com.laminar.workspace.repository.WorkspaceRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class DateMemoServiceIT extends IsolationIntegrationBase {

  @Autowired BoardService boardService;
  @Autowired DateMemoService dateMemoService;
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
    a.setEmail("dm-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName("DM WS");
    ws.setSlug("dm-ws-" + UUID.randomUUID());
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
  void upsert_creates_then_updates() {
    LocalDate today = LocalDate.of(2026, 6, 15);

    DateMemoEntity first = dateMemoService.upsert(boardId, today, "초안", null);
    DateMemoEntity updated = dateMemoService.upsert(boardId, today, "수정본", null);

    assertThat(first.getBodyMd()).isEqualTo("초안");
    assertThat(updated.getBodyMd()).isEqualTo("수정본");
    assertThat(updated.getId()).isEqualTo(first.getId());
  }

  @Test
  @Transactional
  void list_by_range_returns_memos_in_window() {
    dateMemoService.upsert(boardId, LocalDate.of(2026, 6, 1), "june 1", null);
    dateMemoService.upsert(boardId, LocalDate.of(2026, 6, 15), "june 15", null);
    dateMemoService.upsert(boardId, LocalDate.of(2026, 7, 1), "july 1", null);

    assertThat(
            dateMemoService.listByBoardDateRange(
                boardId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
        .hasSize(2);
  }
}
