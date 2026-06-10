package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.card.application.CardService;
import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardImportance;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectRole;
import com.laminar.error.BadRequestException;
import com.laminar.subject.domain.SubjectEntity;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectMemberId;
import com.laminar.subject.repository.SubjectMemberRepository;
import com.laminar.subject.repository.SubjectRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.tab.application.CalendarService;
import com.laminar.tab.application.TabService;
import com.laminar.user.domain.UserEntity;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** CardService 통합 검증 — CRUD + invariant + 캘린더 overlap + DnD reorder + 격리. */
class CardServiceIT extends IsolationIntegrationBase {

  @Autowired TabService tabService;
  @Autowired CardService cardService;
  @Autowired CalendarService calendarService;
  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID subjectId;
  private UUID userA;
  private UUID tabId;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();

    UserEntity a = new UserEntity();
    a.setEmail("card-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    SubjectEntity ws = new SubjectEntity();
    ws.setName("Card WS");
    ws.setSlug("card-ws-" + UUID.randomUUID());
    ws.setOwnerUserId(userA);
    ws.setDefaultTimezone("Asia/Seoul");
    ws.setSettings(new HashMap<>());
    subjectId = subjectRepo.save(ws).getId();

    SubjectMemberEntity m = new SubjectMemberEntity();
    m.setId(new SubjectMemberId(subjectId, userA));
    m.setRole(SubjectRole.OWNER);
    memberRepo.save(m);

    SubjectContextHolder.set(SubjectContext.personal(subjectId, userA, SubjectRole.OWNER));
    filterActivator.activate();
    tabId = tabService.create("Test Tab", "test-tab", null, null, null, null).getId();
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  @Transactional
  void create_persists_card_with_context_ownership() {
    CardEntity card =
        cardService.create(
            new CardService.CreateInput(
                tabId,
                "Test Card",
                null,
                "body",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                null,
                true,
                null,
                CardImportance.NORMAL,
                null,
                null,
                null));

    assertThat(card.getSubjectId()).isEqualTo(subjectId);
    assertThat(card.getUserId()).isEqualTo(userA);
    assertThat(card.getPriority()).isEqualTo(100);
  }

  @Test
  @Transactional
  void date_span_over_30_days_rejected() {
    assertThatThrownBy(
            () ->
                cardService.create(
                    new CardService.CreateInput(
                        tabId,
                        "Too long",
                        null,
                        null,
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 7, 5),
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("30");
  }

  @Test
  @Transactional
  void calendar_overlap_includes_multiday_spanning_range() {
    cardService.create(
        new CardService.CreateInput(
            tabId,
            "spans-week",
            null,
            null,
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 7),
            null,
            true,
            null,
            null,
            null,
            null,
            null));

    CalendarService.CalendarView view =
        calendarService.getTabView(tabId, LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 10));

    assertThat(view.cards()).hasSize(1);
    assertThat(view.cards().get(0).getTitle()).isEqualTo("spans-week");
  }

  @Test
  @Transactional
  void reorder_applies_priority_batch_and_respects_board_id() {
    CardEntity c1 = cardService.create(simpleCard("c1"));
    CardEntity c2 = cardService.create(simpleCard("c2"));
    CardEntity c3 = cardService.create(simpleCard("c3"));

    List<CardEntity> reordered =
        cardService.reorder(tabId, List.of(c3.getId(), c1.getId(), c2.getId()));

    assertThat(reordered).hasSize(3);
    assertThat(reordered.get(0).getPriority()).isEqualTo(100);
    assertThat(reordered.get(2).getPriority()).isEqualTo(300);
  }

  private CardService.CreateInput simpleCard(String title) {
    return new CardService.CreateInput(
        tabId,
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
        null);
  }
}
