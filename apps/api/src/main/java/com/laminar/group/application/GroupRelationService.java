package com.laminar.group.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
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
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("relations");
    if (Objects.equals(fromGroupId, toGroupId)) {
      throw new IllegalArgumentException("from_group_id == to_group_id is not allowed");
    }
    GroupEntity from = groupRepo.findOwnedActiveOrThrow(fromGroupId, ctx, "from group");
    GroupEntity to = groupRepo.findOwnedActiveOrThrow(toGroupId, ctx, "to group");
    if (!Objects.equals(from.getTabId(), to.getTabId())) {
      throw new IllegalArgumentException("from/to groups must belong to the same tab");
    }

    GroupRelationEntity relation = new GroupRelationEntity();
    relation.setSubjectId(ctx.subjectId());
    relation.setUserId(ctx.userId());
    relation.setCreatedBy(ctx.userId());
    relation.setTabId(from.getTabId());
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
  public List<GroupRelationEntity> listByTab(UUID tabId) {
    SubjectContextHolder.requirePersonal();
    return relationRepo.findByTabIdAndDeletedAtIsNull(tabId);
  }

  /**
   * 엣지 라벨(summary) 수정. summary 자체가 이 화살표가 나타내는 관계를 표현한다(별도 relation_kind 분류 없음). null/빈 값이면 라벨 제거.
   */
  @Transactional
  public GroupRelationEntity update(UUID relationId, String summary) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("relations");
    GroupRelationEntity relation = relationRepo.findOwnedActiveOrThrow(relationId, ctx, "relation");
    relation.setSummary(summary == null || summary.isBlank() ? null : summary);
    return relationRepo.save(relation);
  }

  @Transactional
  public void softDelete(UUID relationId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("relations");
    relationRepo
        .findOwnedActive(relationId, ctx)
        .ifPresent(
            r -> {
              r.setDeletedAt(OffsetDateTime.now());
              relationRepo.save(r);
            });
  }
}
