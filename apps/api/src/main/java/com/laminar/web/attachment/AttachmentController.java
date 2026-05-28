package com.laminar.web.attachment;

import com.laminar.attachment.AttachmentEntity;
import com.laminar.attachment.AttachmentParentType;
import com.laminar.attachment.AttachmentService;
import com.laminar.attachment.R2StorageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService service;
    private final R2StorageService storage;

    public AttachmentController(AttachmentService service, R2StorageService storage) {
        this.service = service;
        this.storage = storage;
    }

    @PostMapping
    public ResponseEntity<AttachmentDtos.AttachmentResponse> create(
            @Valid @RequestBody AttachmentDtos.CreateRequest request) {
        AttachmentEntity attachment = service.create(
                request.parentType(), request.parentId(),
                request.storageKey(), request.originalName(),
                request.mime(), request.sizeBytes(), request.sha256());
        return ResponseEntity.ok(toResponse(attachment));
    }

    @PostMapping("/{attachmentId}/finalize")
    public ResponseEntity<AttachmentDtos.AttachmentResponse> finalizeUpload(
            @PathVariable UUID attachmentId,
            @Valid @RequestBody AttachmentDtos.FinalizeRequest request) {
        return ResponseEntity.ok(toResponse(
                service.finalizeUpload(attachmentId, request.actualSizeBytes(), request.actualSha256())));
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<AttachmentDtos.AttachmentResponse> get(@PathVariable UUID attachmentId) {
        return service.findById(attachmentId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<AttachmentDtos.AttachmentResponse>> listByParent(
            @RequestParam AttachmentParentType parentType,
            @RequestParam UUID parentId) {
        return ResponseEntity.ok(
                service.listByParent(parentType, parentId).stream()
                        .map(this::toResponse)
                        .toList());
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID attachmentId) {
        service.softDelete(attachmentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload-url")
    public ResponseEntity<AttachmentDtos.PresignedUrlResponse> createUploadUrl(
            @Valid @RequestBody AttachmentDtos.PresignedUrlRequest request) {
        R2StorageService.PresignedUpload upload = storage.createUploadUrl(request.filename(), request.mime());
        return ResponseEntity.ok(new AttachmentDtos.PresignedUrlResponse(
                upload.url(), upload.storageKey(), upload.expiresInSeconds()));
    }

    @GetMapping("/{attachmentId}/download-url")
    public ResponseEntity<AttachmentDtos.PresignedUrlResponse> createDownloadUrl(@PathVariable UUID attachmentId) {
        return service.findById(attachmentId)
                .map(a -> storage.createDownloadUrl(a.getStorageKey()))
                .map(url -> new AttachmentDtos.PresignedUrlResponse(
                        url, null, R2StorageService.PRESIGN_TTL_SECONDS))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private AttachmentDtos.AttachmentResponse toResponse(AttachmentEntity a) {
        return new AttachmentDtos.AttachmentResponse(
                a.getId(), a.getWorkspaceId(), a.getUserId(), a.getUploadedBy(),
                a.getParentType(), a.getParentId(),
                a.getStorageKey(), a.getOriginalName(), a.getMime(),
                a.getSizeBytes(), a.getSha256(), a.isAccessCheckRequired(),
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
