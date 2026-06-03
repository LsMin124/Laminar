package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.attachment.application.AttachmentService;
import com.laminar.attachment.domain.AttachmentParentType;
import com.laminar.board.application.BoardService;
import com.laminar.card.application.CardService;
import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardImportance;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.domain.UserEntity;
import com.laminar.workspace.domain.WorkspaceEntity;
import com.laminar.workspace.domain.WorkspaceMemberEntity;
import com.laminar.workspace.domain.WorkspaceMemberId;
import com.laminar.workspace.domain.WorkspaceRole;
import com.laminar.workspace.repository.WorkspaceMemberRepository;
import com.laminar.workspace.repository.WorkspaceRepository;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * C-1 회귀 게이트 — 교차 테넌트 격리 (감사 L-2: 기존 IT는 "본인 접근"만 검증해 결선 결함을 놓침).
 *
 * <p>두 워크스페이스(A, B)를 만들고, A의 리소스를 B 컨텍스트로 접근하면 차단(빈 결과)됨을 검증한다. - findById:
 * WorkspaceContext#ownsPersonal 명시 검증 (PK 로드는 @Filter 미적용 → 이 검증이 방어선) - listByBoard:
 * WorkspaceFilterAspect가 활성화한 Hibernate @Filter로 스코프
 */
class CrossTenantIsolationIT extends IsolationIntegrationBase {

  @Autowired BoardService boardService;
  @Autowired CardService cardService;
  @Autowired AttachmentService attachmentService;
  @Autowired UserSystemRepository userRepo;
  @Autowired WorkspaceRepository workspaceRepo;
  @Autowired WorkspaceMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID workspaceA;
  private UUID userA;
  private UUID boardA;
  private UUID cardA;

  private UUID workspaceB;
  private UUID userB;

  @BeforeEach
  void seed() {
    WorkspaceContextHolder.clear();

    userA = newUser("xt-a");
    workspaceA = newWorkspace("xt-a", userA);
    userB = newUser("xt-b");
    workspaceB = newWorkspace("xt-b", userB);

    setContext(workspaceA, userA);
    boardA =
        boardService
            .create("A Board", "a-board-" + UUID.randomUUID(), null, null, null, null)
            .getId();
    CardEntity card =
        cardService.create(
            new CardService.CreateInput(
                boardA,
                "A secret",
                null,
                "TOP SECRET",
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
    cardA = card.getId();
    WorkspaceContextHolder.clear();
  }

  @AfterEach
  void cleanup() {
    WorkspaceContextHolder.clear();
  }

  @Test
  void userB_cannot_read_userA_card_by_id() {
    setContext(workspaceB, userB);
    assertThat(cardService.findById(cardA)).isEmpty();
  }

  @Test
  void userB_cannot_read_userA_board_by_id() {
    setContext(workspaceB, userB);
    assertThat(boardService.findById(boardA)).isEmpty();
  }

  @Test
  void userB_board_card_list_excludes_userA_cards() {
    setContext(workspaceB, userB);
    assertThat(cardService.listByBoard(boardA)).isEmpty();
  }

  @Test
  void userB_cannot_list_userA_card_attachments() {
    setContext(workspaceB, userB);
    assertThat(attachmentService.listByParent(AttachmentParentType.CARD, cardA)).isEmpty();
  }

  @Test
  void userA_can_still_read_own_card() {
    setContext(workspaceA, userA);
    assertThat(cardService.findById(cardA)).isPresent();
  }

  private void setContext(UUID workspaceId, UUID userId) {
    WorkspaceContextHolder.set(WorkspaceContext.personal(workspaceId, userId, WorkspaceRole.OWNER));
    filterActivator.activate();
  }

  private UUID newUser(String prefix) {
    UserEntity u = new UserEntity();
    u.setEmail(prefix + "-" + UUID.randomUUID() + "@test.local");
    return userRepo.save(u).getId();
  }

  private UUID newWorkspace(String prefix, UUID ownerUserId) {
    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName(prefix + " WS");
    ws.setSlug(prefix + "-ws-" + UUID.randomUUID());
    ws.setOwnerUserId(ownerUserId);
    ws.setDefaultTimezone("Asia/Seoul");
    ws.setSettings(new HashMap<>());
    UUID wsId = workspaceRepo.save(ws).getId();
    WorkspaceMemberEntity m = new WorkspaceMemberEntity();
    m.setId(new WorkspaceMemberId(wsId, ownerUserId));
    m.setRole(WorkspaceRole.OWNER);
    memberRepo.save(m);
    return wsId;
  }
}
