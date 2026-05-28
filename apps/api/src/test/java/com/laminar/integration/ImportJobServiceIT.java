package com.laminar.integration;

import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.outbox.ImportJobEntity;
import com.laminar.outbox.ImportJobService;
import com.laminar.outbox.ImportJobStatus;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.UserEntity;
import com.laminar.workspace.WorkspaceEntity;
import com.laminar.workspace.WorkspaceMemberEntity;
import com.laminar.workspace.WorkspaceMemberId;
import com.laminar.workspace.WorkspaceMemberRepository;
import com.laminar.workspace.WorkspaceRepository;
import com.laminar.workspace.WorkspaceRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportJobServiceIT extends IsolationIntegrationBase {

    @Autowired ImportJobService importService;
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
        a.setEmail("imp-a-" + UUID.randomUUID() + "@test.local");
        userA = userRepo.save(a).getId();

        WorkspaceEntity ws = new WorkspaceEntity();
        ws.setName("Imp WS");
        ws.setSlug("imp-ws-" + UUID.randomUUID());
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
    void status_transitions_pending_running_completed() {
        ImportJobEntity job = importService.createPending();
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.PENDING);
        assertThat(job.getImportToken()).isNotBlank();

        ImportJobEntity running = importService.start(job.getId());
        assertThat(running.getStatus()).isEqualTo(ImportJobStatus.RUNNING);
        assertThat(running.getStartedAt()).isNotNull();

        ImportJobEntity completed = importService.complete(job.getId(), Map.of("imported", 42));
        assertThat(completed.getStatus()).isEqualTo(ImportJobStatus.COMPLETED);
        assertThat(completed.getFinishedAt()).isNotNull();
        assertThat(completed.getProgress()).containsEntry("imported", 42);
    }

    @Test
    @Transactional
    void start_from_terminal_status_rejected() {
        ImportJobEntity job = importService.createPending();
        importService.start(job.getId());
        importService.complete(job.getId(), null);

        assertThatThrownBy(() -> importService.start(job.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @Transactional
    void cancel_after_complete_rejected() {
        ImportJobEntity job = importService.createPending();
        importService.start(job.getId());
        importService.complete(job.getId(), null);

        assertThatThrownBy(() -> importService.cancel(job.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
    }
}
