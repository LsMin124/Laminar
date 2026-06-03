package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.laminar.tab.domain.TabEntity;
import com.laminar.tab.repository.TabRepository;
import com.laminar.user.domain.UserEntity;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * TabService 통합 검증 (실제 PostgreSQL + Flyway + @Filter).
 *
 * <p>검증: - create: priority 자동 부여 + subject/user 자동 set - listActive: priority 정렬 + soft-deleted 제외
 * - reorder: 배치 priority 갱신 - 격리: 다른 user의 board는 listActive에 노출 안 됨 - VIEWER 권한은 create 차단
 */
class TabServiceIT extends IsolationIntegrationBase {

  @Autowired TabService tabService;
  @Autowired TabRepository tabRepo;
  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID subjectId;
  private UUID userA;
  private UUID userB;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();

    UserEntity a = new UserEntity();
    a.setEmail("tab-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    UserEntity b = new UserEntity();
    b.setEmail("tab-b-" + UUID.randomUUID() + "@test.local");
    userB = userRepo.save(b).getId();

    SubjectEntity ws = new SubjectEntity();
    ws.setName("Test WS");
    ws.setSlug("test-ws-" + UUID.randomUUID());
    ws.setOwnerUserId(userA);
    ws.setDefaultTimezone("Asia/Seoul");
    ws.setSettings(new HashMap<>());
    subjectId = subjectRepo.save(ws).getId();

    addMember(subjectId, userA, SubjectRole.OWNER);
    addMember(subjectId, userB, SubjectRole.MEMBER);
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  @Transactional
  void create_assigns_priority_and_context_ownership() {
    enterAsOwner();

    TabEntity first = tabService.create("First", "first", null, null, null, null);
    TabEntity second = tabService.create("Second", "second", null, null, null, null);

    assertThat(first.getSubjectId()).isEqualTo(subjectId);
    assertThat(first.getUserId()).isEqualTo(userA);
    assertThat(second.getPriority()).isEqualTo(first.getPriority() + 100);
  }

  @Test
  @Transactional
  void list_filters_cross_user_boards() {
    enterAsOwner();
    tabService.create("UserA Tab", "a-tab", null, null, null, null);

    SubjectContextHolder.set(SubjectContext.personal(subjectId, userB, SubjectRole.MEMBER));
    filterActivator.activate();
    tabService.create("UserB Tab", "b-tab", null, null, null, null);

    SubjectContextHolder.set(SubjectContext.personal(subjectId, userA, SubjectRole.OWNER));
    filterActivator.activate();
    List<TabEntity> visibleToA = tabService.listActive();

    assertThat(visibleToA).extracting(TabEntity::getName).containsExactly("UserA Tab");
  }

  @Test
  @Transactional
  void reorder_applies_batch_priority() {
    enterAsOwner();
    TabEntity b1 = tabService.create("B1", "b1", null, null, null, null);
    TabEntity b2 = tabService.create("B2", "b2", null, null, null, null);
    TabEntity b3 = tabService.create("B3", "b3", null, null, null, null);

    List<TabEntity> reordered = tabService.reorder(List.of(b3.getId(), b1.getId(), b2.getId()));

    assertThat(reordered).hasSize(3);
    assertThat(reordered.get(0).getPriority()).isEqualTo(100);
    assertThat(reordered.get(1).getPriority()).isEqualTo(200);
    assertThat(reordered.get(2).getPriority()).isEqualTo(300);
  }

  @Test
  @Transactional
  void viewer_cannot_mutate() {
    SubjectContextHolder.set(SubjectContext.personal(subjectId, userA, SubjectRole.VIEWER));
    filterActivator.activate();

    assertThatThrownBy(() -> tabService.create("X", "x", null, null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("VIEWER");
  }

  private void enterAsOwner() {
    SubjectContextHolder.set(SubjectContext.personal(subjectId, userA, SubjectRole.OWNER));
    filterActivator.activate();
  }

  private void addMember(UUID wsId, UUID uId, SubjectRole role) {
    SubjectMemberEntity member = new SubjectMemberEntity();
    member.setId(new SubjectMemberId(wsId, uId));
    member.setRole(role);
    memberRepo.save(member);
  }
}
