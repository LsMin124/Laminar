package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.laminar.attachment.application.AttachmentService;
import com.laminar.attachment.domain.AttachmentEntity;
import com.laminar.attachment.domain.AttachmentParentType;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

class AttachmentServiceIT extends IsolationIntegrationBase {

  @Autowired AttachmentService attachmentService;
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
    a.setEmail("att-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    SubjectEntity ws = new SubjectEntity();
    ws.setName("Att WS");
    ws.setSlug("att-ws-" + UUID.randomUUID());
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
  void create_and_finalize_attachment() {
    UUID parentId = UUID.randomUUID();
    AttachmentEntity att =
        attachmentService.create(
            AttachmentParentType.CARD,
            parentId,
            "workspaces/"
                + subjectId
                + "/users/"
                + userA
                + "/attachments/"
                + UUID.randomUUID()
                + "/file.pdf",
            "file.pdf",
            "application/pdf",
            1024L,
            "sha-abc");
    assertThat(att.isAccessCheckRequired()).isTrue();

    // N-4: finalize는 클라이언트 자칭 크기를 무시하고 R2 HEAD 실측을 쓴다 — mock으로 2048B 응답.
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenReturn(HeadObjectResponse.builder().contentLength(2048L).build());

    AttachmentEntity finalized = attachmentService.finalizeUpload(att.getId(), 2048L, "sha-xyz");

    assertThat(finalized.isAccessCheckRequired()).isFalse();
    assertThat(finalized.getSizeBytes()).isEqualTo(2048L);
    assertThat(finalized.getSha256()).isEqualTo("sha-xyz");
  }

  @Test
  @Transactional
  void size_over_20mb_rejected() {
    UUID parentId = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                attachmentService.create(
                    AttachmentParentType.CARD,
                    parentId,
                    "key",
                    "huge.bin",
                    "application/octet-stream",
                    21L * 1024 * 1024,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exceeds");
  }

  @Test
  @Transactional
  void list_by_parent_filters_correctly() {
    UUID cardId = UUID.randomUUID();
    attachmentService.create(
        AttachmentParentType.CARD, cardId, "k1", "a.txt", "text/plain", 10L, null);
    attachmentService.create(
        AttachmentParentType.CARD, cardId, "k2", "b.txt", "text/plain", 20L, null);

    assertThat(attachmentService.listByParent(AttachmentParentType.CARD, cardId)).hasSize(2);
  }
}
