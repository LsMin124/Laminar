package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.card.application.CardService;
import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardImportance;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.samplemanager.application.SampleManagerLinkService;
import com.laminar.samplemanager.domain.SampleManagerLinkEntity;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class SampleManagerLinkServiceIT extends IsolationIntegrationBase {

  @Autowired TabService tabService;
  @Autowired CardService cardService;
  @Autowired SampleManagerLinkService linkService;
  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID subjectId;
  private UUID userA;
  private UUID cardId;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();
    UserEntity a = new UserEntity();
    a.setEmail("sm-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    SubjectEntity ws = new SubjectEntity();
    ws.setName("SM WS");
    ws.setSlug("sm-ws-" + UUID.randomUUID());
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
    UUID tabId = tabService.create("B", "b-" + UUID.randomUUID(), null, null, null, null).getId();
    CardEntity card =
        cardService.create(
            new CardService.CreateInput(
                tabId,
                "Sample card",
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
    cardId = card.getId();
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  @Transactional
  void link_then_update_uses_same_row_idempotently() {
    SampleManagerLinkEntity first =
        linkService.linkOrUpdate(
            cardId,
            "SAMPLE-001",
            "step-1",
            "https://sm.example/SAMPLE-001/step-1",
            Map.of("status", "in_progress"));

    SampleManagerLinkEntity updated =
        linkService.linkOrUpdate(
            cardId,
            "SAMPLE-001",
            "step-1",
            "https://sm.example/SAMPLE-001/step-1",
            Map.of("status", "completed"));

    assertThat(updated.getId()).isEqualTo(first.getId());
    assertThat(updated.getPayloadSnapshot()).containsEntry("status", "completed");
  }

  @Test
  @Transactional
  void different_step_creates_new_row() {
    linkService.linkOrUpdate(cardId, "SAMPLE-001", "step-1", null, Map.of());
    linkService.linkOrUpdate(cardId, "SAMPLE-001", "step-2", null, Map.of());

    assertThat(linkService.listByCard(cardId)).hasSize(2);
  }

  @Test
  @Transactional
  void mark_synced_sets_synced_at() {
    SampleManagerLinkEntity link =
        linkService.linkOrUpdate(cardId, "SAMPLE-002", "step-1", null, Map.of());

    SampleManagerLinkEntity synced = linkService.markSynced(link.getId());

    assertThat(synced.getSyncedAt()).isNotNull();
  }
}
