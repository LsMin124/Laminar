package com.laminar.board;

import com.laminar.card.CardEntity;
import com.laminar.card.CardRelationEntity;
import com.laminar.card.CardRelationService;
import com.laminar.card.CardService;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.group.GroupEntity;
import com.laminar.group.GroupMemberEntity;
import com.laminar.group.GroupMemberRepository;
import com.laminar.group.GroupRelationEntity;
import com.laminar.group.GroupRelationService;
import com.laminar.group.GroupService;
import com.laminar.tab.TabEntity;
import com.laminar.tab.TabGroupMemberEntity;
import com.laminar.tab.TabGroupMemberRepository;
import com.laminar.tab.TabRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 보드의 그래프 뷰 — 노드 (cards + groups) + 엣지 (cardRelations + groupRelations) 통합.
 *
 * 프론트엔드 그래프 렌더링 시 1회 호출로 전체 상태 fetch. 격리는 각 service의 Personal-First 필터에 위임.
 */
@Service
public class BoardGraphService {

    private final CardService cardService;
    private final GroupService groupService;
    private final CardRelationService cardRelationService;
    private final GroupRelationService groupRelationService;
    private final GroupMemberRepository groupMemberRepository;
    private final TabRepository tabRepository;
    private final TabGroupMemberRepository tabGroupMemberRepository;

    public BoardGraphService(
            CardService cardService,
            GroupService groupService,
            CardRelationService cardRelationService,
            GroupRelationService groupRelationService,
            GroupMemberRepository groupMemberRepository,
            TabRepository tabRepository,
            TabGroupMemberRepository tabGroupMemberRepository) {
        this.cardService = cardService;
        this.groupService = groupService;
        this.cardRelationService = cardRelationService;
        this.groupRelationService = groupRelationService;
        this.groupMemberRepository = groupMemberRepository;
        this.tabRepository = tabRepository;
        this.tabGroupMemberRepository = tabGroupMemberRepository;
    }

    @Transactional(readOnly = true)
    public BoardGraph getGraph(UUID boardId) {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
            throw new IllegalStateException("PERSONAL scope required for board graph");
        }
        List<CardEntity> cards = cardService.listByBoard(boardId);
        List<GroupEntity> groups = groupService.listByBoard(boardId);
        List<CardRelationEntity> cardRels = cardRelationService.listByBoard(boardId);
        List<GroupRelationEntity> groupRels = groupRelationService.listByBoard(boardId);

        // P3b 자동그룹용 — 보드 그룹들의 멤버십(groupId → cardIds). groupId는 위에서 이미
        // Personal-First 필터된 사용자 그룹이라 멤버 조회도 사용자 자원에 한정(GroupMemberEntity는
        // workspace 필터 미부착 — 부모 격리 의존).
        List<UUID> groupIds = groups.stream().map(GroupEntity::getId).toList();
        Map<UUID, List<UUID>> groupMembers = new HashMap<>();
        if (!groupIds.isEmpty()) {
            for (GroupMemberEntity m : groupMemberRepository.findByIdGroupIdIn(groupIds)) {
                groupMembers
                        .computeIfAbsent(m.getId().getGroupId(), k -> new ArrayList<>())
                        .add(m.getId().getCardId());
            }
        }
        // P4b 탭 스코프용 — 보드 탭들의 그룹 멤버십(tabId → groupIds). 탭은 Personal-First
        // 필터된 사용자 자원이라 멤버 그룹도 사용자 한정(TabGroupMemberEntity는 부모 격리 의존).
        List<UUID> tabIds = tabRepository
                .findByBoardIdAndDeletedAtIsNullOrderByPriorityAsc(boardId).stream()
                .map(TabEntity::getId)
                .toList();
        Map<UUID, List<UUID>> tabGroups = new HashMap<>();
        if (!tabIds.isEmpty()) {
            for (TabGroupMemberEntity m : tabGroupMemberRepository.findByIdTabIdIn(tabIds)) {
                tabGroups
                        .computeIfAbsent(m.getId().getTabId(), k -> new ArrayList<>())
                        .add(m.getId().getGroupId());
            }
        }
        return new BoardGraph(
                boardId, cards, groups, cardRels, groupRels, groupMembers, tabGroups);
    }

    public record BoardGraph(
            UUID boardId,
            List<CardEntity> cards,
            List<GroupEntity> groups,
            List<CardRelationEntity> cardRelations,
            List<GroupRelationEntity> groupRelations,
            Map<UUID, List<UUID>> groupMembers,
            Map<UUID, List<UUID>> tabGroups
    ) {
    }
}
