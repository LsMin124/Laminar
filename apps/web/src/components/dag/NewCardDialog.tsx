import { useState } from "react";
import type { Category } from "../../lib/graphTypes";

/**
 * 새 카드 생성 폼 — 제목(필수) + 일자(선택, 비우면 미정) + 분류(선택).
 * 일자 기본값은 호출 맥락(툴바=오늘 / 빈 곳 더블클릭=클릭 위치 날짜)에서 전달받되 변경·삭제 가능.
 */
export function NewCardDialog({
  defaultDate,
  categories,
  onSubmit,
  onClose,
}: {
  defaultDate: string | null;
  categories: Category[];
  onSubmit: (input: {
    title: string;
    startDate: string | null;
    categoryId: string | null;
  }) => void;
  onClose: () => void;
}) {
  const [title, setTitle] = useState("");
  const [date, setDate] = useState(defaultDate ?? "");
  const [categoryId, setCategoryId] = useState("");

  function submit() {
    if (!title.trim()) return;
    onSubmit({ title: title.trim(), startDate: date || null, categoryId: categoryId || null });
  }

  return (
    <div className="ncard-overlay" onPointerDown={onClose}>
      <div
        className="ncard"
        role="dialog"
        aria-label="새 카드"
        onPointerDown={(e) => e.stopPropagation()}
        onKeyDown={(e) => {
          if (e.key === "Escape") onClose();
        }}
      >
        <header className="ncard-head">
          <strong>새 카드</strong>
          <button type="button" className="ncard-x" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </header>
        <div className="ncard-body">
          <label className="ncard-field">
            <span>제목</span>
            <input
              type="text"
              className="ncard-input"
              value={title}
              autoFocus
              placeholder="카드 제목"
              onChange={(e) => setTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") submit();
              }}
            />
          </label>
          <label className="ncard-field">
            <span>
              일자 <em>(선택 · 비우면 미정)</em>
            </span>
            <input
              type="date"
              className="ncard-input"
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </label>
          <label className="ncard-field">
            <span>
              분류 <em>(선택)</em>
            </span>
            <select
              className="ncard-input"
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
            >
              <option value="">분류 없음</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <footer className="ncard-foot">
          <button type="button" className="ncard-cancel" onClick={onClose}>
            취소
          </button>
          <button
            type="button"
            className="ncard-submit"
            disabled={!title.trim()}
            onClick={submit}
          >
            만들기
          </button>
        </footer>
      </div>
    </div>
  );
}
