package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.card.application.CardRelationService;
import com.laminar.card.application.CardService;
import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardImportance;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.group.application.GroupRelationService;
import com.laminar.group.application.GroupService;
import com.laminar.group.domain.GroupEntity;
import com.laminar.subject.domain.SubjectEntity;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectMemberId;
import com.laminar.subject.domain.SubjectRole;
import com.laminar.subject.repository.SubjectMemberRepository;
import com.laminar.subject.repository.SubjectRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.tab.application.TabService;
import com.laminar.user.domain.UserEntity;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class RelationServiceIT extends IsolationIntegrationBase {

  @Autowired TabService tabService;
  @Autowired CardService cardService;
  @Autowired GroupService groupService;
  @Autowired CardRelationService cardRelationService;
  @Autowired GroupRelationService groupRelationService;
  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID userA;
  private UUID subjectId;
  private UUID tabId;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();
    UserEntity a = new UserEntity();
    a.setEmail("rel-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    SubjectEntity ws = new SubjectEntity();
    ws.setName("Rel WS");
    ws.setSlug("rel-ws-" + UUID.randomUUID());
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
    tabId = tabService.create("B", "b-" + UUID.randomUUID(), null, null, null, null).getId();
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  @Transactional
  void card_relation_self_loop_rejected() {
    CardEntity c =
        cardService.create(
            new CardService.CreateInput(
                tabId,
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

    assertThat(cardRelationService.listByTab(tabId)).hasSize(1);
  }

  @Test
  @Transactional
  void group_relation_requires_same_board() {
    GroupEntity g1 = groupService.create(tabId, "G1", null, null);
    UUID otherTabId =
        tabService.create("B2", "b2-" + UUID.randomUUID(), null, null, null, null).getId();
    GroupEntity g2 = groupService.create(otherTabId, "G2", null, null);

    assertThatThrownBy(
            () -> groupRelationService.create(g1.getId(), g2.getId(), null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("same tab");
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
