package com.laminar.web.perpetual;

import com.laminar.perpetual.PerpetualVersionEntity;
import com.laminar.perpetual.PerpetualVersionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PerpetualVersionController {

  private final PerpetualVersionService service;

  public PerpetualVersionController(PerpetualVersionService service) {
    this.service = service;
  }

  @PostMapping("/perpetual-versions")
  public ResponseEntity<PerpetualVersionDtos.VersionResponse> commit(
      @Valid @RequestBody PerpetualVersionDtos.CommitRequest request) {
    PerpetualVersionEntity committed =
        service.commit(
            request.perpetualNoteId(),
            request.cardId(),
            request.summary(),
            request.bodyDiffMd(),
            request.markCurrent());
    return ResponseEntity.ok(toResponse(committed));
  }

  @GetMapping("/perpetual-notes/{noteId}/versions")
  public ResponseEntity<List<PerpetualVersionDtos.VersionResponse>> listByNote(
      @PathVariable UUID noteId) {
    return ResponseEntity.ok(service.listByNote(noteId).stream().map(this::toResponse).toList());
  }

  @PostMapping("/perpetual-versions/{versionId}/mark-current-diff")
  public ResponseEntity<PerpetualVersionDtos.VersionResponse> markCurrentDiff(
      @PathVariable UUID versionId) {
    return ResponseEntity.ok(toResponse(service.markCurrentDiff(versionId)));
  }

  private PerpetualVersionDtos.VersionResponse toResponse(PerpetualVersionEntity v) {
    return new PerpetualVersionDtos.VersionResponse(
        v.getId(),
        v.getWorkspaceId(),
        v.getUserId(),
        v.getPerpetualNoteId(),
        v.getCardId(),
        v.getVersionNumber(),
        v.getSummary(),
        v.getBodyDiffMd(),
        v.isCurrentDiff(),
        v.getCommittedAt(),
        v.getCreatedAt());
  }
}
