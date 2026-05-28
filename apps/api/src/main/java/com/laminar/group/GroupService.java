package com.laminar.group;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 그룹 CRUD — Personal-First.
 *
 * 그룹은 board별 카드 묶음 (단기 목표). priority는 board별 자동 부여.
 */
@Service
public class GroupService {

    private static final int PRIORITY_STEP = 100;

    private final GroupRepository groupRepo;

    public GroupService(GroupRepository groupRepo) {
        this.groupRepo = groupRepo;
    }

    @Transactional
    public GroupEntity create(UUID boardId, String name, String color, Map<String, Object> attrs) {
        WorkspaceContext ctx = requirePersonalWritable();

        int nextPriority = groupRepo.findFirstByBoardIdAndDeletedAtIsNullOrderByPriorityDesc(boardId)
                .map(g -> g.getPriority() + PRIORITY_STEP)
                .orElse(PRIORITY_STEP);

        GroupEntity group = new GroupEntity();
        group.setWorkspaceId(ctx.workspaceId());
        group.setUserId(ctx.userId());
        group.setCreatedBy(ctx.userId());
        group.setBoardId(boardId);
        group.setName(name);
        group.setColor(color);
        group.setPriority(nextPriority);
        group.setAttrs(attrs == null ? new HashMap<>() : attrs);
        return groupRepo.save(group);
    }

    @Transactional(readOnly = true)
    public List<GroupEntity> listByBoard(UUID boardId) {
        WorkspaceContextHolder.require();
        return groupRepo.findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(boardId);
    }

    @Transactional(readOnly = true)
    public Optional<GroupEntity> findById(UUID groupId) {
        WorkspaceContextHolder.require();
        return groupRepo.findById(groupId).filter(g -> g.getDeletedAt() == null);
    }

    @Transactional
    public GroupEntity update(UUID groupId, String name, String color, Map<String, Object> attrs) {
        requirePersonalWritable();
        GroupEntity group = groupRepo.findById(groupId)
                .filter(g -> g.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("group not found: " + groupId));
        if (name != null && !name.isBlank()) group.setName(name);
        if (color != null) group.setColor(color);
        if (attrs != null) group.setAttrs(attrs);
        return groupRepo.save(group);
    }

    @Transactional
    public List<GroupEntity> reorder(UUID boardId, List<UUID> orderedGroupIds) {
        requirePersonalWritable();
        if (orderedGroupIds == null || orderedGroupIds.isEmpty()) {
            return List.of();
        }
        List<GroupEntity> result = new ArrayList<>(orderedGroupIds.size());
        for (int i = 0; i < orderedGroupIds.size(); i++) {
            UUID groupId = orderedGroupIds.get(i);
            int newPriority = (i + 1) * PRIORITY_STEP;
            groupRepo.findById(groupId)
                    .filter(g -> g.getDeletedAt() == null)
                    .filter(g -> boardId == null || boardId.equals(g.getBoardId()))
                    .ifPresent(g -> {
                        g.setPriority(newPriority);
                        result.add(groupRepo.save(g));
                    });
        }
        return result;
    }

    @Transactional
    public void softDelete(UUID groupId) {
        requirePersonalWritable();
        groupRepo.findById(groupId)
                .filter(g -> g.getDeletedAt() == null)
                .ifPresent(group -> {
                    group.setDeletedAt(OffsetDateTime.now());
                    groupRepo.save(group);
                });
    }

    private WorkspaceContext requirePersonalWritable() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
            throw new IllegalStateException("PERSONAL scope required");
        }
        if (!ctx.canWrite()) {
            throw new IllegalStateException("VIEWER cannot mutate groups");
        }
        return ctx;
    }
}
