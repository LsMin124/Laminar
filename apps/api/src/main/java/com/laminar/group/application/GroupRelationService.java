package com.laminar.group.application;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.group.domain.GroupEntity;
import com.laminar.group.domain.GroupRelationEntity;
import com.laminar.group.repository.GroupRelationRepository;
import com.laminar.group.repository.GroupRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 사이 화살표 (관계 시각화).
 *
 * <p>검증: - from_group_id != to_group_id (DB chk_group_relations_self 일치) - 같은 board에 속한 두 그룹만 연결 가능
 * (도메인 invariant)
 */
@Service
public class GroupRelationService {

  private final GroupRelationRepository relationRepo;
  private final GroupRepository groupRepo;

  public GroupRelationService(GroupRelationRepository relationRepo, GroupRepository groupRepo) {
    this.relationRepo = relationRepo;
    this.groupRepo = groupRepo;
  }

  @Transactional
  public GroupRelationEntity create(
      UUID fromGroupId,
      UUID toGroupId,
      String relationKind,
      String summary,
      String bodyMd,
      Map<String, Object> attrs) {
    WorkspaceContext ctx = requirePersonalWritable();
    if (Objects.equals(fromGroupId, toGroupId)) {
      throw new IllegalArgumentException("from_group_id == to_group_id is not allowed");
    }
    GroupEntity from =
        groupRepo
            .findById(fromGroupId)
            .filter(g -> g.getDeletedAt() == null)
            .filter(g -> ctx.ownsPersonal(g.getWorkspaceId(), g.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("from group not found"));
    GroupEntity to =
        groupRepo
            .findById(toGroupId)
            .filter(g -> g.getDeletedAt() == null)
            .filter(g -> ctx.ownsPersonal(g.getWorkspaceId(), g.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("to group not found"));
    if (!Objects.equals(from.getBoardId(), to.getBoardId())) {
      throw new IllegalArgumentException("from/to groups must belong to the same board");
    }

    GroupRelationEntity relation = new GroupRelationEntity();
    relation.setWorkspaceId(ctx.workspaceId());
    relation.setUserId(ctx.userId());
    relation.setCreatedBy(ctx.userId());
    relation.setBoardId(from.getBoardId());
    relation.setFromGroupId(fromGroupId);
    relation.setToGroupId(toGroupId);
    relation.setRelationKind(
        relationKind == null || relationKind.isBlank() ? "default" : relationKind);
    relation.setSummary(summary);
    relation.setBodyMd(bodyMd);
    relation.setAttrs(attrs == null ? new HashMap<>() : attrs);
    return relationRepo.save(relation);
  }

  @Transactional(readOnly = true)
  public List<GroupRelationEntity> listByBoard(UUID boardId) {
    WorkspaceContextHolder.requirePersonal();
    return relationRepo.findByBoardIdAndDeletedAtIsNull(boardId);
  }

  @Transactional
  public void softDelete(UUID relationId) {
    WorkspaceContext ctx = requirePersonalWritable();
    relationRepo
        .findById(relationId)
        .filter(r -> r.getDeletedAt() == null)
        .filter(r -> ctx.ownsPersonal(r.getWorkspaceId(), r.getUserId()))
        .ifPresent(
            r -> {
              r.setDeletedAt(OffsetDateTime.now());
              relationRepo.save(r);
            });
  }

  private WorkspaceContext requirePersonalWritable() {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    if (!ctx.canWrite()) {
      throw new IllegalStateException("VIEWER cannot mutate relations");
    }
    return ctx;
  }
}
