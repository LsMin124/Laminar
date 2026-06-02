package com.laminar.attachment;

import com.laminar.config.R2Properties;
import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * R2 presigned URL 발급 (5분 TTL).
 *
 * <p>storage_key 형식: workspaces/{workspaceId}/users/{userId}/attachments/{uuid}/{filename} 사용자별 격리된
 * path로 R2에 저장 — bucket policy로 cross-user 직접 접근 차단 가능 (인프라 책임).
 */
@Service
public class R2StorageService {

  public static final int PRESIGN_TTL_SECONDS = 300;

  /**
   * 업로드 허용 MIME allowlist (M-4). 스크립트 실행형(html/svg/js)은 제외 — 저장형 XSS 차단. 미상(octet-stream)은 허용하되
   * 다운로드 시 Content-Disposition: attachment로 강제 다운로드.
   */
  private static final Set<String> ALLOWED_MIME =
      Set.of(
          "image/png",
          "image/jpeg",
          "image/gif",
          "image/webp",
          "application/pdf",
          "text/plain",
          "text/csv",
          "text/markdown",
          "application/json",
          "application/zip",
          "application/msword",
          "application/vnd.ms-excel",
          "application/vnd.ms-powerpoint",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          "application/octet-stream");

  private final S3Presigner presigner;
  private final S3Client s3Client;
  private final String bucket;

  public R2StorageService(
      S3Presigner r2S3Presigner, S3Client r2S3Client, R2Properties r2Properties) {
    this.presigner = r2S3Presigner;
    this.s3Client = r2S3Client;
    this.bucket = r2Properties.bucket();
  }

  public PresignedUpload createUploadUrl(String filename, String mime) {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    if (ctx.scope() != WorkspaceContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL scope required");
    }
    String contentType =
        (mime == null || mime.isBlank()) ? "application/octet-stream" : mime.trim().toLowerCase();
    if (!ALLOWED_MIME.contains(contentType)) {
      throw new IllegalArgumentException("file type not allowed: " + contentType);
    }
    String storageKey =
        String.format(
            "workspaces/%s/users/%s/attachments/%s/%s",
            ctx.workspaceId(), ctx.userId(), UUID.randomUUID(), safeFilename(filename));

    PutObjectRequest putRequest =
        PutObjectRequest.builder().bucket(bucket).key(storageKey).contentType(contentType).build();
    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
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
    String requiredPrefix =
        String.format("workspaces/%s/users/%s/", ctx.workspaceId(), ctx.userId());
    if (storageKey == null || !storageKey.startsWith(requiredPrefix)) {
      throw new IllegalStateException("storage key not owned by current user");
    }
    GetObjectRequest getRequest =
        GetObjectRequest.builder()
            .bucket(bucket)
            .key(storageKey)
            // M-4: 인라인 렌더 대신 강제 다운로드 — 저장형 콘텐츠(html/svg) 실행 방지
            .responseContentDisposition("attachment")
            .build();
    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofSeconds(PRESIGN_TTL_SECONDS))
            .getObjectRequest(getRequest)
            .build();
    PresignedGetObjectRequest presigned = presigner.presignGetObject(presignRequest);
    return presigned.url().toString();
  }

  /**
   * 업로드된 R2 객체의 실제 크기를 서버측에서 검증 (N-4).
   *
   * <p>클라이언트가 보고하는 size는 위조 가능하다(AWS SDK presigned PUT은 content-length-range를 적용하지 못함). finalize
   * 시점에 R2에 HEAD하여 실제 저장 바이트를 확인하고, 한도 초과 시 객체를 즉시 삭제 후 거부 — 무제한 업로드로 인한 스토리지 고갈을 차단한다. 소유 prefix도
   * fail-closed 검증(M-5와 동일). HEAD/DELETE는 R2Config의 S3Client(서버측 직접 호출) 사용.
   *
   * @return 검증된 실제 바이트 수 (한도 이내)
   */
  public long verifyUploadedSize(String storageKey, long maxBytes) {
    WorkspaceContext ctx = WorkspaceContextHolder.require();
    String requiredPrefix =
        String.format("workspaces/%s/users/%s/", ctx.workspaceId(), ctx.userId());
    if (storageKey == null || !storageKey.startsWith(requiredPrefix)) {
      throw new IllegalStateException("storage key not owned by current user");
    }
    long actualSize;
    try {
      HeadObjectResponse head =
          s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build());
      actualSize = head.contentLength() == null ? 0L : head.contentLength();
    } catch (NoSuchKeyException e) {
      throw new IllegalArgumentException("업로드된 객체를 찾을 수 없습니다");
    }
    if (actualSize > maxBytes) {
      // 한도 초과 → 즉시 삭제(스토리지 보존 차단). 삭제 실패는 무시(베스트에포트 — cleanup cron 후속).
      try {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
      } catch (RuntimeException ignored) {
        // 삭제 실패해도 거부는 유지
      }
      throw new IllegalArgumentException("업로드 파일이 크기 제한을 초과했습니다");
    }
    return actualSize;
  }

  private static String safeFilename(String filename) {
    if (filename == null || filename.isBlank()) return "file";
    // path traversal·special char 제거. 클라이언트가 보낸 그대로 신뢰 0.
    return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
  }

  public record PresignedUpload(String url, String storageKey, int expiresInSeconds) {}
}
