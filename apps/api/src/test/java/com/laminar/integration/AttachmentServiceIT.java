package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.attachment.AttachmentEntity;
import com.laminar.attachment.AttachmentParentType;
import com.laminar.attachment.AttachmentService;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.UserEntity;
import com.laminar.workspace.WorkspaceEntity;
import com.laminar.workspace.WorkspaceMemberEntity;
import com.laminar.workspace.WorkspaceMemberId;
import com.laminar.workspace.WorkspaceMemberRepository;
import com.laminar.workspace.WorkspaceRepository;
import com.laminar.workspace.WorkspaceRole;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class AttachmentServiceIT extends IsolationIntegrationBase {

  @Autowired AttachmentService attachmentService;
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
    a.setEmail("att-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName("Att WS");
    ws.setSlug("att-ws-" + UUID.randomUUID());
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
  void create_and_finalize_attachment() {
    UUID parentId = UUID.randomUUID();
    AttachmentEntity att =
        attachmentService.create(
            AttachmentParentType.CARD,
            parentId,
            "workspaces/"
                + workspaceId
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
    attachmentService.create(
        AttachmentParentType.PERPETUAL, cardId, "k3", "c.txt", "text/plain", 30L, null);

    assertThat(attachmentService.listByParent(AttachmentParentType.CARD, cardId)).hasSize(2);
    assertThat(attachmentService.listByParent(AttachmentParentType.PERPETUAL, cardId)).hasSize(1);
  }
}
