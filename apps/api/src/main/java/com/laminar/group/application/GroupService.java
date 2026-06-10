package com.laminar.group.application;

import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
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
  // 카드 검증은 CardService 경유 — CardRepository 원정 접근 금지 (DX-20, ArchUnit 강제).
  private final com.laminar.card.application.CardService cardService;

  public GroupService(
      GroupRepository groupRepo,
      GroupMemberRepository memberRepo,
      com.laminar.card.application.CardService cardService) {
    this.groupRepo = groupRepo;
    this.memberRepo = memberRepo;
    this.cardService = cardService;
  }

  @Transactional
  public GroupEntity create(UUID tabId, String name, String color, Map<String, Object> attrs) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("groups");

    int nextPriority =
        groupRepo
            .findFirstByTabIdAndDeletedAtIsNullOrderByPriorityDesc(tabId)
            .map(g -> g.getPriority() + PRIORITY_STEP)
            .orElse(PRIORITY_STEP);

    GroupEntity group = new GroupEntity();
    group.setSubjectId(ctx.subjectId());
    group.setUserId(ctx.userId());
    group.setCreatedBy(ctx.userId());
    group.setTabId(tabId);
    group.setName(name);
    group.setColor(color);
    group.setPriority(nextPriority);
    group.setAttrs(attrs == null ? new HashMap<>() : attrs);
    return groupRepo.save(group);
  }

  @Transactional(readOnly = true)
  public List<GroupEntity> listByTab(UUID tabId) {
    SubjectContextHolder.requirePersonal();
    return groupRepo.findByTabIdAndDeletedAtIsNullOrderByPriorityAsc(tabId);
  }

  @Transactional(readOnly = true)
  public Optional<GroupEntity> findById(UUID groupId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonal();
    return groupRepo.findOwnedActive(groupId, ctx);
  }

  @Transactional
  public GroupEntity update(
      UUID groupId, String name, String color, String bodyMd, Map<String, Object> attrs) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("groups");
    GroupEntity group = groupRepo.findOwnedActiveOrThrow(groupId, ctx, "group");
    if (name != null && !name.isBlank()) group.setName(name);
    if (color != null) group.setColor(color);
    if (bodyMd != null) group.setBodyMd(bodyMd);
    if (attrs != null) group.setAttrs(attrs);
    return groupRepo.save(group);
  }

  @Transactional
  public List<GroupEntity> reorder(UUID tabId, List<UUID> orderedGroupIds) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("groups");
    if (orderedGroupIds == null || orderedGroupIds.isEmpty()) {
      return List.of();
    }
    List<GroupEntity> result = new ArrayList<>(orderedGroupIds.size());
    for (int i = 0; i < orderedGroupIds.size(); i++) {
      UUID groupId = orderedGroupIds.get(i);
      int newPriority = (i + 1) * PRIORITY_STEP;
      groupRepo
          .findOwnedActive(groupId, ctx)
          .filter(g -> tabId == null || tabId.equals(g.getTabId()))
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
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("groups");
    groupRepo
        .findOwnedActive(groupId, ctx)
        .ifPresent(
            group -> {
              group.setDeletedAt(OffsetDateTime.now());
              groupRepo.save(group);
            });
  }

  /** 그룹 ↔ 카드 멤버십 추가. group/card는 현재 user의 자원이어야 (Personal-First 격리 자동). */
  @Transactional
  public GroupMemberEntity addMember(UUID groupId, UUID cardId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("groups");
    groupRepo.findOwnedActiveOrThrow(groupId, ctx, "group");
    cardService.requireOwnedActive(cardId);

    GroupMemberEntity member = new GroupMemberEntity();
    member.setId(new GroupMemberId(groupId, cardId));
    member.setAddedBy(ctx.userId());
    return memberRepo.save(member);
  }

  @Transactional
  public void removeMember(UUID groupId, UUID cardId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonalWritable("groups");
    // group/card 격리 검증 후 삭제 — 다른 user 그룹은 소유권 불일치로 빈 Optional
    groupRepo.findOwnedActiveOrThrow(groupId, ctx, "group");
    memberRepo.findById(new GroupMemberId(groupId, cardId)).ifPresent(memberRepo::delete);
  }

  @Transactional(readOnly = true)
  public List<UUID> listCardIdsInGroup(UUID groupId) {
    SubjectContext ctx = SubjectContextHolder.requirePersonal();
    groupRepo.findOwnedActiveOrThrow(groupId, ctx, "group");
    return memberRepo.findByIdGroupId(groupId).stream().map(m -> m.getId().getCardId()).toList();
  }

  @Transactional(readOnly = true)
  public List<UUID> listGroupIdsForCard(UUID cardId) {
    SubjectContextHolder.requirePersonal();
    cardService.requireOwnedActive(cardId);
    return memberRepo.findByIdCardId(cardId).stream().map(m -> m.getId().getGroupId()).toList();
  }
}
