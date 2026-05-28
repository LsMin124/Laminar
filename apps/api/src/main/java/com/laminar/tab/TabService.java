package com.laminar.tab;

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
 * 탭 CRUD — Personal-First, parent_tab_id self-ref tree.
 *
 * Tree depth ≤ 10 검증 (Spec §2.4.5 추정). parent_tab_id 사이클은 self-ref 차단만 (실제 사이클은
 * 트리 구조상 발생 불가하지만 service에서 직속 부모 검증).
 *
 * Personal-First 격리: parent_tab_id는 자기 user의 탭이어야 (parent 조회 시 @Filter 자동).
 */
@Service
public class TabService {

    private static final int PRIORITY_STEP = 100;
    private static final int MAX_TREE_DEPTH = 10;

    private final TabRepository tabRepo;

    public TabService(TabRepository tabRepo) {
        this.tabRepo = tabRepo;
    }

    @Transactional
    public TabEntity create(
            UUID boardId,
            UUID parentTabId,
            String name,
            Boolean visible,
            Boolean collapsed,
            Boolean showLabel,
            String labelColor,
            Map<String, Object> attrs) {
        WorkspaceContext ctx = requirePersonalWritable();
        if (parentTabId != null) {
            validateParent(boardId, parentTabId);
        }

        int nextPriority = tabRepo.findFirstByBoardIdAndDeletedAtIsNullOrderByPriorityDesc(boardId)
                .map(t -> t.getPriority() + PRIORITY_STEP)
                .orElse(PRIORITY_STEP);

        TabEntity tab = new TabEntity();
        tab.setWorkspaceId(ctx.workspaceId());
        tab.setUserId(ctx.userId());
        tab.setCreatedBy(ctx.userId());
        tab.setBoardId(boardId);
        tab.setParentTabId(parentTabId);
        tab.setName(name);
        tab.setPriority(nextPriority);
        tab.setVisible(visible == null ? true : visible);
        tab.setCollapsed(collapsed == null ? false : collapsed);
        tab.setShowLabel(showLabel == null ? false : showLabel);
        tab.setLabelColor(labelColor);
        tab.setAttrs(attrs == null ? new HashMap<>() : attrs);
        return tabRepo.save(tab);
    }

    @Transactional(readOnly = true)
    public List<TabEntity> listByBoard(UUID boardId) {
        WorkspaceContextHolder.require();
        return tabRepo.findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(boardId);
    }

    @Transactional(readOnly = true)
    public List<TabEntity> listRootsByBoard(UUID boardId) {
        WorkspaceContextHolder.require();
        return tabRepo.findByBoardIdAndParentTabIdIsNullAndDeletedAtIsNullOrderByPriorityAsc(boardId);
    }

    @Transactional(readOnly = true)
    public List<TabEntity> listChildren(UUID parentTabId) {
        WorkspaceContextHolder.require();
        return tabRepo.findByParentTabIdAndDeletedAtIsNullOrderByPriorityAsc(parentTabId);
    }

    @Transactional(readOnly = true)
    public Optional<TabEntity> findById(UUID tabId) {
        WorkspaceContextHolder.require();
        return tabRepo.findById(tabId).filter(t -> t.getDeletedAt() == null);
    }

    @Transactional
    public TabEntity update(
            UUID tabId,
            String name,
            UUID parentTabId,
            Boolean visible,
            Boolean collapsed,
            Boolean showLabel,
            String labelColor,
            Map<String, Object> attrs) {
        requirePersonalWritable();
        TabEntity tab = tabRepo.findById(tabId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("tab not found: " + tabId));

        if (name != null && !name.isBlank()) tab.setName(name);
        if (parentTabId != null) {
            if (Objects.equals(parentTabId, tabId)) {
                throw new IllegalArgumentException("parent_tab_id cannot equal self");
            }
            validateParent(tab.getBoardId(), parentTabId);
            tab.setParentTabId(parentTabId);
        }
        if (visible != null) tab.setVisible(visible);
        if (collapsed != null) tab.setCollapsed(collapsed);
        if (showLabel != null) tab.setShowLabel(showLabel);
        if (labelColor != null) tab.setLabelColor(labelColor);
        if (attrs != null) tab.setAttrs(attrs);
        return tabRepo.save(tab);
    }

    @Transactional
    public List<TabEntity> reorder(UUID boardId, List<UUID> orderedTabIds) {
        requirePersonalWritable();
        if (orderedTabIds == null || orderedTabIds.isEmpty()) return List.of();
        List<TabEntity> result = new ArrayList<>(orderedTabIds.size());
        for (int i = 0; i < orderedTabIds.size(); i++) {
            UUID tabId = orderedTabIds.get(i);
            int newPriority = (i + 1) * PRIORITY_STEP;
            tabRepo.findById(tabId)
                    .filter(t -> t.getDeletedAt() == null)
                    .filter(t -> boardId == null || boardId.equals(t.getBoardId()))
                    .ifPresent(t -> {
                        t.setPriority(newPriority);
                        result.add(tabRepo.save(t));
                    });
        }
        return result;
    }

    @Transactional
    public void softDelete(UUID tabId) {
        requirePersonalWritable();
        tabRepo.findById(tabId)
                .filter(t -> t.getDeletedAt() == null)
                .ifPresent(tab -> {
                    tab.setDeletedAt(OffsetDateTime.now());
                    tabRepo.save(tab);
                });
    }

    /**
     * 부모 탭 존재 + 같은 board + tree depth ≤ MAX_TREE_DEPTH 검증.
     */
    private void validateParent(UUID boardId, UUID parentTabId) {
        TabEntity parent = tabRepo.findById(parentTabId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("parent tab not found"));
        if (!Objects.equals(boardId, parent.getBoardId())) {
            throw new IllegalArgumentException("parent tab must be on the same board");
        }
        int depth = 1;
        UUID cursor = parent.getParentTabId();
        while (cursor != null) {
            depth++;
            if (depth >= MAX_TREE_DEPTH) {
                throw new IllegalArgumentException("tab tree depth exceeds " + MAX_TREE_DEPTH);
            }
            UUID next = tabRepo.findById(cursor)
                    .map(TabEntity::getParentTabId)
                    .orElse(null);
            cursor = next;
        }
    }

    private WorkspaceContext requirePersonalWritable() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
            throw new IllegalStateException("PERSONAL scope required");
        }
        if (!ctx.canWrite()) {
            throw new IllegalStateException("VIEWER cannot mutate tabs");
        }
        return ctx;
    }
}
