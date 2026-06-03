package com.laminar.board.presentation;

import com.laminar.board.application.BoardGraphService;
import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardRelationEntity;
import com.laminar.card.presentation.CardDtos;
import com.laminar.card.presentation.CardRelationDtos;
import com.laminar.group.domain.GroupEntity;
import com.laminar.group.domain.GroupRelationEntity;
import com.laminar.group.presentation.GroupDtos;
import com.laminar.group.presentation.GroupRelationDtos;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/boards/{id}/graph — 보드 전체 그래프 (노드 + 엣지) 1회 fetch. */
@RestController
@RequestMapping("/api/boards")
public class BoardGraphController {

  private final BoardGraphService graphService;

  public BoardGraphController(BoardGraphService graphService) {
    this.graphService = graphService;
  }

  @GetMapping("/{boardId}/graph")
  public ResponseEntity<BoardGraphResponse> graph(@PathVariable UUID boardId) {
    BoardGraphService.BoardGraph graph = graphService.getGraph(boardId);
    return ResponseEntity.ok(
        new BoardGraphResponse(
            graph.boardId(),
            graph.cards().stream().map(BoardGraphController::toCard).toList(),
            graph.groups().stream().map(BoardGraphController::toGroup).toList(),
            graph.cardRelations().stream().map(BoardGraphController::toCardRelation).toList(),
            graph.groupRelations().stream().map(BoardGraphController::toGroupRelation).toList(),
            graph.groupMembers(),
            graph.tabGroups()));
  }

  public record BoardGraphResponse(
      UUID boardId,
      List<CardDtos.CardResponse> cards,
      List<GroupDtos.GroupResponse> groups,
      List<CardRelationDtos.CardRelationResponse> cardRelations,
      List<GroupRelationDtos.GroupRelationResponse> groupRelations,
      Map<UUID, List<UUID>> groupMembers,
      Map<UUID, List<UUID>> tabGroups) {}

  private static CardDtos.CardResponse toCard(CardEntity c) {
    return new CardDtos.CardResponse(
        c.getId(),
        c.getWorkspaceId(),
        c.getUserId(),
        c.getBoardId(),
        c.getTitle(),
        c.getSlug(),
        c.getBodyMd(),
        c.getStartDate(),
        c.getEndDate(),
        c.getStartTime(),
        c.isAllDay(),
        c.getTimeZone(),
        c.getImportance(),
        c.isCompleted(),
        c.getLinkedPerpetualId(),
        c.getRrule(),
        c.getOrigin(),
        c.getPriority(),
        c.getAttrs(),
        c.getArchivedAt(),
        c.getCreatedAt(),
        c.getUpdatedAt());
  }

  private static GroupDtos.GroupResponse toGroup(GroupEntity g) {
    return new GroupDtos.GroupResponse(
        g.getId(),
        g.getWorkspaceId(),
        g.getUserId(),
        g.getBoardId(),
        g.getName(),
        g.getColor(),
        g.getPriority(),
        g.getAttrs(),
        g.getCreatedAt(),
        g.getUpdatedAt());
  }

  private static CardRelationDtos.CardRelationResponse toCardRelation(CardRelationEntity r) {
    return new CardRelationDtos.CardRelationResponse(
        r.getId(),
        r.getWorkspaceId(),
        r.getUserId(),
        r.getBoardId(),
        r.getFromCardId(),
        r.getToCardId(),
        r.getRelationKind(),
        r.getSummary(),
        r.getBodyMd(),
        r.getAttrs(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }

  private static GroupRelationDtos.GroupRelationResponse toGroupRelation(GroupRelationEntity r) {
    return new GroupRelationDtos.GroupRelationResponse(
        r.getId(),
        r.getWorkspaceId(),
        r.getUserId(),
        r.getBoardId(),
        r.getFromGroupId(),
        r.getToGroupId(),
        r.getRelationKind(),
        r.getSummary(),
        r.getBodyMd(),
        r.getAttrs(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }
}
