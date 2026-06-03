package com.laminar.card.presentation;

import com.laminar.card.application.CardRelationService;
import com.laminar.card.domain.CardRelationEntity;
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
public class CardRelationController {

  private final CardRelationService service;

  public CardRelationController(CardRelationService service) {
    this.service = service;
  }

  @PostMapping("/card-relations")
  public ResponseEntity<CardRelationDtos.CardRelationResponse> create(
      @Valid @RequestBody CardRelationDtos.CreateRequest request) {
    CardRelationEntity created =
        service.create(
            request.fromCardId(),
            request.toCardId(),
            request.relationKind(),
            request.summary(),
            request.bodyMd(),
            request.attrs());
    return ResponseEntity.ok(toResponse(created));
  }

  @GetMapping("/tabs/{tabId}/card-relations")
  public ResponseEntity<List<CardRelationDtos.CardRelationResponse>> listByTab(
      @PathVariable UUID tabId) {
    return ResponseEntity.ok(service.listByTab(tabId).stream().map(this::toResponse).toList());
  }

  @DeleteMapping("/card-relations/{relationId}")
  public ResponseEntity<Void> delete(@PathVariable UUID relationId) {
    service.softDelete(relationId);
    return ResponseEntity.noContent().build();
  }

  private CardRelationDtos.CardRelationResponse toResponse(CardRelationEntity r) {
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
}
