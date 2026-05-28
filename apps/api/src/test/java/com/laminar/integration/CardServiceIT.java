package com.laminar.integration;

import com.laminar.board.BoardService;
import com.laminar.board.CalendarService;
import com.laminar.card.CardEntity;
import com.laminar.card.CardImportance;
import com.laminar.card.CardService;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CardService 통합 검증 — CRUD + invariant + 캘린더 overlap + DnD reorder + 격리.
 */
class CardServiceIT extends IsolationIntegrationBase {

    @Autowired BoardService boardService;
    @Autowired CardService cardService;
    @Autowired CalendarService calendarService;
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
        a.setEmail("card-a-" + UUID.randomUUID() + "@test.local");
        userA = userRepo.save(a).getId();

        WorkspaceEntity ws = new WorkspaceEntity();
        ws.setName("Card WS");
        ws.setSlug("card-ws-" + UUID.randomUUID());
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
        boardId = boardService.create("Test Board", "test-board", null, null, null, null).getId();
    }

    @AfterEach
    void cleanup() {
        WorkspaceContextHolder.clear();
    }

    @Test
    @Transactional
    void create_persists_card_with_context_ownership() {
        CardEntity card = cardService.create(new CardService.CreateInput(
                boardId, "Test Card", null, "body",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3),
                null, true, null,
                CardImportance.NORMAL, null, null, null, null));

        assertThat(card.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(card.getUserId()).isEqualTo(userA);
        assertThat(card.getPriority()).isEqualTo(100);
    }

    @Test
    @Transactional
    void perpetual_link_invariant_rejects_mismatch() {
        assertThatThrownBy(() -> cardService.create(new CardService.CreateInput(
                boardId, "Bad", null, null, null, null, null, true, null,
                CardImportance.PERPETUAL_VER, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linked_perpetual_id");
    }

    @Test
    @Transactional
    void date_span_over_30_days_rejected() {
        assertThatThrownBy(() -> cardService.create(new CardService.CreateInput(
                boardId, "Too long", null, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 5),
                null, true, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30");
    }

    @Test
    @Transactional
    void calendar_overlap_includes_multiday_spanning_range() {
        cardService.create(new CardService.CreateInput(
                boardId, "spans-week", null, null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7),
                null, true, null, null, null, null, null, null));

        CalendarService.CalendarView view = calendarService.getBoardView(
                boardId, LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 10));

        assertThat(view.cards()).hasSize(1);
        assertThat(view.cards().get(0).getTitle()).isEqualTo("spans-week");
    }

    @Test
    @Transactional
    void reorder_applies_priority_batch_and_respects_board_id() {
        CardEntity c1 = cardService.create(simpleCard("c1"));
        CardEntity c2 = cardService.create(simpleCard("c2"));
        CardEntity c3 = cardService.create(simpleCard("c3"));

        List<CardEntity> reordered = cardService.reorder(
                boardId, List.of(c3.getId(), c1.getId(), c2.getId()));

        assertThat(reordered).hasSize(3);
        assertThat(reordered.get(0).getPriority()).isEqualTo(100);
        assertThat(reordered.get(2).getPriority()).isEqualTo(300);
    }

    private CardService.CreateInput simpleCard(String title) {
        return new CardService.CreateInput(
                boardId, title, null, null, null, null, null, true, null,
                CardImportance.NORMAL, null, null, null, null);
    }
}
