package com.laminar.datememo.presentation;

import com.laminar.datememo.application.DateMemoService;
import com.laminar.datememo.domain.DateMemoEntity;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/date-memos")
public class DateMemoController {

  private final DateMemoService service;

  public DateMemoController(DateMemoService service) {
    this.service = service;
  }

  @PutMapping
  public ResponseEntity<DateMemoDtos.DateMemoResponse> upsert(
      @Valid @RequestBody DateMemoDtos.UpsertRequest request) {
    DateMemoEntity saved =
        service.upsert(request.tabId(), request.date(), request.bodyMd(), request.attrs());
    return ResponseEntity.ok(toResponse(saved));
  }

  @GetMapping("/{tabId}/{date}")
  public ResponseEntity<DateMemoDtos.DateMemoResponse> get(
      @PathVariable UUID tabId,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return service
        .findByDate(tabId, date)
        .map(this::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/{tabId}")
  public ResponseEntity<List<DateMemoDtos.DateMemoResponse>> listByRange(
      @PathVariable UUID tabId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        service.listByTabDateRange(tabId, from, to).stream().map(this::toResponse).toList());
  }

  @DeleteMapping("/{tabId}/{date}")
  public ResponseEntity<Void> delete(
      @PathVariable UUID tabId,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    service.delete(tabId, date);
    return ResponseEntity.noContent().build();
  }

  private DateMemoDtos.DateMemoResponse toResponse(DateMemoEntity m) {
    return new DateMemoDtos.DateMemoResponse(
        m.getId().getTabId(),
        m.getId().getUserId(),
        m.getId().getDate(),
        m.getBodyMd(),
        m.getAttrs(),
        m.getCreatedAt(),
        m.getUpdatedAt());
  }
}
