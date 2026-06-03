package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.laminar.tab.domain.TabEntity;
import com.laminar.tab.repository.TabRepository;
import com.laminar.user.domain.UserEntity;
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
 * scope에서 자기 user tab → 보임 3. PERSONAL scope에서 같은 subject 다른 user tab → 0건 (cross-user 누출 0) 4.
 * PERSONAL scope에서 다른 subject tab → 0건 (cross-subject 누출 0) 5. SystemRepository
 * (UserSystemRepository)는 모든 사용자 read (격리 우회)
 */
class SubjectIsolationMatrixIT extends IsolationIntegrationBase {

  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;
  @Autowired TabRepository tabRepo;
  @Autowired HibernateFilterActivator filterActivator;
  @Autowired EntityManager entityManager;

  private UUID userA;
  private UUID userB;
  private UUID subjectX;
  private UUID subjectY;
  private UUID tabXAId;
  private UUID tabXBId;
  private UUID tabYAId;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();

    UserEntity a = new UserEntity();
    a.setEmail("a-" + UUID.randomUUID() + "@test.local");
    a = userRepo.save(a);
    userA = a.getId();

    UserEntity b = new UserEntity();
    b.setEmail("b-" + UUID.randomUUID() + "@test.local");
    b = userRepo.save(b);
    userB = b.getId();

    SubjectEntity wsX = createSubject(userA, "ws-x-" + UUID.randomUUID());
    subjectX = wsX.getId();
    SubjectEntity wsY = createSubject(userB, "ws-y-" + UUID.randomUUID());
    subjectY = wsY.getId();

    addMember(subjectX, userA, SubjectRole.OWNER);
    addMember(subjectX, userB, SubjectRole.MEMBER);
    addMember(subjectY, userB, SubjectRole.OWNER);

    tabXAId = createTab(subjectX, userA, "ws-x-userA-tab").getId();
    tabXBId = createTab(subjectX, userB, "ws-x-userB-tab").getId();
    tabYAId = createTab(subjectY, userB, "ws-y-userB-tab").getId();
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  @Transactional
  void matrix_1_system_scope_sees_all_boards() {
    SubjectContextHolder.set(SubjectContext.system());
    filterActivator.activate();

    List<TabEntity> all = tabRepo.findAll();

    assertThat(all)
        .extracting(TabEntity::getId)
        .as("SYSTEM scope filters disabled — all 3 seed boards visible")
        .contains(tabXAId, tabXBId, tabYAId);
  }

  @Test
  @Transactional
  void matrix_2_personal_scope_userA_in_wsX_sees_only_own_board() {
    SubjectContextHolder.set(SubjectContext.personal(subjectX, userA, SubjectRole.OWNER));
    filterActivator.activate();

    List<TabEntity> visible = tabRepo.findAll();

    assertThat(visible)
        .extracting(TabEntity::getId)
        .as("PERSONAL scope userA@wsX — only own tab, cross-user X")
        .containsExactly(tabXAId);
  }

  @Test
  @Transactional
  void matrix_3_personal_scope_userB_in_wsX_does_not_see_userA_board() {
    SubjectContextHolder.set(SubjectContext.personal(subjectX, userB, SubjectRole.MEMBER));
    filterActivator.activate();

    List<TabEntity> visible = tabRepo.findAll();

    assertThat(visible)
        .extracting(TabEntity::getId)
        .as("같은 subject + 다른 user — cross-user 누출 0")
        .containsExactly(tabXBId)
        .doesNotContain(tabXAId);
  }

  @Test
  @Transactional
  void matrix_4_personal_scope_userB_in_wsY_does_not_see_wsX_board() {
    SubjectContextHolder.set(SubjectContext.personal(subjectY, userB, SubjectRole.OWNER));
    filterActivator.activate();

    List<TabEntity> visible = tabRepo.findAll();

    assertThat(visible)
        .extracting(TabEntity::getId)
        .as("다른 subject — cross-subject 누출 0")
        .containsExactly(tabYAId)
        .doesNotContain(tabXAId, tabXBId);
  }

  @Test
  @Transactional
  void matrix_5_system_repository_bypasses_filter() {
    SubjectContextHolder.set(SubjectContext.personal(subjectY, userB, SubjectRole.OWNER));
    filterActivator.activate();

    List<UserEntity> allUsers = userRepo.findAll();

    assertThat(allUsers)
        .extracting(UserEntity::getId)
        .as("UserSystemRepository는 SystemRepository 마커 → 격리 우회")
        .contains(userA, userB);
  }

  private SubjectEntity createSubject(UUID ownerId, String slug) {
    SubjectEntity ws = new SubjectEntity();
    ws.setName("Test " + slug);
    ws.setSlug(slug);
    ws.setOwnerUserId(ownerId);
    ws.setDefaultTimezone("Asia/Seoul");
    ws.setSettings(new HashMap<>());
    return subjectRepo.save(ws);
  }

  private void addMember(UUID subjectId, UUID userId, SubjectRole role) {
    SubjectMemberEntity member = new SubjectMemberEntity();
    member.setId(new SubjectMemberId(subjectId, userId));
    member.setRole(role);
    memberRepo.save(member);
  }

  private TabEntity createTab(UUID subjectId, UUID userId, String slug) {
    TabEntity tab = new TabEntity();
    tab.setSubjectId(subjectId);
    tab.setUserId(userId);
    tab.setName(slug);
    tab.setSlug(slug);
    tab.setSettings(new HashMap<>());
    return tabRepo.save(tab);
  }
}
