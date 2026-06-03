package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.attachment.application.AttachmentService;
import com.laminar.attachment.domain.AttachmentParentType;
import com.laminar.card.application.CardService;
import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardImportance;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
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

/**
 * C-1 회귀 게이트 — 교차 테넌트 격리 (감사 L-2: 기존 IT는 "본인 접근"만 검증해 결선 결함을 놓침).
 *
 * <p>두 워크스페이스(A, B)를 만들고, A의 리소스를 B 컨텍스트로 접근하면 차단(빈 결과)됨을 검증한다. - findById:
 * SubjectContext#ownsPersonal 명시 검증 (PK 로드는 @Filter 미적용 → 이 검증이 방어선) - listByTab:
 * SubjectFilterAspect가 활성화한 Hibernate @Filter로 스코프
 */
class CrossTenantIsolationIT extends IsolationIntegrationBase {

  @Autowired TabService tabService;
  @Autowired CardService cardService;
  @Autowired AttachmentService attachmentService;
  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID subjectA;
  private UUID userA;
  private UUID tabA;
  private UUID cardA;

  private UUID subjectB;
  private UUID userB;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();

    userA = newUser("xt-a");
    subjectA = newSubject("xt-a", userA);
    userB = newUser("xt-b");
    subjectB = newSubject("xt-b", userB);

    setContext(subjectA, userA);
    tabA = tabService.create("A Tab", "a-tab-" + UUID.randomUUID(), null, null, null, null).getId();
    CardEntity card =
        cardService.create(
            new CardService.CreateInput(
                tabA,
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
                null));
    cardA = card.getId();
    SubjectContextHolder.clear();
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  void userB_cannot_read_userA_card_by_id() {
    setContext(subjectB, userB);
    assertThat(cardService.findById(cardA)).isEmpty();
  }

  @Test
  void userB_cannot_read_userA_board_by_id() {
    setContext(subjectB, userB);
    assertThat(tabService.findById(tabA)).isEmpty();
  }

  @Test
  void userB_board_card_list_excludes_userA_cards() {
    setContext(subjectB, userB);
    assertThat(cardService.listByTab(tabA)).isEmpty();
  }

  @Test
  void userB_cannot_list_userA_card_attachments() {
    setContext(subjectB, userB);
    assertThat(attachmentService.listByParent(AttachmentParentType.CARD, cardA)).isEmpty();
  }

  @Test
  void userA_can_still_read_own_card() {
    setContext(subjectA, userA);
    assertThat(cardService.findById(cardA)).isPresent();
  }

  private void setContext(UUID subjectId, UUID userId) {
    SubjectContextHolder.set(SubjectContext.personal(subjectId, userId, SubjectRole.OWNER));
    filterActivator.activate();
  }

  private UUID newUser(String prefix) {
    UserEntity u = new UserEntity();
    u.setEmail(prefix + "-" + UUID.randomUUID() + "@test.local");
    return userRepo.save(u).getId();
  }

  private UUID newSubject(String prefix, UUID ownerUserId) {
    SubjectEntity ws = new SubjectEntity();
    ws.setName(prefix + " WS");
    ws.setSlug(prefix + "-ws-" + UUID.randomUUID());
    ws.setOwnerUserId(ownerUserId);
    ws.setDefaultTimezone("Asia/Seoul");
    ws.setSettings(new HashMap<>());
    UUID wsId = subjectRepo.save(ws).getId();
    SubjectMemberEntity m = new SubjectMemberEntity();
    m.setId(new SubjectMemberId(wsId, ownerUserId));
    m.setRole(SubjectRole.OWNER);
    memberRepo.save(m);
    return wsId;
  }
}
