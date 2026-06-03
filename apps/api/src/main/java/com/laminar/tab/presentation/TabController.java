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

/** /api/boards — 보드 CRUD. PERSONAL scope 진입 후 호출 (X-Laminar-Subject-Id 헤더 필수). */
@RestController
@RequestMapping("/api/boards")
public class TabController {

  private final TabService tabService;

  public TabController(TabService tabService) {
    this.tabService = tabService;
  }

  @PostMapping
  public ResponseEntity<TabDtos.TabResponse> create(
      @Valid @RequestBody TabDtos.CreateRequest request) {
    TabEntity tab =
        tabService.create(
            request.name(),
            request.slug(),
            request.defaultView(),
            request.iconName(),
            request.iconColor(),
            request.settings());
    return ResponseEntity.ok(toResponse(tab));
  }

  @GetMapping
  public ResponseEntity<List<TabDtos.TabResponse>> list() {
    List<TabDtos.TabResponse> response =
        tabService.listActive().stream().map(this::toResponse).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{tabId}")
  public ResponseEntity<TabDtos.TabResponse> get(@PathVariable UUID tabId) {
    return tabService
        .findById(tabId)
        .map(this::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PatchMapping("/{tabId}")
  public ResponseEntity<TabDtos.TabResponse> update(
      @PathVariable UUID tabId, @Valid @RequestBody TabDtos.UpdateRequest request) {
    TabEntity updated =
        tabService.update(
            tabId,
            request.name(),
            request.defaultView(),
            request.iconName(),
            request.iconColor(),
            request.settings());
    return ResponseEntity.ok(toResponse(updated));
  }

  @DeleteMapping("/{tabId}")
  public ResponseEntity<Void> delete(@PathVariable UUID tabId) {
    tabService.softDelete(tabId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/reorder")
  public ResponseEntity<List<TabDtos.TabResponse>> reorder(
      @Valid @RequestBody TabDtos.ReorderRequest request) {
    return ResponseEntity.ok(
        tabService.reorder(request.orderedIds()).stream().map(this::toResponse).toList());
  }

  private TabDtos.TabResponse toResponse(TabEntity tab) {
    return new TabDtos.TabResponse(
        tab.getId(),
        tab.getSubjectId(),
        tab.getUserId(),
        tab.getName(),
        tab.getSlug(),
        tab.getDefaultView(),
        tab.getIconName(),
        tab.getIconColor(),
        tab.getSettings(),
        tab.getPriority(),
        tab.getCreatedAt(),
        tab.getUpdatedAt());
  }
}
