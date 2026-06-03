package com.laminar.perpetual.presentation;

import com.laminar.perpetual.application.PerpetualColumnService;
import com.laminar.perpetual.domain.PerpetualColumnDefinitionEntity;
import com.laminar.perpetual.domain.PerpetualColumnEntity;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PerpetualColumnController {

  private final PerpetualColumnService service;

  public PerpetualColumnController(PerpetualColumnService service) {
    this.service = service;
  }

  @PostMapping("/perpetual-column-definitions")
  public ResponseEntity<PerpetualColumnDtos.DefinitionResponse> createDefinition(
      @Valid @RequestBody PerpetualColumnDtos.CreateDefinitionRequest request) {
    PerpetualColumnDefinitionEntity definition =
        service.createDefinition(
            request.boardId(), request.name(), request.type(), request.enumValues());
    return ResponseEntity.ok(toDefinitionResponse(definition));
  }

  @GetMapping("/boards/{boardId}/perpetual-column-definitions")
  public ResponseEntity<List<PerpetualColumnDtos.DefinitionResponse>> listDefinitionsByBoard(
      @PathVariable UUID boardId) {
    return ResponseEntity.ok(
        service.listDefinitionsByBoard(boardId).stream().map(this::toDefinitionResponse).toList());
  }

  @DeleteMapping("/perpetual-column-definitions/{definitionId}")
  public ResponseEntity<Void> deleteDefinition(@PathVariable UUID definitionId) {
    service.softDeleteDefinition(definitionId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/perpetual-column-values")
  public ResponseEntity<PerpetualColumnDtos.ColumnValueResponse> upsertValue(
      @Valid @RequestBody PerpetualColumnDtos.UpsertValueRequest request) {
    PerpetualColumnEntity saved =
        service.upsertValue(
            request.perpetualNoteId(), request.columnDefinitionId(), request.value());
    return ResponseEntity.ok(toValueResponse(saved));
  }

  @DeleteMapping("/perpetual-column-values")
  public ResponseEntity<Void> deleteValue(
      @RequestParam UUID perpetualNoteId, @RequestParam UUID columnDefinitionId) {
    service.deleteValue(perpetualNoteId, columnDefinitionId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/perpetual-notes/{noteId}/columns")
  public ResponseEntity<List<PerpetualColumnDtos.ColumnValueResponse>> listValuesForNote(
      @PathVariable UUID noteId) {
    return ResponseEntity.ok(
        service.listValuesForNote(noteId).stream().map(this::toValueResponse).toList());
  }

  private PerpetualColumnDtos.DefinitionResponse toDefinitionResponse(
      PerpetualColumnDefinitionEntity d) {
    return new PerpetualColumnDtos.DefinitionResponse(
        d.getId(),
        d.getWorkspaceId(),
        d.getBoardId(),
        d.getName(),
        d.getType(),
        d.getEnumValues(),
        d.getPriority(),
        d.getCreatedAt(),
        d.getUpdatedAt());
  }

  private PerpetualColumnDtos.ColumnValueResponse toValueResponse(PerpetualColumnEntity c) {
    return new PerpetualColumnDtos.ColumnValueResponse(
        c.getId().getPerpetualNoteId(), c.getId().getColumnDefinitionId(), c.getValue());
  }
}
