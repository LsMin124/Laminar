package com.laminar.tab.application;

import com.laminar.card.application.CardRelationService;
import com.laminar.card.application.CardService;
import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardRelationEntity;
import com.laminar.context.SubjectContext;
import com.laminar.context.SubjectContextHolder;
import com.laminar.group.application.GroupRelationService;
import com.laminar.group.application.GroupService;
import com.laminar.group.domain.GroupEntity;
import com.laminar.group.domain.GroupMemberEntity;
import com.laminar.group.domain.GroupRelationEntity;
import com.laminar.group.repository.GroupMemberRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보드의 그래프 뷰 — 노드 (cards + groups) + 엣지 (cardRelations + groupRelations) 통합.
 *
 * <p>프론트엔드 그래프 렌더링 시 1회 호출로 전체 상태 fetch. 격리는 각 service의 Personal-First 필터에 위임.
 */
@Service
public class TabGraphService {

  private final CardService cardService;
  private final GroupService groupService;
  private final CardRelationService cardRelationService;
  private final GroupRelationService groupRelationService;
  private final GroupMemberRepository groupMemberRepository;

  public TabGraphService(
      CardService cardService,
      GroupService groupService,
      CardRelationService cardRelationService,
      GroupRelationService groupRelationService,
      GroupMemberRepository groupMemberRepository) {
    this.cardService = cardService;
    this.groupService = groupService;
    this.cardRelationService = cardRelationService;
    this.groupRelationService = groupRelationService;
    this.groupMemberRepository = groupMemberRepository;
  }

  @Transactional(readOnly = true)
  public TabGraph getGraph(UUID tabId) {
    SubjectContext ctx = SubjectContextHolder.require();
    if (ctx.scope() != SubjectContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required for tab graph");
    }
    List<CardEntity> cards = cardService.listByTab(tabId);
    List<GroupEntity> groups = groupService.listByTab(tabId);
    List<CardRelationEntity> cardRels = cardRelationService.listByTab(tabId);
    List<GroupRelationEntity> groupRels = groupRelationService.listByTab(tabId);

    // 자동그룹용 — 보드 그룹들의 멤버십(groupId → cardIds). groupId는 위에서 이미
    // Personal-First 필터된 사용자 그룹이라 멤버 조회도 사용자 자원에 한정(GroupMemberEntity는
    // subject 필터 미부착 — 부모 격리 의존).
    List<UUID> groupIds = groups.stream().map(GroupEntity::getId).toList();
    Map<UUID, List<UUID>> groupMembers = new HashMap<>();
    if (!groupIds.isEmpty()) {
      for (GroupMemberEntity m : groupMemberRepository.findByIdGroupIdIn(groupIds)) {
        groupMembers
            .computeIfAbsent(m.getId().getGroupId(), k -> new ArrayList<>())
            .add(m.getId().getCardId());
      }
    }
    return new TabGraph(tabId, cards, groups, cardRels, groupRels, groupMembers);
  }

  public record TabGraph(
      UUID tabId,
      List<CardEntity> cards,
      List<GroupEntity> groups,
      List<CardRelationEntity> cardRelations,
      List<GroupRelationEntity> groupRelations,
      Map<UUID, List<UUID>> groupMembers) {}
}
