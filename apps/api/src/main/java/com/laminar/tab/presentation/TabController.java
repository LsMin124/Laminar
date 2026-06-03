package com.laminar.tab.presentation;

import com.laminar.tab.application.TabService;
import com.laminar.tab.domain.TabEntity;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TabController {

  private final TabService tabService;

  public TabController(TabService tabService) {
    this.tabService = tabService;
  }

  @PostMapping("/tabs")
  public ResponseEntity<TabDtos.TabResponse> create(
      @Valid @RequestBody TabDtos.CreateRequest request) {
    TabEntity tab =
        tabService.create(
            request.boardId(),
            request.parentTabId(),
            request.name(),
            request.visible(),
            request.collapsed(),
            request.showLabel(),
            request.labelColor(),
            request.attrs());
    return ResponseEntity.ok(toResponse(tab));
  }

  @GetMapping("/boards/{boardId}/tabs")
  public ResponseEntity<List<TabDtos.TabResponse>> listByBoard(@PathVariable UUID boardId) {
    return ResponseEntity.ok(
        tabService.listByBoard(boardId).stream().map(this::toResponse).toList());
  }

  @GetMapping("/boards/{boardId}/tabs/roots")
  public ResponseEntity<List<TabDtos.TabResponse>> listRoots(@PathVariable UUID boardId) {
    return ResponseEntity.ok(
        tabService.listRootsByBoard(boardId).stream().map(this::toResponse).toList());
  }

  @GetMapping("/tabs/{tabId}/children")
  public ResponseEntity<List<TabDtos.TabResponse>> listChildren(@PathVariable UUID tabId) {
    return ResponseEntity.ok(
        tabService.listChildren(tabId).stream().map(this::toResponse).toList());
  }

  @GetMapping("/tabs/{tabId}")
  public ResponseEntity<TabDtos.TabResponse> get(@PathVariable UUID tabId) {
    return tabService
        .findById(tabId)
        .map(this::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PatchMapping("/tabs/{tabId}")
  public ResponseEntity<TabDtos.TabResponse> update(
      @PathVariable UUID tabId, @Valid @RequestBody TabDtos.UpdateRequest request) {
    TabEntity updated =
        tabService.update(
            tabId,
            request.name(),
            request.parentTabId(),
            request.visible(),
            request.collapsed(),
            request.showLabel(),
            request.labelColor(),
            request.attrs());
    return ResponseEntity.ok(toResponse(updated));
  }

  @PatchMapping("/tabs/reorder")
  public ResponseEntity<List<TabDtos.TabResponse>> reorder(
      @Valid @RequestBody TabDtos.ReorderRequest request) {
    return ResponseEntity.ok(
        tabService.reorder(request.boardId(), request.orderedIds()).stream()
            .map(this::toResponse)
            .toList());
  }

  @DeleteMapping("/tabs/{tabId}")
  public ResponseEntity<Void> delete(@PathVariable UUID tabId) {
    tabService.softDelete(tabId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/tabs/{tabId}/cards/{cardId}")
  public ResponseEntity<Void> addMember(@PathVariable UUID tabId, @PathVariable UUID cardId) {
    tabService.addMember(tabId, cardId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/tabs/{tabId}/cards/{cardId}")
  public ResponseEntity<Void> removeMember(@PathVariable UUID tabId, @PathVariable UUID cardId) {
    tabService.removeMember(tabId, cardId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/tabs/{tabId}/groups/{groupId}")
  public ResponseEntity<Void> addGroup(@PathVariable UUID tabId, @PathVariable UUID groupId) {
    tabService.addGroup(tabId, groupId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/tabs/{tabId}/groups/{groupId}")
  public ResponseEntity<Void> removeGroup(@PathVariable UUID tabId, @PathVariable UUID groupId) {
    tabService.removeGroup(tabId, groupId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/tabs/{tabId}/groups")
  public ResponseEntity<List<UUID>> listGroupsInTab(@PathVariable UUID tabId) {
    return ResponseEntity.ok(tabService.listGroupIdsInTab(tabId));
  }

  @GetMapping("/tabs/{tabId}/cards")
  public ResponseEntity<List<UUID>> listCardsInTab(@PathVariable UUID tabId) {
    return ResponseEntity.ok(tabService.listCardIdsInTab(tabId));
  }

  @GetMapping("/cards/{cardId}/tabs")
  public ResponseEntity<List<UUID>> listTabsForCard(@PathVariable UUID cardId) {
    return ResponseEntity.ok(tabService.listTabIdsForCard(cardId));
  }

  private TabDtos.TabResponse toResponse(TabEntity t) {
    return new TabDtos.TabResponse(
        t.getId(),
        t.getWorkspaceId(),
        t.getUserId(),
        t.getBoardId(),
        t.getParentTabId(),
        t.getName(),
        t.getPriority(),
        t.isVisible(),
        t.isCollapsed(),
        t.isShowLabel(),
        t.getLabelColor(),
        t.getAttrs(),
        t.getCreatedAt(),
        t.getUpdatedAt());
  }
}
