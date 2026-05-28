package com.laminar.web.card;

import com.laminar.card.CardRelationEntity;
import com.laminar.card.CardRelationService;
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
public class CardRelationController {

    private final CardRelationService service;

    public CardRelationController(CardRelationService service) {
        this.service = service;
    }

    @PostMapping("/card-relations")
    public ResponseEntity<CardRelationDtos.CardRelationResponse> create(
            @Valid @RequestBody CardRelationDtos.CreateRequest request) {
        CardRelationEntity created = service.create(
                request.fromCardId(),
                request.toCardId(),
                request.relationKind(),
                request.summary(),
                request.bodyMd(),
                request.attrs());
        return ResponseEntity.ok(toResponse(created));
    }

    @GetMapping("/boards/{boardId}/card-relations")
    public ResponseEntity<List<CardRelationDtos.CardRelationResponse>> listByBoard(@PathVariable UUID boardId) {
        return ResponseEntity.ok(
                service.listByBoard(boardId).stream().map(this::toResponse).toList());
    }

    @DeleteMapping("/card-relations/{relationId}")
    public ResponseEntity<Void> delete(@PathVariable UUID relationId) {
        service.softDelete(relationId);
        return ResponseEntity.noContent().build();
    }

    private CardRelationDtos.CardRelationResponse toResponse(CardRelationEntity r) {
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
}
