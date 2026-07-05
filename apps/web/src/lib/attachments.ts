/**
 * 첨부(이미지) — presign → R2 raw PUT → 메타 생성 → finalize. 인라인 표시 URL 조회.
 * R2 PUT은 쿠키/CSRF 없이 직접(서명된 URL) — CSP connect-src에 R2 허용 필요(SecurityConfig).
 */
import { useQuery } from "@tanstack/react-query";
import { api } from "./api";

const IMAGE_MIME = new Set(["image/png", "image/jpeg", "image/gif", "image/webp"]);

export function isSupportedImage(file: File): boolean {
  return IMAGE_MIME.has(file.type);
}

interface PresignResponse {
  url: string;
  storageKey: string;
  expiresInSeconds: number;
}

/**
 * 이미지 파일을 R2에 업로드하고 attachment id를 반환. parentId로 소유 노드에 귀속.
 * presign(백엔드) → raw PUT(R2, 서명된 content-type과 정확히 일치해야 SigV4 통과) → 메타 → finalize(HEAD 크기 검증).
 */
export async function uploadImageAttachment(
  file: File,
  parentType: "WHITEBOARD_NODE",
  parentId: string,
): Promise<string> {
  const presign = await api.post<PresignResponse>("/api/attachments/upload-url", {
    parentType,
    parentId,
    filename: file.name,
    mime: file.type,
  });
  const put = await fetch(presign.url, {
    method: "PUT",
    headers: { "Content-Type": file.type },
    body: file,
  });
  if (!put.ok) throw new Error(`R2 업로드 실패: ${put.status}`);
  const meta = await api.post<{ id: string }>("/api/attachments", {
    parentType,
    parentId,
    storageKey: presign.storageKey,
    originalName: file.name,
    mime: file.type,
    sizeBytes: file.size,
  });
  await api.post(`/api/attachments/${meta.id}/finalize`, { actualSizeBytes: file.size });
  return meta.id;
}

/** 인라인 이미지 presigned URL(5분 TTL) — {@code <img src>}. 만료 전 자동 갱신. */
export function useAttachmentInlineUrl(attachmentId: string | null) {
  return useQuery({
    queryKey: ["attachment-inline", attachmentId],
    queryFn: () =>
      api.get<PresignResponse>(`/api/attachments/${attachmentId}/inline-url`).then((r) => r.url),
    enabled: !!attachmentId,
    staleTime: 4 * 60 * 1000,
    refetchInterval: 4 * 60 * 1000,
  });
}
