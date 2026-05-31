import { useState } from "react";
import { CardForm, cardToFormValues, type CardFormValues } from "./CardForm";
import { AttachmentUploader } from "../attachment/AttachmentUploader";
import {
  useAttachmentsByParent,
  useCard,
  useCardRendered,
  useDeleteAttachment,
  useDeleteCard,
  useUpdateCard,
} from "../../lib/queries";
import { api } from "../../lib/api";
import { useDialogs } from "../ui/DialogProvider";
import type { PresignedUrlResponse } from "../../lib/types";
import "../../pages/CardDetailPage.css";
import "./CardInspector.css";

interface Props {
  cardId: string;
  boardId: string;
  onClose: () => void;
}

/**
 * 카드 인스펙터 — 보드 작업면 우측 패널에서 카드를 *페이지 이동 없이* 보고 편집한다 (P1).
 * 기존 CardDetailPage(라우트형)의 로직을 패널로 흡수: 라우트 param 대신 props, 보드 이동 대신 onClose.
 */
export function CardInspector({ cardId, boardId, onClose }: Props) {
  const [editing, setEditing] = useState(false);

  const dialogs = useDialogs();
  const card = useCard(cardId);
  const rendered = useCardRendered(cardId);
  const updateCard = useUpdateCard(cardId, boardId);
  const deleteCard = useDeleteCard(cardId, boardId);
  const attachments = useAttachmentsByParent("CARD", cardId);
  const deleteAttachment = useDeleteAttachment("CARD", cardId);

  async function handleUpdate(values: CardFormValues) {
    await updateCard.mutateAsync({
      title: values.title,
      bodyMd: values.bodyMd || "",
      startDate: values.startDate || null,
      endDate: values.endDate || null,
      startTime: values.startTime || null,
      allDay: values.allDay,
      importance: values.importance,
      rrule: values.rrule || null,
      completed: values.completed,
    });
    setEditing(false);
  }

  async function handleDelete() {
    const ok = await dialogs.confirm({
      title: "카드 삭제",
      message: `'${card.data?.title}' 카드를 삭제할까요? (soft delete)`,
      confirmLabel: "삭제",
      danger: true,
    });
    if (!ok) return;
    await deleteCard.mutateAsync();
    onClose();
  }

  async function handleDownload(attachmentId: string) {
    try {
      const presigned = await api.get<PresignedUrlResponse>(
        `/api/attachments/${attachmentId}/download-url`,
      );
      window.open(presigned.url, "_blank", "noopener,noreferrer");
    } catch (e) {
      await dialogs.alert({
        title: "다운로드 실패",
        message: `URL 발급 실패: ${e instanceof Error ? e.message : e}`,
      });
    }
  }

  return (
    <aside className="card-inspector">
      <header className="card-inspector-head">
        <span className="card-inspector-label">카드</span>
        <button
          type="button"
          className="card-inspector-close"
          onClick={onClose}
          aria-label="인스펙터 닫기"
        >
          ✕
        </button>
      </header>

      {card.isLoading ? (
        <p className="loading">카드 불러오는 중...</p>
      ) : card.error || !card.data ? (
        <p className="auth-error">카드를 찾을 수 없습니다.</p>
      ) : editing ? (
        <div className="card-inspector-body">
          <CardForm
            initial={cardToFormValues(card.data)}
            submitting={updateCard.isPending}
            submitLabel="저장"
            onCancel={() => setEditing(false)}
            onSubmit={handleUpdate}
          />
        </div>
      ) : (
        <div className="card-detail card-inspector-body">
          <div className="card-detail-actions card-inspector-actions">
            <span
              className={`card-importance importance-${card.data.importance.toLowerCase()}`}
            >
              {card.data.importance}
            </span>
            {card.data.completed && <span className="card-status">완료</span>}
            <span className="card-inspector-spacer" />
            <button
              type="button"
              className="card-detail-edit"
              onClick={() => setEditing(true)}
            >
              편집
            </button>
            <button
              type="button"
              className="card-detail-delete"
              onClick={handleDelete}
              disabled={deleteCard.isPending}
            >
              삭제
            </button>
          </div>
          <h1 className="card-detail-title">{card.data.title}</h1>
          <dl className="card-detail-meta">
            {card.data.startDate && (
              <>
                <dt>기간</dt>
                <dd>
                  {card.data.startDate}
                  {card.data.endDate &&
                  card.data.endDate !== card.data.startDate
                    ? ` ~ ${card.data.endDate}`
                    : ""}
                  {card.data.startTime && ` ${card.data.startTime}`}
                </dd>
              </>
            )}
            {card.data.rrule && (
              <>
                <dt>반복</dt>
                <dd>
                  <code>{card.data.rrule}</code>
                </dd>
              </>
            )}
            {card.data.origin !== "MANUAL" && (
              <>
                <dt>출처</dt>
                <dd>{card.data.origin}</dd>
              </>
            )}
          </dl>
          <div className="card-detail-body">
            {rendered.data?.html ? (
              <div
                className="markdown"
                dangerouslySetInnerHTML={{ __html: rendered.data.html }}
              />
            ) : card.data.bodyMd ? (
              <pre className="markdown-raw">{card.data.bodyMd}</pre>
            ) : (
              <p className="markdown-empty">내용 없음</p>
            )}
          </div>
          <section className="card-attachments">
            <header className="card-attachments-head">
              <h3>첨부</h3>
              <AttachmentUploader
                parentType="CARD"
                parentId={cardId}
                onUploaded={() => attachments.refetch()}
              />
            </header>
            {attachments.data && attachments.data.length > 0 ? (
              <ul className="attachment-list">
                {attachments.data.map((a) => (
                  <li key={a.id} className="attachment-item">
                    <button
                      type="button"
                      className="attachment-name"
                      onClick={() => handleDownload(a.id)}
                    >
                      {a.originalName ?? a.storageKey}
                    </button>
                    <span className="attachment-size">
                      {a.sizeBytes
                        ? `${(a.sizeBytes / 1024).toFixed(1)} KB`
                        : "—"}
                    </span>
                    <button
                      type="button"
                      className="attachment-delete"
                      onClick={() => deleteAttachment.mutate(a.id)}
                      disabled={deleteAttachment.isPending}
                    >
                      삭제
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="markdown-empty">첨부 없음</p>
            )}
          </section>
        </div>
      )}
    </aside>
  );
}
