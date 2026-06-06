import { useEffect, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import "katex/dist/katex.min.css";
import { useTabGraph, useUpdateCard } from "../../lib/dag";
import "./CardBody.css";

/**
 * 카드 본문 — 이미지·수식(KaTeX)·GFM 마크다운 렌더 + 보기↔편집 토글.
 * raw HTML 경로(rehype-raw) 없이 렌더하므로 마크다운 외 임의 HTML은 이스케이프된다.
 */
export function CardBody({ cardId, tabId }: { cardId: string; tabId: string }) {
  const graph = useTabGraph(tabId);
  const updateCard = useUpdateCard(tabId);
  const card = graph.data?.cards.find((c) => c.id === cardId);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");

  // 편집 중이 아닐 때만 서버 값으로 draft 동기화.
  useEffect(() => {
    if (!editing) setDraft(card?.bodyMd ?? "");
  }, [card?.bodyMd, editing]);

  if (!card) {
    return (
      <div className="cb-empty">
        {graph.isLoading ? "불러오는 중..." : "카드를 찾을 수 없습니다."}
      </div>
    );
  }

  function save() {
    updateCard.mutate({ cardId, bodyMd: draft });
    setEditing(false);
  }
  function cancel() {
    setDraft(card?.bodyMd ?? "");
    setEditing(false);
  }

  const hasBody = !!card.bodyMd && card.bodyMd.trim().length > 0;

  return (
    <div className="cb">
      <div className="cb-head">
        <strong className="cb-title">{card.title || "(제목 없음)"}</strong>
        {editing ? (
          <span className="cb-actions">
            <button type="button" className="cb-btn" onClick={save}>
              저장
            </button>
            <button type="button" className="cb-btn" onClick={cancel}>
              취소
            </button>
          </span>
        ) : (
          <button type="button" className="cb-btn" onClick={() => setEditing(true)}>
            편집
          </button>
        )}
      </div>
      <div className="cb-body">
        {editing ? (
          <textarea
            className="cb-editor"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="마크다운 본문 — 이미지 ![alt](url), 수식 인라인 $E=mc^2$ / 블록 $$ ... $$"
            autoFocus
          />
        ) : hasBody ? (
          <div className="cb-md">
            <ReactMarkdown
              remarkPlugins={[remarkGfm, remarkMath]}
              rehypePlugins={[rehypeKatex]}
            >
              {card.bodyMd ?? ""}
            </ReactMarkdown>
          </div>
        ) : (
          <div className="cb-placeholder">본문이 비어 있습니다. “편집”으로 작성하세요.</div>
        )}
      </div>
    </div>
  );
}
