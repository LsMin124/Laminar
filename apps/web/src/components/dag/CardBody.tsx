import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import "katex/dist/katex.min.css";
import { useTabGraph, useUpdateCard } from "../../lib/dag";
import "./CardBody.css";

// 플러그인 배열은 모듈 상수로 고정(렌더마다 새 배열 생성 방지).
const REMARK = [remarkGfm, remarkMath];
const REHYPE = [rehypeKatex];

const AUTOSAVE_MS = 600;

/** 마크다운 → 이미지·수식(KaTeX)·GFM 렌더 (클라이언트). raw HTML 경로 없음. */
function MarkdownView({ source }: { source: string }) {
  return (
    <div className="cb-md">
      <ReactMarkdown remarkPlugins={REMARK} rehypePlugins={REHYPE}>
        {source}
      </ReactMarkdown>
    </div>
  );
}

/**
 * 카드 본문 — 보기(전체폭 렌더) ↔ 편집(좌 입력 / 우 라이브 미리보기).
 * 편집 중 미리보기는 draft를 클라이언트에서 즉시 렌더(저장 불필요). 영속은 디바운스 자동저장.
 */
export function CardBody({ cardId, tabId }: { cardId: string; tabId: string }) {
  const graph = useTabGraph(tabId);
  const updateCard = useUpdateCard(tabId);
  const card = graph.data?.cards.find((c) => c.id === cardId);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [dirty, setDirty] = useState(false);
  const timer = useRef<number | null>(null);

  // 편집 중이 아닐 때만 서버 값으로 draft 동기화.
  useEffect(() => {
    if (!editing) setDraft(card?.bodyMd ?? "");
  }, [card?.bodyMd, editing]);
  // 언마운트 시 대기 중인 자동저장 타이머 정리.
  useEffect(() => () => clearTimer(), []);

  function clearTimer() {
    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }
  }

  if (!card) {
    return (
      <div className="cb-empty">
        {graph.isLoading ? "불러오는 중..." : "카드를 찾을 수 없습니다."}
      </div>
    );
  }

  function onEdit(value: string) {
    setDraft(value);
    setDirty(true);
    clearTimer();
    timer.current = window.setTimeout(() => {
      updateCard.mutate({ cardId, bodyMd: value }, { onSuccess: () => setDirty(false) });
    }, AUTOSAVE_MS);
  }
  function done() {
    clearTimer();
    if (dirty) {
      updateCard.mutate({ cardId, bodyMd: draft }, { onSuccess: () => setDirty(false) });
    }
    setEditing(false);
  }

  const hasBody = !!card.bodyMd && card.bodyMd.trim().length > 0;

  return (
    <div className="cb">
      <div className="cb-head">
        <strong className="cb-title">{card.title || "(제목 없음)"}</strong>
        {editing ? (
          <span className="cb-actions">
            <span className="cb-status">{dirty ? "● 변경됨" : "저장됨"}</span>
            <button type="button" className="cb-btn" onClick={done}>
              완료
            </button>
          </span>
        ) : (
          <button type="button" className="cb-btn" onClick={() => setEditing(true)}>
            편집
          </button>
        )}
      </div>

      {editing ? (
        <div className="cb-split">
          <textarea
            className="cb-editor"
            value={draft}
            onChange={(e) => onEdit(e.target.value)}
            placeholder="마크다운 본문 — 이미지 ![alt](url), 수식 인라인 $E=mc^2$ / 블록 $$ ... $$"
            autoFocus
          />
          <div className="cb-preview">
            {draft.trim().length > 0 ? (
              <MarkdownView source={draft} />
            ) : (
              <div className="cb-placeholder">입력하면 여기에 실시간 미리보기가 표시됩니다.</div>
            )}
          </div>
        </div>
      ) : (
        <div className="cb-body">
          {hasBody ? (
            <MarkdownView source={card.bodyMd ?? ""} />
          ) : (
            <div className="cb-placeholder">본문이 비어 있습니다. “편집”으로 작성하세요.</div>
          )}
        </div>
      )}
    </div>
  );
}
