package com.laminar.card.presentation;

import com.laminar.card.application.CardService;
import com.laminar.card.domain.CardEntity;
import com.laminar.markdown.MarkdownService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CardController {

  private final CardService cardService;
  private final MarkdownService markdownService;

  public CardController(CardService cardService, MarkdownService markdownService) {
    this.cardService = cardService;
    this.markdownService = markdownService;
  }

  @PostMapping("/cards")
  public ResponseEntity<CardDtos.CardResponse> create(
      @Valid @RequestBody CardDtos.CreateRequest request) {
    CardEntity card =
        cardService.create(
            new CardService.CreateInput(
                request.tabId(),
                request.title(),
                request.slug(),
                request.bodyMd(),
                request.startDate(),
                request.endDate(),
                request.startTime(),
                request.allDay(),
                request.timeZone(),
                request.importance(),
                request.rrule(),
                request.origin(),
                request.attrs()));
    return ResponseEntity.ok(toResponse(card));
  }

  @PutMapping("/cards/{id}/category")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void setCategory(@PathVariable UUID id, @RequestBody CardDtos.SetCategoryRequest request) {
    cardService.setCategory(id, request.categoryId());
  }

  @GetMapping("/tabs/{tabId}/cards")
  public ResponseEntity<List<CardDtos.CardResponse>> listByTab(
      @PathVariable UUID tabId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    List<CardEntity> cards =
        (from != null && to != null)
            ? cardService.listByTabAndDateRange(tabId, from, to)
            : cardService.listByTab(tabId);
    return ResponseEntity.ok(cards.stream().map(this::toResponse).toList());
  }

  @GetMapping("/cards/{cardId}")
  public ResponseEntity<CardDtos.CardResponse> get(@PathVariable UUID cardId) {
    return cardService
        .findById(cardId)
        .map(this::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PatchMapping("/cards/{cardId}")
  public ResponseEntity<CardDtos.CardResponse> update(
      @PathVariable UUID cardId, @Valid @RequestBody CardDtos.UpdateRequest request) {
    CardEntity updated =
        cardService.update(
            cardId,
            new CardService.UpdateInput(
                request.title(),
                request.bodyMd(),
                request.startDate(),
                request.endDate(),
                request.startTime(),
                request.allDay(),
                request.timeZone(),
                request.importance(),
                request.rrule(),
                request.completed(),
                request.attrs(),
                request.canvasY()));
    return ResponseEntity.ok(toResponse(updated));
  }

  @PostMapping("/cards/{cardId}/archive")
  public ResponseEntity<Void> archive(@PathVariable UUID cardId) {
    cardService.archive(cardId);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/cards/{cardId}")
  public ResponseEntity<Void> delete(@PathVariable UUID cardId) {
    cardService.softDelete(cardId);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/tabs/{tabId}/cards/reorder")
  public ResponseEntity<List<CardDtos.CardResponse>> reorder(
      @PathVariable UUID tabId, @Valid @RequestBody CardDtos.ReorderRequest request) {
    return ResponseEntity.ok(
        cardService.reorder(tabId, request.orderedIds()).stream().map(this::toResponse).toList());
  }

  @GetMapping("/cards/{cardId}/rendered")
  public ResponseEntity<CardDtos.RenderedBodyResponse> rendered(@PathVariable UUID cardId) {
    return cardService
        .findById(cardId)
        .map(c -> new CardDtos.RenderedBodyResponse(cardId, markdownService.render(c.getBodyMd())))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private CardDtos.CardResponse toResponse(CardEntity c) {
    return new CardDtos.CardResponse(
        c.getId(),
        c.getSubjectId(),
        c.getUserId(),
        c.getTabId(),
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
        c.getRrule(),
        c.getOrigin(),
        c.getPriority(),
        c.getAttrs(),
        c.getArchivedAt(),
        c.getCreatedAt(),
        c.getUpdatedAt(),
        c.getCanvasY());
  }
}
