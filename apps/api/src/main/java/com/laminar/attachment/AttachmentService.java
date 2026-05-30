package com.laminar.attachment;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 첨부 메타 CRUD — Personal-First.
 *
 * Spec §2.5.1: 20MB byte 한도 (DB CHECK + service 검증), parent_type=card/perpetual.
 * 실제 byte는 R2에 저장 (이 서비스는 메타만). presigned URL은 R2StorageService.
 */
@Service
public class AttachmentService {

    static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    private final AttachmentRepository attachmentRepo;
    private final R2StorageService r2Storage;

    public AttachmentService(AttachmentRepository attachmentRepo, R2StorageService r2Storage) {
        this.attachmentRepo = attachmentRepo;
        this.r2Storage = r2Storage;
    }

    @Transactional
    public AttachmentEntity create(
            AttachmentParentType parentType,
            UUID parentId,
            String storageKey,
            String originalName,
            String mime,
            Long sizeBytes,
            String sha256) {
        WorkspaceContext ctx = requirePersonalWritable();
        if (parentType == null) {
            throw new IllegalArgumentException("parent_type required");
        }
        if (parentId == null) {
            throw new IllegalArgumentException("parent_id required");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storage_key required");
        }
        if (sizeBytes != null && sizeBytes > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("size exceeds " + MAX_SIZE_BYTES + " bytes");
        }

        AttachmentEntity attachment = new AttachmentEntity();
        attachment.setWorkspaceId(ctx.workspaceId());
        attachment.setUserId(ctx.userId());
        attachment.setUploadedBy(ctx.userId());
        attachment.setParentType(parentType);
        attachment.setParentId(parentId);
        attachment.setStorageKey(storageKey);
        attachment.setOriginalName(originalName);
        attachment.setMime(mime);
        attachment.setSizeBytes(sizeBytes);
        attachment.setSha256(sha256);
        attachment.setAccessCheckRequired(true);
        return attachmentRepo.save(attachment);
    }

    /**
     * 업로드 완료 후 finalize — sha256·size 확인 후 access_check_required=false.
     */
    @Transactional
    public AttachmentEntity finalizeUpload(UUID attachmentId, Long actualSizeBytes, String actualSha256) {
        WorkspaceContext ctx = requirePersonalWritable();
        AttachmentEntity attachment = attachmentRepo.findById(attachmentId)
                .filter(a -> a.getDeletedAt() == null)
                .filter(a -> ctx.ownsPersonal(a.getWorkspaceId(), a.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("attachment not found"));
        // N-4: 클라이언트 자칭 크기(actualSizeBytes)는 위조 가능 → R2의 실제 객체 크기를 HEAD로
        // 검증한다. 한도 초과 시 R2StorageService가 객체를 삭제하고 거부(스토리지 고갈 차단).
        long verifiedSize = r2Storage.verifyUploadedSize(attachment.getStorageKey(), MAX_SIZE_BYTES);
        attachment.setSizeBytes(verifiedSize);
        attachment.setSha256(actualSha256);
        attachment.setAccessCheckRequired(false);
        return attachmentRepo.save(attachment);
    }

    @Transactional(readOnly = true)
    public Optional<AttachmentEntity> findById(UUID attachmentId) {
        WorkspaceContext ctx = WorkspaceContextHolder.requirePersonal();
        return attachmentRepo.findById(attachmentId)
                .filter(a -> a.getDeletedAt() == null)
                .filter(a -> ctx.ownsPersonal(a.getWorkspaceId(), a.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<AttachmentEntity> listByParent(AttachmentParentType parentType, UUID parentId) {
        WorkspaceContextHolder.requirePersonal();
        return attachmentRepo.findByParentTypeAndParentIdAndDeletedAtIsNull(parentType, parentId);
    }

    @Transactional
    public void softDelete(UUID attachmentId) {
        WorkspaceContext ctx = requirePersonalWritable();
        attachmentRepo.findById(attachmentId)
                .filter(a -> a.getDeletedAt() == null)
                .filter(a -> ctx.ownsPersonal(a.getWorkspaceId(), a.getUserId()))
                .ifPresent(a -> {
                    a.setDeletedAt(OffsetDateTime.now());
                    attachmentRepo.save(a);
                });
    }

    private WorkspaceContext requirePersonalWritable() {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
            throw new IllegalStateException("PERSONAL scope required");
        }
        if (!ctx.canWrite()) {
            throw new IllegalStateException("VIEWER cannot mutate attachments");
        }
        return ctx;
    }
}
