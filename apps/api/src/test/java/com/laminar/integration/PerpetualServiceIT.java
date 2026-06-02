package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.board.BoardService;
import com.laminar.context.HibernateFilterActivator;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.perpetual.PerpetualColumnDefinitionEntity;
import com.laminar.perpetual.PerpetualColumnService;
import com.laminar.perpetual.PerpetualColumnType;
import com.laminar.perpetual.PerpetualNoteEntity;
import com.laminar.perpetual.PerpetualNoteService;
import com.laminar.perpetual.PerpetualVersionEntity;
import com.laminar.perpetual.PerpetualVersionService;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.UserEntity;
import com.laminar.workspace.WorkspaceEntity;
import com.laminar.workspace.WorkspaceMemberEntity;
import com.laminar.workspace.WorkspaceMemberId;
import com.laminar.workspace.WorkspaceMemberRepository;
import com.laminar.workspace.WorkspaceRepository;
import com.laminar.workspace.WorkspaceRole;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class PerpetualServiceIT extends IsolationIntegrationBase {

  @Autowired BoardService boardService;
  @Autowired PerpetualNoteService noteService;
  @Autowired PerpetualColumnService columnService;
  @Autowired PerpetualVersionService versionService;
  @Autowired UserSystemRepository userRepo;
  @Autowired WorkspaceRepository workspaceRepo;
  @Autowired WorkspaceMemberRepository memberRepo;
  @Autowired HibernateFilterActivator filterActivator;

  private UUID workspaceId;
  private UUID userA;
  private UUID boardId;

  @BeforeEach
  void seed() {
    WorkspaceContextHolder.clear();
    UserEntity a = new UserEntity();
    a.setEmail("perp-a-" + UUID.randomUUID() + "@test.local");
    userA = userRepo.save(a).getId();

    WorkspaceEntity ws = new WorkspaceEntity();
    ws.setName("Perp WS");
    ws.setSlug("perp-ws-" + UUID.randomUUID());
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
    boardId = boardService.create("B", "b-" + UUID.randomUUID(), null, null, null, null).getId();
  }

  @AfterEach
  void cleanup() {
    WorkspaceContextHolder.clear();
  }

  @Test
  @Transactional
  void perpetual_note_tree_root_and_children() {
    PerpetualNoteEntity root = noteService.create(boardId, null, null, "Root", null, null);
    PerpetualNoteEntity child =
        noteService.create(boardId, null, root.getId(), "Child", null, null);

    assertThat(child.getParentPerpetualId()).isEqualTo(root.getId());
    assertThat(noteService.listChildren(root.getId()))
        .extracting(PerpetualNoteEntity::getId)
        .containsExactly(child.getId());
  }

  @Test
  @Transactional
  void column_definition_duplicate_name_rejected() {
    columnService.createDefinition(boardId, "Status", PerpetualColumnType.TEXT, null);

    assertThatThrownBy(
            () -> columnService.createDefinition(boardId, "Status", PerpetualColumnType.TEXT, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  @Transactional
  void dropdown_value_must_match_enum() {
    PerpetualNoteEntity note = noteService.create(boardId, null, null, "Note", null, null);
    PerpetualColumnDefinitionEntity dropdown =
        columnService.createDefinition(
            boardId, "Stage", PerpetualColumnType.DROPDOWN, List.of("draft", "review", "done"));

    columnService.upsertValue(note.getId(), dropdown.getId(), "review");

    assertThatThrownBy(() -> columnService.upsertValue(note.getId(), dropdown.getId(), "unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("enum_values");
  }

  @Test
  @Transactional
  void version_commit_increments_and_mark_current_swaps() {
    PerpetualNoteEntity note = noteService.create(boardId, null, null, "Versioned", null, null);

    PerpetualVersionEntity v1 =
        versionService.commit(note.getId(), null, "v1 summary", "diff-1", true);
    PerpetualVersionEntity v2 =
        versionService.commit(note.getId(), null, "v2 summary", "diff-2", true);

    assertThat(v1.getVersionNumber()).isEqualTo(1);
    assertThat(v2.getVersionNumber()).isEqualTo(2);
    assertThat(versionService.listByNote(note.getId()))
        .extracting(PerpetualVersionEntity::isCurrentDiff)
        .containsExactly(true, false);
  }
}
