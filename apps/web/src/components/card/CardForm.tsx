import { useState, type FormEvent } from "react";
import { MarkdownEditor } from "../editor/MarkdownEditor";
import type { CardImportance, CardResponse } from "../../lib/types";
import "./CardForm.css";

const IMPORTANCE_OPTIONS: CardImportance[] = [
  "NORMAL",
  "CF",
  "URGENT",
  "PURCHASE",
  "PERPETUAL_VER",
  "ARTICLE",
  "PROCESS",
];

export interface CardFormValues {
  title: string;
  bodyMd: string;
  startDate: string;
  endDate: string;
  startTime: string;
  allDay: boolean;
  importance: CardImportance;
  rrule: string;
  completed: boolean;
}

export function emptyCardForm(initialDate?: string): CardFormValues {
  return {
    title: "",
    bodyMd: "",
    startDate: initialDate ?? "",
    endDate: "",
    startTime: "",
    allDay: true,
    importance: "NORMAL",
    rrule: "",
    completed: false,
  };
}

export function cardToFormValues(card: CardResponse): CardFormValues {
  return {
    title: card.title,
    bodyMd: card.bodyMd ?? "",
    startDate: card.startDate ?? "",
    endDate: card.endDate ?? "",
    startTime: card.startTime ?? "",
    allDay: card.allDay,
    importance: card.importance,
    rrule: card.rrule ?? "",
    completed: card.completed,
  };
}

interface CardFormProps {
  initial: CardFormValues;
  submitting: boolean;
  submitLabel: string;
  onCancel: () => void;
  onSubmit: (values: CardFormValues) => Promise<void> | void;
}

export function CardForm({
  initial,
  submitting,
  submitLabel,
  onCancel,
  onSubmit,
}: CardFormProps) {
  const [values, setValues] = useState<CardFormValues>(initial);
  const [error, setError] = useState<string | null>(null);

  function update<K extends keyof CardFormValues>(
    key: K,
    val: CardFormValues[K],
  ) {
    setValues((prev) => ({ ...prev, [key]: val }));
  }

  async function handle(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (!values.title.trim()) {
      setError("제목은 비울 수 없습니다.");
      return;
    }
    if (values.endDate && values.startDate && values.endDate < values.startDate) {
      setError("종료일은 시작일 이후여야 합니다.");
      return;
    }
    try {
      await onSubmit(values);
    } catch (e) {
      setError(`저장 실패: ${e instanceof Error ? e.message : String(e)}`);
    }
  }

  return (
    <form className="card-form" onSubmit={handle}>
      <label className="card-form-row">
        <span>제목</span>
        <input
          type="text"
          value={values.title}
          onChange={(e) => update("title", e.target.value)}
          maxLength={200}
          required
          autoFocus
        />
      </label>
      <div className="card-form-grid">
        <label>
          <span>중요도</span>
          <select
            value={values.importance}
            onChange={(e) =>
              update("importance", e.target.value as CardImportance)
            }
          >
            {IMPORTANCE_OPTIONS.map((imp) => (
              <option key={imp} value={imp}>
                {imp}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>시작일</span>
          <input
            type="date"
            value={values.startDate}
            onChange={(e) => update("startDate", e.target.value)}
          />
        </label>
        <label>
          <span>종료일</span>
          <input
            type="date"
            value={values.endDate}
            onChange={(e) => update("endDate", e.target.value)}
          />
        </label>
        <label>
          <span>시작 시간</span>
          <input
            type="time"
            value={values.startTime}
            onChange={(e) => update("startTime", e.target.value)}
            disabled={values.allDay}
          />
        </label>
        <label className="card-form-checkbox">
          <input
            type="checkbox"
            checked={values.allDay}
            onChange={(e) => update("allDay", e.target.checked)}
          />
          <span>종일</span>
        </label>
        <label className="card-form-checkbox">
          <input
            type="checkbox"
            checked={values.completed}
            onChange={(e) => update("completed", e.target.checked)}
          />
          <span>완료</span>
        </label>
      </div>
      <label className="card-form-row">
        <span>반복 (RRULE)</span>
        <input
          type="text"
          placeholder="FREQ=WEEKLY;INTERVAL=1;COUNT=10"
          value={values.rrule}
          onChange={(e) => update("rrule", e.target.value)}
          maxLength={500}
        />
      </label>
      <label className="card-form-row">
        <span>본문 (Markdown)</span>
        <MarkdownEditor
          value={values.bodyMd}
          onChange={(v) => update("bodyMd", v)}
          minHeight={280}
        />
      </label>
      {error && <p className="auth-error">{error}</p>}
      <div className="card-form-actions">
        <button type="button" onClick={onCancel} disabled={submitting}>
          취소
        </button>
        <button type="submit" disabled={submitting} className="primary">
          {submitting ? "저장 중..." : submitLabel}
        </button>
      </div>
    </form>
  );
}
