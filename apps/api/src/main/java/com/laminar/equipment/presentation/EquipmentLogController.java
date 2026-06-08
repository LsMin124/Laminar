package com.laminar.equipment.presentation;

import com.laminar.equipment.application.EquipmentLogService;
import com.laminar.equipment.domain.EquipmentLogColumnEntity;
import com.laminar.equipment.domain.EquipmentLogColumnType;
import com.laminar.equipment.domain.EquipmentLogEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api — 장비 로그 시트 (동적 컬럼 정의 + 행 단위 값).
 *
 * <p>subject-shared. 컬럼·로그 모두 멤버 read·write (service에서 scope 강제). 컬럼 type별 값 검증은 service 책임.
 */
@RestController
@RequestMapping("/api")
public class EquipmentLogController {

  private final EquipmentLogService service;

  public EquipmentLogController(EquipmentLogService service) {
    this.service = service;
  }

  // ── 컬럼 정의 ──────────────────────────────────────────────────────────

  @GetMapping("/equipment/{equipmentId}/log-columns")
  public ResponseEntity<List<ColumnResponse>> listColumns(@PathVariable UUID equipmentId) {
    return ResponseEntity.ok(
        service.listColumns(equipmentId).stream().map(EquipmentLogController::toColumn).toList());
  }

  @PostMapping("/equipment/{equipmentId}/log-columns")
  public ResponseEntity<ColumnResponse> createColumn(
      @PathVariable UUID equipmentId, @Valid @RequestBody CreateColumnRequest request) {
    EquipmentLogColumnEntity column =
        service.createColumn(
            equipmentId,
            request.columnKey(),
            request.columnLabel(),
            request.columnType(),
            request.enumValues(),
            request.required(),
            request.defaultValue());
    return ResponseEntity.ok(toColumn(column));
  }

  @DeleteMapping("/log-columns/{columnId}")
  public ResponseEntity<Void> deleteColumn(@PathVariable UUID columnId) {
    service.softDeleteColumn(columnId);
    return ResponseEntity.noContent().build();
  }

  // ── 로그 행 ────────────────────────────────────────────────────────────

  @GetMapping("/equipment/{equipmentId}/logs")
  public ResponseEntity<List<LogResponse>> listLogs(@PathVariable UUID equipmentId) {
    return ResponseEntity.ok(
        service.listLogs(equipmentId).stream().map(EquipmentLogController::toLog).toList());
  }

  @PostMapping("/equipment/{equipmentId}/logs")
  public ResponseEntity<LogResponse> log(
      @PathVariable UUID equipmentId, @Valid @RequestBody LogRequest request) {
    EquipmentLogEntity entry =
        service.log(
            equipmentId,
            request.loggedAt(),
            request.reservationId(),
            request.values(),
            request.notes());
    return ResponseEntity.ok(toLog(entry));
  }

  // ── DTO ────────────────────────────────────────────────────────────────

  public record CreateColumnRequest(
      @NotBlank @Size(max = 100) String columnKey,
      @NotBlank @Size(max = 200) String columnLabel,
      @NotNull EquipmentLogColumnType columnType,
      List<String> enumValues,
      boolean required,
      @Size(max = 500) String defaultValue) {}

  public record ColumnResponse(
      UUID id,
      UUID equipmentId,
      String columnKey,
      String columnLabel,
      EquipmentLogColumnType columnType,
      List<String> enumValues,
      boolean required,
      int priority,
      String defaultValue) {}

  public record LogRequest(
      OffsetDateTime loggedAt,
      UUID reservationId,
      Map<String, Object> values,
      @Size(max = 2000) String notes) {}

  public record LogResponse(
      UUID id,
      UUID equipmentId,
      UUID loggedBy,
      OffsetDateTime loggedAt,
      UUID reservationId,
      Map<String, Object> values,
      String notes,
      OffsetDateTime createdAt) {}

  private static ColumnResponse toColumn(EquipmentLogColumnEntity c) {
    return new ColumnResponse(
        c.getId(),
        c.getEquipmentId(),
        c.getColumnKey(),
        c.getColumnLabel(),
        c.getColumnType(),
        c.getEnumValues(),
        c.isRequired(),
        c.getPriority(),
        c.getDefaultValue());
  }

  private static LogResponse toLog(EquipmentLogEntity e) {
    return new LogResponse(
        e.getId(),
        e.getEquipmentId(),
        e.getLoggedBy(),
        e.getLoggedAt(),
        e.getReservationId(),
        e.getValues(),
        e.getNotes(),
        e.getCreatedAt());
  }
}
