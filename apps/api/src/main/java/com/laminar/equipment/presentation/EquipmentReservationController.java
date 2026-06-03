package com.laminar.equipment.presentation;

import com.laminar.equipment.application.EquipmentReservationService;
import com.laminar.equipment.domain.EquipmentReservationEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EquipmentReservationController {

  private final EquipmentReservationService service;

  public EquipmentReservationController(EquipmentReservationService service) {
    this.service = service;
  }

  @PostMapping("/equipment/{equipmentId}/reservations")
  public ResponseEntity<ReservationResponse> reserve(
      @PathVariable UUID equipmentId, @Valid @RequestBody ReserveRequest request) {
    EquipmentReservationEntity reservation =
        service.reserve(
            equipmentId,
            request.startAt(),
            request.endAt(),
            request.purpose(),
            request.rrule(),
            request.cardId());
    return ResponseEntity.ok(toResponse(reservation));
  }

  @GetMapping("/equipment/{equipmentId}/reservations")
  public ResponseEntity<List<ReservationResponse>> listForEquipment(
      @PathVariable UUID equipmentId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
    return ResponseEntity.ok(
        service.listByEquipmentRange(equipmentId, from, to).stream()
            .map(EquipmentReservationController::toResponse)
            .toList());
  }

  @GetMapping("/me/reservations")
  public ResponseEntity<List<ReservationResponse>> listMine() {
    return ResponseEntity.ok(
        service.listMyReservations().stream()
            .map(EquipmentReservationController::toResponse)
            .toList());
  }

  @DeleteMapping("/reservations/{reservationId}")
  public ResponseEntity<Void> cancel(@PathVariable UUID reservationId) {
    service.cancel(reservationId);
    return ResponseEntity.noContent().build();
  }

  public record ReserveRequest(
      @NotNull OffsetDateTime startAt,
      @NotNull OffsetDateTime endAt,
      @Size(max = 500) String purpose,
      @Size(max = 500) String rrule,
      UUID cardId) {}

  public record ReservationResponse(
      UUID id,
      UUID subjectId,
      UUID equipmentId,
      UUID reservedBy,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      String purpose,
      String rrule,
      UUID cardId,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  private static ReservationResponse toResponse(EquipmentReservationEntity r) {
    return new ReservationResponse(
        r.getId(),
        r.getSubjectId(),
        r.getEquipmentId(),
        r.getReservedBy(),
        r.getStartAt(),
        r.getEndAt(),
        r.getPurpose(),
        r.getRrule(),
        r.getCardId(),
        r.getCreatedAt(),
        r.getUpdatedAt());
  }
}
