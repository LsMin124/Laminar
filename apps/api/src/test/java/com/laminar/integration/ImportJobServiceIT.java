package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.error.ConflictException;
import com.laminar.outbox.application.ImportJobService;
import com.laminar.outbox.domain.ImportJobEntity;
import com.laminar.outbox.domain.ImportJobStatus;
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

class ImportJobServiceIT extends IsolationIntegrationBase {

  @Autowired ImportJobService importService;
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
    a.setEmail("imp-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    SubjectEntity ws = new SubjectEntity();
    ws.setName("Imp WS");
    ws.setSlug("imp-ws-" + UUID.randomUUID());
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
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("PENDING");
  }

  @Test
  @Transactional
  void cancel_after_complete_rejected() {
    ImportJobEntity job = importService.createPending();
    importService.start(job.getId());
    importService.complete(job.getId(), null);

    assertThatThrownBy(() -> importService.cancel(job.getId()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("terminal");
  }
}
