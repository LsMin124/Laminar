package com.laminar.perpetual;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 영구노트 CRUD — Personal-First, parent_perpetual_id self-ref tree.
 *
 * Spec §2.4.5: tree depth ≤ 10. board/tab nullable (자유 메모).
 */
@Service
public class PerpetualNoteService {

    private static final int PRIORITY_STEP = 100;
    private static final int MAX_TREE_DEPTH = 10;

    private final PerpetualNoteRepository noteRepo;

    public PerpetualNoteService(PerpetualNoteRepository noteRepo) {
        this.noteRepo = noteRepo;
    }

    @Transactional
    public PerpetualNoteEntity create(
            UUID boardId,
            UUID tabId,
            UUID parentPerpetualId,
            String title,
            String bodyMd,
            Map<String, Object> attrs) {
        WorkspaceContext ctx = requirePersonalWritable();
        if (parentPerpetualId != null) {
            validateParent(parentPerpetualId);
        }

        int nextPriority = tabId == null
                ? PRIORITY_STEP
                : noteRepo.findFirstByTabIdAndDeletedAtIsNullOrderByPriorityDesc(tabId)
                        .map(n -> n.getPriority() + PRIORITY_STEP)
                        .orElse(PRIORITY_STEP);

        PerpetualNoteEntity note = new PerpetualNoteEntity();
        note.setWorkspaceId(ctx.workspaceId());
        note.setUserId(ctx.userId());
        note.setCreatedBy(ctx.userId());
        note.setBoardId(boardId);
        note.setTabId(tabId);
        note.setParentPerpetualId(parentPerpetualId);
        note.setTitle(title);
        note.setBodyMd(bodyMd);
        note.setPriority(nextPriority);
        note.setAttrs(attrs == null ? new HashMap<>() : attrs);
        return noteRepo.save(note);
    }

    @Transactional(readOnly = true)
    public List<PerpetualNoteEntity> listByBoard(UUID boardId) {
        WorkspaceContextHolder.require();
        return noteRepo.findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(boardId);
    }

    @Transactional(readOnly = true)
    public List<PerpetualNoteEntity> listByTab(UUID tabId) {
        WorkspaceContextHolder.require();
        return noteRepo.findByTabIdAndDeletedAtIsNullOrderByPriorityAsc(tabId);
    }

    @Transactional(readOnly = true)
    public List<PerpetualNoteEntity> listRootsByTab(UUID tabId) {
        WorkspaceContextHolder.require();
        return noteRepo.findByTabIdAndParentPerpetualIdIsNullAndDeletedAtIsNullOrderByPriorityAsc(tabId);
    }

    @Transactional(readOnly = true)
    public List<PerpetualNoteEntity> listChildren(UUID parentId) {
        WorkspaceContextHolder.require();
        return noteRepo.findByParentPerpetualIdAndDeletedAtIsNullOrderByPriorityAsc(parentId);
    }

    @Transactional(readOnly = true)
    public Optional<PerpetualNoteEntity> findById(UUID noteId) {
        WorkspaceContextHolder.require();
        return noteRepo.findById(noteId).filter(n -> n.getDeletedAt() == null);
    }

    @Transactional
    public PerpetualNoteEntity update(
            UUID noteId,
            String title,
            String bodyMd,
            UUID parentPerpetualId,
            Map<String, Object> attrs) {
        requirePersonalWritable();
        PerpetualNoteEntity note = noteRepo.findById(noteId)
                .filter(n -> n.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("note not found: " + noteId));

        if (title != null && !title.isBlank()) note.setTitle(title);
        if (bodyMd != null) note.setBodyMd(bodyMd);
        if (parentPerpetualId != null) {
            if (Objects.equals(parentPerpetualId, noteId)) {
                throw new IllegalArgumentException("parent_perpetual_id cannot equal self");
            }
            validateParent(parentPerpetualId);
            note.setParentPerpetualId(parentPerpetualId);
        }
        if (attrs != null) note.setAttrs(attrs);
        return noteRepo.save(note);
    }

    @Transactional
    public List<PerpetualNoteEntity> reorder(UUID tabId, List<UUID> orderedNoteIds) {
        requirePersonalWritable();
        if (orderedNoteIds == null || orderedNoteIds.isEmpty()) return List.of();
        List<PerpetualNoteEntity> result = new ArrayList<>(orderedNoteIds.size());
        for (int i = 0; i < orderedNoteIds.size(); i++) {
            UUID noteId = orderedNoteIds.get(i);
            int newPriority = (i + 1) * PRIORITY_STEP;
            noteRepo.findById(noteId)
                    .filter(n -> n.getDeletedAt() == null)
                    .filter(n -> tabId == null || tabId.equals(n.getTabId()))
                    .ifPresent(n -> {
                        n.setPriority(newPriority);
                        result.add(noteRepo.save(n));
                    });
        }
        return result;
    }

    @Transactional
    public void softDelete(UUID noteId) {
        requirePersonalWritable();
        noteRepo.findById(noteId)
                .filter(n -> n.getDeletedAt() == null)
                .ifPresent(note -> {
                    note.setDeletedAt(OffsetDateTime.now());
                    noteRepo.save(note);
                });
    }

    private void validateParent(UUID parentPerpetualId) {
        PerpetualNoteEntity parent = noteRepo.findById(parentPerpetualId)
                .filter(n -> n.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("parent note not found"));
        int depth = 1;
        UUID cursor = parent.getParentPerpetualId();
        while (cursor != null) {
            depth++;
            if (depth >= MAX_TREE_DEPTH) {
                throw new IllegalArgumentException("perpetual tree depth exceeds " + MAX_TREE_DEPTH);
            }
            UUID next = noteRepo.findById(cursor).map(PerpetualNoteEntity::getParentPerpetualId).orElse(null);
            cursor = next;
        }
    }

    private WorkspaceContext requirePersonalWritable() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
            throw new IllegalStateException("PERSONAL scope required");
        }
        if (!ctx.canWrite()) {
            throw new IllegalStateException("VIEWER cannot mutate perpetual notes");
        }
        return ctx;
    }
}
