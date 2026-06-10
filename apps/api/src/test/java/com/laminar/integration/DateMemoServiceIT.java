package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.context.SubjectRole;
import com.laminar.datememo.application.DateMemoService;
import com.laminar.datememo.domain.DateMemoEntity;
import com.laminar.datememo.domain.DateMemoId;
import com.laminar.subject.domain.SubjectEntity;
import com.laminar.subject.domain.SubjectMemberEntity;
import com.laminar.subject.domain.SubjectMemberId;
import com.laminar.subject.repository.SubjectMemberRepository;
import com.laminar.subject.repository.SubjectRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.tab.application.TabService;
import com.laminar.user.domain.UserEntity;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class DateMemoServiceIT extends IsolationIntegrationBase {

  @Autowired TabService tabService;
  @Autowired DateMemoService dateMemoService;
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
    a.setEmail("dm-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    SubjectEntity ws = new SubjectEntity();
    ws.setName("DM WS");
    ws.setSlug("dm-ws-" + UUID.randomUUID());
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
  void upsert_creates_then_updates() {
    LocalDate today = LocalDate.of(2026, 6, 15);

    DateMemoEntity first = dateMemoService.upsert(tabId, today, "초안", null);
    // 같은 트랜잭션(영속성 컨텍스트)에선 두 번째 upsert가 동일 managed 엔티티를 갱신하므로
    // first 참조도 "수정본"이 된다 — 생성 시점 값은 호출 직후 스냅샷으로 검증.
    DateMemoId firstId = first.getId();
    String firstBody = first.getBodyMd();

    DateMemoEntity updated = dateMemoService.upsert(tabId, today, "수정본", null);

    assertThat(firstBody).isEqualTo("초안");
    assertThat(updated.getBodyMd()).isEqualTo("수정본");
    assertThat(updated.getId()).isEqualTo(firstId);
  }

  @Test
  @Transactional
  void list_by_range_returns_memos_in_window() {
    dateMemoService.upsert(tabId, LocalDate.of(2026, 6, 1), "june 1", null);
    dateMemoService.upsert(tabId, LocalDate.of(2026, 6, 15), "june 15", null);
    dateMemoService.upsert(tabId, LocalDate.of(2026, 7, 1), "july 1", null);

    assertThat(
            dateMemoService.listByTabDateRange(
                tabId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
        .hasSize(2);
  }
}
