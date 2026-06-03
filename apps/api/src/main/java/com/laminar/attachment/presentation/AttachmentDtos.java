package com.laminar.attachment.presentation;

import com.laminar.attachment.domain.AttachmentParentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class AttachmentDtos {

  private AttachmentDtos() {}

  public record CreateRequest(
      @NotNull AttachmentParentType parentType,
      @NotNull UUID parentId,
      @NotBlank @Size(max = 500) String storageKey,
      @Size(max = 500) String originalName,
      @Size(max = 200) String mime,
      Long sizeBytes,
      @Size(max = 100) String sha256) {}

  public record FinalizeRequest(Long actualSizeBytes, @Size(max = 100) String actualSha256) {}

  public record AttachmentResponse(
      UUID id,
      UUID subjectId,
      UUID userId,
      UUID uploadedBy,
      AttachmentParentType parentType,
      UUID parentId,
      String storageKey,
      String originalName,
      String mime,
      Long sizeBytes,
      String sha256,
      boolean accessCheckRequired,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {}

  public record PresignedUrlRequest(
      @NotNull AttachmentParentType parentType,
      @NotNull UUID parentId,
      @NotBlank String filename,
      @Size(max = 200) String mime) {}

  public record PresignedUrlResponse(String url, String storageKey, int expiresInSeconds) {}
}
