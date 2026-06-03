package com.laminar.group.application;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.group.domain.GroupEntity;
import com.laminar.group.domain.GroupMemberEntity;
import com.laminar.group.domain.GroupMemberId;
import com.laminar.group.repository.GroupMemberRepository;
import com.laminar.group.repository.GroupRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 CRUD — Personal-First.
 *
 * <p>그룹은 board별 카드 묶음 (단기 목표). priority는 board별 자동 부여.
 */
@Service
public class GroupService {

  private static final int PRIORITY_STEP = 100;

  private final GroupRepository groupRepo;
  private final GroupMemberRepository memberRepo;
  private final com.laminar.card.CardRepository cardRepo;

  public GroupService(
      GroupRepository groupRepo,
      GroupMemberRepository memberRepo,
      com.laminar.card.CardRepository cardRepo) {
    this.groupRepo = groupRepo;
    this.memberRepo = memberRepo;
    this.cardRepo = cardRepo;
  }

  @Transactional
  public GroupEntity create(UUID boardId, String name, String color, Map<String, Object> attrs) {
    WorkspaceContext ctx = requirePersonalWritable();

    int nextPriority =
        groupRepo
            .findFirstByBoardIdAndDeletedAtIsNullOrderByPriorityDesc(boardId)
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
    WorkspaceContextHolder.requirePersonal();
    return groupRepo.findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(boardId);
  }

  @Transactional(readOnly = true)
  public Optional<GroupEntity> findById(UUID groupId) {
    WorkspaceContext ctx = WorkspaceContextHolder.requirePersonal();
    return groupRepo
        .findById(groupId)
        .filter(g -> g.getDeletedAt() == null)
        .filter(g -> ctx.ownsPersonal(g.getWorkspaceId(), g.getUserId()));
  }

  @Transactional
  public GroupEntity update(UUID groupId, String name, String color, Map<String, Object> attrs) {
    WorkspaceContext ctx = requirePersonalWritable();
    GroupEntity group =
        groupRepo
            .findById(groupId)
            .filter(g -> g.getDeletedAt() == null)
            .filter(g -> ctx.ownsPersonal(g.getWorkspaceId(), g.getUserId()))
            .orElseThrow(() -> new IllegalArgumentException("group not found: " + groupId));
    if (name != null && !name.isBlank()) group.setName(name);
    if (color != null) group.setColor(color);
    if (attrs != null) group.setAttrs(attrs);
    return groupRepo.save(group);
  }

  @Transactional
  public List<GroupEntity> reorder(UUID boardId, List<UUID> orderedGroupIds) {
    WorkspaceContext ctx = requirePersonalWritable();
    if (orderedGroupIds == null || orderedGroupIds.isEmpty()) {
      return List.of();
    }
    List<GroupEntity> result = new ArrayList<>(orderedGroupIds.size());
    for (int i = 0; i < orderedGroupIds.size(); i++) {
      UUID groupId = orderedGroupIds.get(i);
      int newPriority = (i + 1) * PRIORITY_STEP;
      groupRepo
          .findById(groupId)
          .filter(g -> g.getDeletedAt() == null)
          .filter(g -> ctx.ownsPersonal(g.getWorkspaceId(), g.getUserId()))
          .filter(g -> boardId == null || boardId.equals(g.getBoardId()))
          .ifPresent(
              g -> {
                g.setPriority(newPriority);
                result.add(groupRepo.save(g));
              });
    }
    return result;
  }

  @Transactional
  public void softDelete(UUID groupId) {
    WorkspaceContext ctx = requirePersonalWritable();
    groupRepo
        .findById(groupId)
        .filter(g -> g.getDeletedAt() == null)
        .filter(g -> ctx.ownsPersonal(g.getWorkspaceId(), g.getUserId()))
        .ifPresent(
            group -> {
              group.setDeletedAt(OffsetDateTime.now());
              groupRepo.save(group);
            });
  }

  /** 그룹 ↔ 카드 멤버십 추가. group/card는 현재 user의 자원이어야 (Personal-First 격리 자동). */
  @Transactional
  public GroupMemberEntity addMember(UUID groupId, UUID cardId) {
    WorkspaceContext ctx = requirePersonalWritable();
    groupRepo
        .findById(groupId)
        .filter(g -> g.getDeletedAt() == null)
        .filter(g -> ctx.ownsPersonal(g.getWorkspaceId(), g.getUserId()))
        .orElseThrow(() -> new IllegalArgumentException("group not found: " + groupId));
    cardRepo
        .findById(cardId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsPersonal(c.getWorkspaceId(), c.getUserId()))
        .orElseThrow(() -> new IllegalArgumentException("card not found: " + cardId));

    GroupMemberEntity member = new GroupMemberEntity();
    member.setId(new GroupMemberId(groupId, cardId));
    member.setAddedBy(ctx.userId());
    return memberRepo.save(member);
  }

  @Transactional
  public void removeMember(UUID groupId, UUID cardId) {
    WorkspaceContext ctx = requirePersonalWritable();
    // group/card 격리 검증 후 삭제 — 다른 user 그룹은 소유권 불일치로 빈 Optional
    groupRepo
        .findById(groupId)
        .filter(g -> g.getDeletedAt() == null)
        .filter(g -> ctx.ownsPersonal(g.getWorkspaceId(), g.getUserId()))
        .orElseThrow(() -> new IllegalArgumentException("group not found: " + groupId));
    memberRepo.findById(new GroupMemberId(groupId, cardId)).ifPresent(memberRepo::delete);
  }

  @Transactional(readOnly = true)
  public List<UUID> listCardIdsInGroup(UUID groupId) {
    WorkspaceContext ctx = WorkspaceContextHolder.requirePersonal();
    groupRepo
        .findById(groupId)
        .filter(g -> g.getDeletedAt() == null)
        .filter(g -> ctx.ownsPersonal(g.getWorkspaceId(), g.getUserId()))
        .orElseThrow(() -> new IllegalArgumentException("group not found: " + groupId));
    return memberRepo.findByIdGroupId(groupId).stream().map(m -> m.getId().getCardId()).toList();
  }

  @Transactional(readOnly = true)
  public List<UUID> listGroupIdsForCard(UUID cardId) {
    WorkspaceContext ctx = WorkspaceContextHolder.requirePersonal();
    cardRepo
        .findById(cardId)
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> ctx.ownsPersonal(c.getWorkspaceId(), c.getUserId()))
        .orElseThrow(() -> new IllegalArgumentException("card not found: " + cardId));
    return memberRepo.findByIdCardId(cardId).stream().map(m -> m.getId().getGroupId()).toList();
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
