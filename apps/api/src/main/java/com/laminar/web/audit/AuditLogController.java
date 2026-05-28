package com.laminar.web.audit;

import com.laminar.audit.AuditLogEntity;
import com.laminar.audit.AuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogDtos.AuditLogResponse>> listRecent(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(
                service.listRecent(limit).stream().map(this::toResponse).toList());
    }

    @GetMapping("/range")
    public ResponseEntity<List<AuditLogDtos.AuditLogResponse>> listInRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ResponseEntity.ok(
                service.listInRange(from, to).stream().map(this::toResponse).toList());
    }

    private AuditLogDtos.AuditLogResponse toResponse(AuditLogEntity e) {
        return new AuditLogDtos.AuditLogResponse(
                e.getId(), e.getWorkspaceId(), e.getActorUserId(),
                e.getAction(), e.getTargetType(), e.getTargetId(),
                e.getSummary(), e.getPayload(), e.getOccurredAt());
    }
}
