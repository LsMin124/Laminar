package com.laminar.web.group;

import com.laminar.group.GroupRelationEntity;
import com.laminar.group.GroupRelationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

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
        GroupRelationEntity created = service.create(
                request.fromGroupId(),
                request.toGroupId(),
                request.relationKind(),
                request.summary(),
                request.bodyMd(),
                request.attrs());
        return ResponseEntity.ok(toResponse(created));
    }

    @GetMapping("/boards/{boardId}/group-relations")
    public ResponseEntity<List<GroupRelationDtos.GroupRelationResponse>> listByBoard(@PathVariable UUID boardId) {
        return ResponseEntity.ok(
                service.listByBoard(boardId).stream().map(this::toResponse).toList());
    }

    @DeleteMapping("/group-relations/{relationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID relationId) {
        service.softDelete(relationId);
        return ResponseEntity.noContent().build();
    }

    private GroupRelationDtos.GroupRelationResponse toResponse(GroupRelationEntity r) {
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
