package com.laminar.web.tab;

import com.laminar.tab.TabRelationEntity;
import com.laminar.tab.TabRelationService;
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
public class TabRelationController {

    private final TabRelationService service;

    public TabRelationController(TabRelationService service) {
        this.service = service;
    }

    @PostMapping("/tab-relations")
    public ResponseEntity<TabRelationDtos.TabRelationResponse> create(
            @Valid @RequestBody TabRelationDtos.CreateRequest request) {
        TabRelationEntity created = service.create(
                request.fromTabId(),
                request.toTabId(),
                request.summary(),
                request.bodyMd(),
                request.attrs());
        return ResponseEntity.ok(toResponse(created));
    }

    @GetMapping("/boards/{boardId}/tab-relations")
    public ResponseEntity<List<TabRelationDtos.TabRelationResponse>> listByBoard(@PathVariable UUID boardId) {
        return ResponseEntity.ok(
                service.listByBoard(boardId).stream().map(this::toResponse).toList());
    }

    @DeleteMapping("/tab-relations/{relationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID relationId) {
        service.softDelete(relationId);
        return ResponseEntity.noContent().build();
    }

    private TabRelationDtos.TabRelationResponse toResponse(TabRelationEntity r) {
        return new TabRelationDtos.TabRelationResponse(
                r.getId(), r.getWorkspaceId(), r.getUserId(), r.getBoardId(),
                r.getFromTabId(), r.getToTabId(),
                r.getSummary(), r.getBodyMd(), r.getAttrs(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
