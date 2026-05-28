package com.laminar.web.datememo;

import com.laminar.datememo.DateMemoEntity;
import com.laminar.datememo.DateMemoService;
import jakarta.validation.Valid;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
        DateMemoEntity saved = service.upsert(
                request.boardId(), request.date(), request.bodyMd(), request.attrs());
        return ResponseEntity.ok(toResponse(saved));
    }

    @GetMapping("/{boardId}/{date}")
    public ResponseEntity<DateMemoDtos.DateMemoResponse> get(
            @PathVariable UUID boardId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.findByDate(boardId, date)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{boardId}")
    public ResponseEntity<List<DateMemoDtos.DateMemoResponse>> listByRange(
            @PathVariable UUID boardId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(
                service.listByBoardDateRange(boardId, from, to).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @DeleteMapping("/{boardId}/{date}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID boardId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.delete(boardId, date);
        return ResponseEntity.noContent().build();
    }

    private DateMemoDtos.DateMemoResponse toResponse(DateMemoEntity m) {
        return new DateMemoDtos.DateMemoResponse(
                m.getId().getBoardId(),
                m.getId().getUserId(),
                m.getId().getDate(),
                m.getBodyMd(),
                m.getAttrs(),
                m.getCreatedAt(),
                m.getUpdatedAt());
    }
}
