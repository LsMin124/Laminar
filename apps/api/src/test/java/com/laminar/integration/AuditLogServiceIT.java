package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.laminar.audit.application.AuditLogService;
import com.laminar.audit.domain.AuditLogEntity;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.UserEntity;
import com.laminar.workspace.domain.WorkspaceEntity;
import com.laminar.workspace.domain.WorkspaceMemberEntity;
import com.laminar.workspace.domain.WorkspaceMemberId;
import com.laminar.workspace.domain.WorkspaceRole;
import com.laminar.workspace.repository.WorkspaceMemberRepository;
import com.laminar.workspace.repository.WorkspaceRepository;
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
  @Autowired WorkspaceRepository workspaceRepo;
  @Autowired WorkspaceMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID workspaceId;
  private UUID userA;

  @BeforeEach
  void seed() {
    WorkspaceContextHolder.clear();
    UserEntity a = new UserEntity();
    a.setEmail("audit-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName("Audit WS");
    ws.setSlug("audit-ws-" + UUID.randomUUID());
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
  }

  @AfterEach
  void cleanup() {
    WorkspaceContextHolder.clear();
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

    assertThat(entry.getWorkspaceId()).isEqualTo(workspaceId);
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
