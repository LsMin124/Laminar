package com.laminar.board;

import com.laminar.card.CardEntity;
import com.laminar.card.CardRelationEntity;
import com.laminar.card.CardRelationService;
import com.laminar.card.CardService;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import com.laminar.group.GroupEntity;
import com.laminar.group.GroupRelationEntity;
import com.laminar.group.GroupRelationService;
import com.laminar.group.GroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    public BoardGraphService(
            CardService cardService,
            GroupService groupService,
            CardRelationService cardRelationService,
            GroupRelationService groupRelationService) {
        this.cardService = cardService;
        this.groupService = groupService;
        this.cardRelationService = cardRelationService;
        this.groupRelationService = groupRelationService;
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
        return new BoardGraph(boardId, cards, groups, cardRels, groupRels);
    }

    public record BoardGraph(
            UUID boardId,
            List<CardEntity> cards,
            List<GroupEntity> groups,
            List<CardRelationEntity> cardRelations,
            List<GroupRelationEntity> groupRelations
    ) {
    }
}
