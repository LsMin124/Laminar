package com.laminar.web.group;

import com.laminar.group.GroupEntity;
import com.laminar.group.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping("/groups")
    public ResponseEntity<GroupDtos.GroupResponse> create(@Valid @RequestBody GroupDtos.CreateRequest request) {
        GroupEntity group = groupService.create(
                request.boardId(), request.name(), request.color(), request.attrs());
        return ResponseEntity.ok(toResponse(group));
    }

    @GetMapping("/boards/{boardId}/groups")
    public ResponseEntity<List<GroupDtos.GroupResponse>> listByBoard(@PathVariable UUID boardId) {
        return ResponseEntity.ok(
                groupService.listByBoard(boardId).stream().map(this::toResponse).toList());
    }

    @GetMapping("/groups/{groupId}")
    public ResponseEntity<GroupDtos.GroupResponse> get(@PathVariable UUID groupId) {
        return groupService.findById(groupId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/groups/{groupId}")
    public ResponseEntity<GroupDtos.GroupResponse> update(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupDtos.UpdateRequest request) {
        GroupEntity updated = groupService.update(
                groupId, request.name(), request.color(), request.attrs());
        return ResponseEntity.ok(toResponse(updated));
    }

    @PatchMapping("/groups/reorder")
    public ResponseEntity<List<GroupDtos.GroupResponse>> reorder(
            @Valid @RequestBody GroupDtos.ReorderRequest request) {
        return ResponseEntity.ok(
                groupService.reorder(request.boardId(), request.orderedIds()).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<Void> delete(@PathVariable UUID groupId) {
        groupService.softDelete(groupId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/groups/{groupId}/cards/{cardId}")
    public ResponseEntity<Void> addMember(@PathVariable UUID groupId, @PathVariable UUID cardId) {
        groupService.addMember(groupId, cardId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/groups/{groupId}/cards/{cardId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID groupId, @PathVariable UUID cardId) {
        groupService.removeMember(groupId, cardId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/groups/{groupId}/cards")
    public ResponseEntity<List<UUID>> listCardsInGroup(@PathVariable UUID groupId) {
        return ResponseEntity.ok(groupService.listCardIdsInGroup(groupId));
    }

    @GetMapping("/cards/{cardId}/groups")
    public ResponseEntity<List<UUID>> listGroupsForCard(@PathVariable UUID cardId) {
        return ResponseEntity.ok(groupService.listGroupIdsForCard(cardId));
    }

    private GroupDtos.GroupResponse toResponse(GroupEntity g) {
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
}
