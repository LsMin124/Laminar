package com.laminar.samplemanager.presentation;

import com.laminar.samplemanager.application.SampleManagerLinkService;
import com.laminar.samplemanager.domain.SampleManagerLinkEntity;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sample-manager-links")
public class SampleManagerLinkController {

  private final SampleManagerLinkService service;

  public SampleManagerLinkController(SampleManagerLinkService service) {
    this.service = service;
  }

  @PutMapping
  public ResponseEntity<SampleManagerLinkDtos.LinkResponse> linkOrUpdate(
      @Valid @RequestBody SampleManagerLinkDtos.LinkOrUpdateRequest request) {
    SampleManagerLinkEntity link =
        service.linkOrUpdate(
            request.cardId(),
            request.sampleId(),
            request.stepId(),
            request.sampleManagerUrl(),
            request.payloadSnapshot());
    return ResponseEntity.ok(toResponse(link));
  }

  @PostMapping("/{linkId}/sync")
  public ResponseEntity<SampleManagerLinkDtos.LinkResponse> markSynced(@PathVariable UUID linkId) {
    return ResponseEntity.ok(toResponse(service.markSynced(linkId)));
  }

  @GetMapping("/by-card/{cardId}")
  public ResponseEntity<List<SampleManagerLinkDtos.LinkResponse>> listByCard(
      @PathVariable UUID cardId) {
    return ResponseEntity.ok(service.listByCard(cardId).stream().map(this::toResponse).toList());
  }

  @GetMapping("/{linkId}")
  public ResponseEntity<SampleManagerLinkDtos.LinkResponse> get(@PathVariable UUID linkId) {
    return service
        .findById(linkId)
        .map(this::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{linkId}")
  public ResponseEntity<Void> delete(@PathVariable UUID linkId) {
    service.softDelete(linkId);
    return ResponseEntity.noContent().build();
  }

  private SampleManagerLinkDtos.LinkResponse toResponse(SampleManagerLinkEntity l) {
    return new SampleManagerLinkDtos.LinkResponse(
        l.getId(),
        l.getWorkspaceId(),
        l.getUserId(),
        l.getCardId(),
        l.getSampleId(),
        l.getStepId(),
        l.getSampleManagerUrl(),
        l.getSyncedAt(),
        l.getPayloadSnapshot(),
        l.getCreatedAt(),
        l.getUpdatedAt());
  }
}
