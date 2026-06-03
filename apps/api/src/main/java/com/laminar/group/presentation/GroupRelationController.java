package com.laminar.group.presentation;

import com.laminar.group.application.GroupRelationService;
import com.laminar.group.domain.GroupRelationEntity;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GroupRelationController {

  private final GroupRelationService service;

  public GroupRelationController(GroupRelationService service) {
    this.service = service;
  }

  @PostMapping("/group-relations")
  public ResponseEntity<GroupRelationDtos.GroupRelationResponse> create(
      @Valid @RequestBody GroupRelationDtos.CreateRequest request) {
    GroupRelationEntity created =
        service.create(
            request.fromGroupId(),
            request.toGroupId(),
            request.relationKind(),
            request.summary(),
            request.bodyMd(),
            request.attrs());
    return ResponseEntity.ok(toResponse(created));
  }

  @GetMapping("/tabs/{tabId}/group-relations")
  public ResponseEntity<List<GroupRelationDtos.GroupRelationResponse>> listByTab(
      @PathVariable UUID tabId) {
    return ResponseEntity.ok(service.listByTab(tabId).stream().map(this::toResponse).toList());
  }

  @DeleteMapping("/group-relations/{relationId}")
  public ResponseEntity<Void> delete(@PathVariable UUID relationId) {
    service.softDelete(relationId);
    return ResponseEntity.noContent().build();
  }

  private GroupRelationDtos.GroupRelationResponse toResponse(GroupRelationEntity r) {
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
