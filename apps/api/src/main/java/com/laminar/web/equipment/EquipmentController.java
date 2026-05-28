package com.laminar.web.equipment;

import com.laminar.equipment.EquipmentEntity;
import com.laminar.equipment.EquipmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /api/equipment — 공용 자원 (장비) CRUD.
 *
 * workspace-shared. 모든 멤버 read·write, 삭제는 OWNER만 (service에서 강제).
 */
@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {

    private final EquipmentService service;

    public EquipmentController(EquipmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EquipmentResponse> create(@Valid @RequestBody CreateRequest request) {
        EquipmentEntity created = service.create(
                request.name(), request.description(), request.location(),
                request.defaultLogColumns());
        return ResponseEntity.ok(toResponse(created));
    }

    @GetMapping
    public ResponseEntity<List<EquipmentResponse>> list(
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        List<EquipmentEntity> result = activeOnly ? service.listActive() : service.listAll();
        return ResponseEntity.ok(result.stream().map(EquipmentController::toResponse).toList());
    }

    @GetMapping("/{equipmentId}")
    public ResponseEntity<EquipmentResponse> get(@PathVariable UUID equipmentId) {
        return service.findById(equipmentId)
                .map(EquipmentController::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{equipmentId}")
    public ResponseEntity<EquipmentResponse> update(
            @PathVariable UUID equipmentId,
            @Valid @RequestBody UpdateRequest request) {
        EquipmentEntity updated = service.update(
                equipmentId, request.name(), request.description(), request.location(),
                request.defaultLogColumns());
        return ResponseEntity.ok(toResponse(updated));
    }

    @PostMapping("/{equipmentId}/toggle-active")
    public ResponseEntity<EquipmentResponse> toggleActive(
            @PathVariable UUID equipmentId,
            @RequestBody ToggleRequest request) {
        return ResponseEntity.ok(toResponse(service.toggleActive(equipmentId, request.activeValue())));
    }

    @DeleteMapping("/{equipmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID equipmentId) {
        service.softDelete(equipmentId);
        return ResponseEntity.noContent().build();
    }

    public record CreateRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @Size(max = 200) String location,
            List<Map<String, Object>> defaultLogColumns
    ) {
    }

    public record UpdateRequest(
            @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @Size(max = 200) String location,
            List<Map<String, Object>> defaultLogColumns
    ) {
    }

    public record ToggleRequest(@NotNull Boolean active) {
        public boolean activeValue() {
            return Boolean.TRUE.equals(this.active);
        }
    }

    public record EquipmentResponse(
            UUID id,
            UUID workspaceId,
            UUID createdBy,
            String name,
            String description,
            String location,
            boolean active,
            List<Map<String, Object>> defaultLogColumns,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    private static EquipmentResponse toResponse(EquipmentEntity e) {
        return new EquipmentResponse(
                e.getId(), e.getWorkspaceId(), e.getCreatedBy(),
                e.getName(), e.getDescription(), e.getLocation(),
                e.isActive(), e.getDefaultLogColumns(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
