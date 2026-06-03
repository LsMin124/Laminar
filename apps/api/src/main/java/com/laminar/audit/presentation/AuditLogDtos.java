package com.laminar.audit.presentation;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class AuditLogDtos {

  private AuditLogDtos() {}

  public record AuditLogResponse(
      UUID id,
      UUID subjectId,
      UUID actorUserId,
      String action,
      String targetType,
      UUID targetId,
      String summary,
      Map<String, Object> payload,
      OffsetDateTime occurredAt) {}
}
