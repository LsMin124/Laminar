import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import rehypeKatex from "rehype-katex";
import "katex/dist/katex.min.css";
import "./CardBody.css";

// 플러그인 배열은 모듈 상수로 고정(렌더마다 새 배열 생성 방지).
const REMARK = [remarkGfm, remarkMath];
const REHYPE = [rehypeKatex];

const AUTOSAVE_MS = 1500;

/** 마크다운 → 이미지·수식(KaTeX)·GFM 렌더 (클라이언트). raw HTML 경로 없음. */
export function MarkdownView({ source }: { source: string }) {
  return (
    <div className="cb-md">
      <ReactMarkdown remarkPlugins={REMARK} rehypePlugins={REHYPE}>
        {source}
      </ReactMarkdown>
    </div>
  );
}

/**
 * 마크다운 문서 셸 — 보기(전체폭 렌더) ↔ 편집(좌 입력 / 우 라이브 미리보기).
 * 편집 중 미리보기는 draft를 클라이언트에서 즉시 렌더(저장 불필요). 영속은 디바운스 자동저장.
 *
 * 카드 본문·그룹 본문이 공유한다(엔티티별 wrapper가 title/value/onSave만 주입).
 */
export function MarkdownDoc({
  title,
  value,
  loading,
  missing,
  missingLabel,
  placeholder,
  onSave,
}: {
  title: string;
  /** 서버에 영속된 현재 값(없으면 null). */
  value: string | null;
  loading: boolean;
  missing: boolean;
  missingLabel: string;
  placeholder?: string;
  onSave: (md: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [dirty, setDirty] = useState(false);
  const timer = useRef<number | null>(null);
  const draftRef = useRef("");
  const dirtyRef = useRef(false);
  const valueRef = useRef(value);
  const onSaveRef = useRef(onSave);
  // latest-ref 동기화 — 소비자(flush·자동저장 타이머·언마운트)는 전부 커밋 후에 읽으므로
  // 렌더 중 대입 대신 커밋 후 대입으로 충분하다(react-hooks/refs 준수).
  useEffect(() => {
    valueRef.current = value;
    onSaveRef.current = onSave;
  });

  // 편집 중이 아닐 때만 서버 값으로 draft 동기화.
  useEffect(() => {
    if (!editing) {
      setDraft(value ?? "");
      draftRef.current = value ?? "";
      dirtyRef.current = false;
    }
  }, [value, editing]);

  // 대기 중 변경분을 즉시 저장(flush) — 변경 없거나 서버값과 동일하면 no-op(요청 절약).
  const flush = () => {
    if (!dirtyRef.current) return;
    dirtyRef.current = false;
    setDirty(false);
    const v = draftRef.current;
    if (v === (valueRef.current ?? "")) return;
    onSaveRef.current(v);
  };
  const flushRef = useRef(flush);
  useEffect(() => {
    flushRef.current = flush;
  });

  // 언마운트(탭 전환·닫기 포함) 시 타이머 정리 + 미저장분 flush(데이터 유실 방지).
  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
      flushRef.current();
    },
    [],
  );

  if (loading || missing) {
    return <div className="cb-empty">{loading ? "불러오는 중..." : missingLabel}</div>;
  }

  function onEdit(value: string) {
    setDraft(value);
    draftRef.current = value;
    setDirty(true);
    dirtyRef.current = true;
    if (timer.current) clearTimeout(timer.current);
    timer.current = window.setTimeout(() => flushRef.current(), AUTOSAVE_MS);
  }
  function done() {
    if (timer.current) clearTimeout(timer.current);
    flushRef.current();
    setEditing(false);
  }

  const hasBody = !!value && value.trim().length > 0;

  return (
    <div className="cb">
      <div className="cb-head">
        <strong className="cb-title">{title}</strong>
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
            onBlur={() => flushRef.current()}
            placeholder={placeholder}
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
            <MarkdownView source={value ?? ""} />
          ) : (
            <div className="cb-placeholder">본문이 비어 있습니다. “편집”으로 작성하세요.</div>
          )}
        </div>
      )}
    </div>
  );
}
