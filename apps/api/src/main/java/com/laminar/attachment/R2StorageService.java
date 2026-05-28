package com.laminar.attachment;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * R2 presigned URL 발급 (5분 TTL).
 *
 * storage_key 형식: workspaces/{workspaceId}/users/{userId}/attachments/{uuid}/{filename}
 * 사용자별 격리된 path로 R2에 저장 — bucket policy로 cross-user 직접 접근 차단 가능 (인프라 책임).
 */
@Service
public class R2StorageService {

    public static final int PRESIGN_TTL_SECONDS = 300;

    private final S3Presigner presigner;
    private final String bucket;

    public R2StorageService(
            S3Presigner r2S3Presigner,
            @Value("${app.r2.bucket:laminar-attachments}") String bucket) {
        this.presigner = r2S3Presigner;
        this.bucket = bucket;
    }

    public PresignedUpload createUploadUrl(String filename, String mime) {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
            throw new IllegalStateException("PERSONAL scope required");
        }
        String storageKey = String.format(
                "workspaces/%s/users/%s/attachments/%s/%s",
                ctx.workspaceId(), ctx.userId(), UUID.randomUUID(), safeFilename(filename));

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(mime == null || mime.isBlank() ? "application/octet-stream" : mime)
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PRESIGN_TTL_SECONDS))
                .putObjectRequest(putRequest)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        return new PresignedUpload(presigned.url().toString(), storageKey, PRESIGN_TTL_SECONDS);
    }

    public String createDownloadUrl(String storageKey) {
        WorkspaceContext ctx = WorkspaceContextHolder.require();
        if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
            throw new IllegalStateException("PERSONAL scope required");
        }
        // M-5 방어심화: 호출자 소유 prefix의 키만 presign 허용 (엔티티 검증과 무관하게 fail-closed).
        String requiredPrefix = String.format(
                "workspaces/%s/users/%s/", ctx.workspaceId(), ctx.userId());
        if (storageKey == null || !storageKey.startsWith(requiredPrefix)) {
            throw new IllegalStateException("storage key not owned by current user");
        }
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(PRESIGN_TTL_SECONDS))
                .getObjectRequest(getRequest)
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "file";
        // path traversal·special char 제거. 클라이언트가 보낸 그대로 신뢰 0.
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    public record PresignedUpload(String url, String storageKey, int expiresInSeconds) {
    }
}
