import { useState, type ChangeEvent } from "react";
import { api, ApiError } from "../../lib/api";
import type {
  AttachmentParentType,
  AttachmentResponse,
  PresignedUrlResponse,
} from "../../lib/types";
import "./AttachmentUploader.css";

interface AttachmentUploaderProps {
  parentType: AttachmentParentType;
  parentId: string;
  onUploaded: (attachment: AttachmentResponse) => void;
  disabled?: boolean;
}

export function AttachmentUploader({
  parentType,
  parentId,
  onUploaded,
  disabled,
}: AttachmentUploaderProps) {
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    event.target.value = "";

    setError(null);
    setUploading(true);
    setProgress("업로드 URL 발급 중...");

    try {
      const presigned = await api.post<PresignedUrlResponse>(
        "/api/attachments/upload-url",
        { parentType, parentId, filename: file.name, mime: file.type },
      );

      setProgress("R2 업로드 중...");
      const putRes = await fetch(presigned.url, {
        method: "PUT",
        headers: { "Content-Type": file.type || "application/octet-stream" },
        body: file,
      });
      if (!putRes.ok) {
        throw new Error(`R2 PUT 실패: ${putRes.status}`);
      }

      setProgress("메타 등록 중...");
      const created = await api.post<AttachmentResponse>("/api/attachments", {
        parentType,
        parentId,
        storageKey: presigned.storageKey,
        originalName: file.name,
        mime: file.type,
        sizeBytes: file.size,
      });

      const finalized = await api.post<AttachmentResponse>(
        `/api/attachments/${created.id}/finalize`,
        { actualSizeBytes: file.size },
      );

      onUploaded(finalized);
      setProgress(null);
    } catch (e) {
      const message =
        e instanceof ApiError
          ? `${e.message}`
          : e instanceof Error
            ? e.message
            : String(e);
      setError(`업로드 실패: ${message}`);
      setProgress(null);
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="attachment-uploader">
      <label
        className={`attachment-uploader-btn${disabled || uploading ? " disabled" : ""}`}
      >
        <input
          type="file"
          onChange={handleFile}
          disabled={disabled || uploading}
        />
        {uploading ? (progress ?? "업로드 중...") : "+ 첨부 추가"}
      </label>
      {error && <p className="attachment-uploader-error">{error}</p>}
    </div>
  );
}
