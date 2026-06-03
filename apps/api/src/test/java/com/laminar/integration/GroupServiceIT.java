package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.card.application.CardService;
import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardImportance;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class GroupServiceIT extends IsolationIntegrationBase {

  @Autowired TabService tabService;
  @Autowired GroupService groupService;
  @Autowired CardService cardService;
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
    a.setEmail("group-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    SubjectEntity ws = new SubjectEntity();
    ws.setName("Group WS");
    ws.setSlug("group-ws-" + UUID.randomUUID());
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
  void create_and_list_by_board() {
    groupService.create(tabId, "G1", "#ff0000", null);
    groupService.create(tabId, "G2", "#00ff00", null);

    List<GroupEntity> groups = groupService.listByTab(tabId);

    assertThat(groups).hasSize(2);
    assertThat(groups.get(0).getPriority()).isEqualTo(100);
    assertThat(groups.get(1).getPriority()).isEqualTo(200);
  }

  @Test
  @Transactional
  void add_member_links_card_to_group() {
    GroupEntity group = groupService.create(tabId, "G", null, null);
    CardEntity card =
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

    groupService.addMember(group.getId(), card.getId());

    assertThat(groupService.listCardIdsInGroup(group.getId())).containsExactly(card.getId());
    assertThat(groupService.listGroupIdsForCard(card.getId())).containsExactly(group.getId());
  }

  @Test
  @Transactional
  void remove_member_clears_link() {
    GroupEntity group = groupService.create(tabId, "G", null, null);
    CardEntity card =
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
    groupService.addMember(group.getId(), card.getId());

    groupService.removeMember(group.getId(), card.getId());

    assertThat(groupService.listCardIdsInGroup(group.getId())).isEmpty();
  }
}
