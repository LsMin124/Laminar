package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.audit.application.AuditLogService;
import com.laminar.audit.domain.AuditLogEntity;
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
import com.laminar.user.domain.UserEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class AuditLogServiceIT extends IsolationIntegrationBase {

  @Autowired AuditLogService auditService;
  @Autowired UserSystemRepository userRepo;
  @Autowired SubjectRepository subjectRepo;
  @Autowired SubjectMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID subjectId;
  private UUID userA;

  @BeforeEach
  void seed() {
    SubjectContextHolder.clear();
    UserEntity a = new UserEntity();
    a.setEmail("audit-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    SubjectEntity ws = new SubjectEntity();
    ws.setName("Audit WS");
    ws.setSlug("audit-ws-" + UUID.randomUUID());
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
  }

  @AfterEach
  void cleanup() {
    SubjectContextHolder.clear();
  }

  @Test
  @Transactional
  void append_creates_audit_with_actor_and_workspace() {
    AuditLogEntity entry =
        auditService.append(
            null,
            "card.created",
            "card",
            UUID.randomUUID(),
            "user created card",
            Map.of("title", "test"));

    assertThat(entry.getSubjectId()).isEqualTo(subjectId);
    assertThat(entry.getActorUserId()).isEqualTo(userA);
    assertThat(entry.getAction()).isEqualTo("card.created");
    assertThat(entry.getOccurredAt()).isNotNull();
  }

  @Test
  @Transactional
  void list_recent_returns_in_desc_order() {
    auditService.append(null, "a.1", null, null, "first", null);
    auditService.append(null, "a.2", null, null, "second", null);
    auditService.append(null, "a.3", null, null, "third", null);

    assertThat(auditService.listRecent(10)).extracting(AuditLogEntity::getAction).startsWith("a.3");
  }
}
