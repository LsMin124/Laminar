package com.laminar.tab.presentation;

import com.laminar.card.domain.CardEntity;
import com.laminar.card.domain.CardRelationEntity;
import com.laminar.card.presentation.CardDtos;
import com.laminar.card.presentation.CardRelationDtos;
import com.laminar.category.application.CardCategoryService;
import com.laminar.category.domain.CardCategoryEntity;
import com.laminar.category.presentation.CardCategoryDtos;
import com.laminar.group.domain.GroupEntity;
import com.laminar.group.domain.GroupRelationEntity;
import com.laminar.group.presentation.GroupDtos;
import com.laminar.group.presentation.GroupRelationDtos;
import com.laminar.tab.application.TabGraphService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/tabs/{id}/graph — 보드 전체 그래프 (노드 + 엣지) 1회 fetch. */
@RestController
@RequestMapping("/api/tabs")
public class TabGraphController {

  private final TabGraphService graphService;
  private final CardCategoryService categoryService;

  public TabGraphController(TabGraphService graphService, CardCategoryService categoryService) {
    this.graphService = graphService;
    this.categoryService = categoryService;
  }

  @GetMapping("/{tabId}/graph")
  public ResponseEntity<TabGraphResponse> graph(@PathVariable UUID tabId) {
    TabGraphService.TabGraph graph = graphService.getGraph(tabId);
    List<CardCategoryDtos.CategoryResponse> categories =
        categoryService.list().stream().map(TabGraphController::toCategory).toList();
    Map<UUID, UUID> cardCategoryIds =
        graph.cards().stream()
            .filter(c -> c.getCategoryId() != null)
            .collect(Collectors.toMap(CardEntity::getId, CardEntity::getCategoryId));
    return ResponseEntity.ok(
        new TabGraphResponse(
            graph.tabId(),
            graph.cards().stream().map(TabGraphController::toCard).toList(),
            graph.groups().stream().map(TabGraphController::toGroup).toList(),
            graph.cardRelations().stream().map(TabGraphController::toCardRelation).toList(),
            graph.groupRelations().stream().map(TabGraphController::toGroupRelation).toList(),
            graph.groupMembers(),
            categories,
            cardCategoryIds));
  }

  public record TabGraphResponse(
      UUID tabId,
      List<CardDtos.CardResponse> cards,
      List<GroupDtos.GroupResponse> groups,
      List<CardRelationDtos.CardRelationResponse> cardRelations,
      List<GroupRelationDtos.GroupRelationResponse> groupRelations,
      Map<UUID, List<UUID>> groupMembers,
      List<CardCategoryDtos.CategoryResponse> categories,
      Map<UUID, UUID> cardCategoryIds) {}

  private static CardDtos.CardResponse toCard(CardEntity c) {
    return new CardDtos.CardResponse(
        c.getId(),
        c.getSubjectId(),
        c.getUserId(),
        c.getTabId(),
        c.getTitle(),
        c.getSlug(),
        null, // 그래프엔 전체 bodyMd를 싣지 않음(페이로드 경감) — 전체 본문은 GET /api/cards/{id}
        CardDtos.bodyExcerpt(c.getBodyMd()),
        c.getStartDate(),
        c.getEndDate(),
        c.getStartTime(),
        c.isAllDay(),
        c.getTimeZone(),
        c.getImportance(),
        c.isCompleted(),
        c.getRrule(),
        c.getOrigin(),
        c.getPriority(),
        c.getAttrs(),
        c.getArchivedAt(),
        c.getCreatedAt(),
        c.getUpdatedAt(),
        c.getCanvasY());
  }

  private static CardCategoryDtos.CategoryResponse toCategory(CardCategoryEntity c) {
    return new CardCategoryDtos.CategoryResponse(c.getId(), c.getName(), c.getColor());
  }

  private static GroupDtos.GroupResponse toGroup(GroupEntity g) {
    return new GroupDtos.GroupResponse(
        g.getId(),
        g.getSubjectId(),
        g.getUserId(),
        g.getTabId(),
        g.getName(),
        g.getColor(),
        g.getBodyMd(),
        g.getPriority(),
        g.getAttrs(),
        g.getCreatedAt(),
        g.getUpdatedAt());
  }

  private static CardRelationDtos.CardRelationResponse toCardRelation(CardRelationEntity r) {
    return new CardRelationDtos.CardRelationResponse(
        r.getId(),
        r.getSubjectId(),
        r.getUserId(),
        r.getTabId(),
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
        r.getSubjectId(),
        r.getUserId(),
        r.getTabId(),
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
