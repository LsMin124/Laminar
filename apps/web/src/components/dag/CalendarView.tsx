import { useMemo, useState } from "react";
import { useMoveCard, useTabGraph, useUpdateCard, type Card } from "../../lib/dag";
import { ApiError } from "../../lib/api";
import { useDialogs } from "../ui/DialogProvider";
import "./CalendarView.css";

const MS_DAY = 86400000;
const MAX_SPAN_DAYS = 30;
const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

function parseDate(s: string): number {
  const [y, m, d] = s.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}
function fmtDate(ms: number): string {
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())}`;
}
function todayUtc(): number {
  const n = new Date();
  return Date.UTC(n.getFullYear(), n.getMonth(), n.getDate());
}
function startOfMonth(ms: number): number {
  const d = new Date(ms);
  return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1);
}

/**
 * 캘린더 투영 — DAG 캔버스와 같은 탭 데이터(useTabGraph 캐시 공유)를 월 그리드에 투영.
 * 카드를 다른 날짜 셀로 드래그하면 startDate 변경(멀티데이는 span 보존) → 캔버스에도 자동 반영(양방향).
 */
export function CalendarView({ tabId }: { tabId: string }) {
  const graph = useTabGraph(tabId);
  const moveCard = useMoveCard(tabId);
  const updateCard = useUpdateCard(tabId);
  const dialogs = useDialogs();
  const [monthMs, setMonthMs] = useState(() => startOfMonth(todayUtc()));
  const [hideCompleted, setHideCompleted] = useState(false);

  const cards = useMemo(() => graph.data?.cards ?? [], [graph.data]);
  const today = todayUtc();
  const monthIndex = new Date(monthMs).getUTCMonth();

  const days = useMemo(() => {
    const gridStart = monthMs - new Date(monthMs).getUTCDay() * MS_DAY;
    return Array.from({ length: 42 }, (_, i) => gridStart + i * MS_DAY);
  }, [monthMs]);

  // startDate 기준 셀 매핑(멀티데이는 시작일 셀에 표시 + 스팬 표시).
  const byDay = useMemo(() => {
    const map = new Map<number, Card[]>();
    for (const c of cards) {
      if (!c.startDate || (hideCompleted && c.completed)) continue;
      const k = parseDate(c.startDate);
      const arr = map.get(k) ?? [];
      arr.push(c);
      map.set(k, arr);
    }
    return map;
  }, [cards, hideCompleted]);

  async function reportError(err: unknown) {
    let msg = "작업에 실패했습니다.";
    if (err instanceof ApiError && err.status === 409) {
      const body = err.body as { message?: string } | string;
      const m = typeof body === "object" && body?.message ? body.message : "";
      msg = m.includes("predecessor")
        ? "선행 카드보다 앞 날짜로 옮길 수 없습니다. (캔버스에서 화살표를 끊고 이동하세요)"
        : m.includes("span")
          ? `기간은 최대 ${MAX_SPAN_DAYS}일까지입니다.`
          : "충돌이 발생했습니다.";
    }
    await dialogs.alert({ title: "처리 불가", message: msg });
  }

  function reschedule(cardId: string, dayMs: number) {
    const c = cards.find((x) => x.id === cardId);
    if (!c || (c.startDate && parseDate(c.startDate) === dayMs)) return;
    const input: { cardId: string; startDate: string; endDate?: string } = {
      cardId,
      startDate: fmtDate(dayMs),
    };
    if (c.startDate && c.endDate) {
      const delta = dayMs - parseDate(c.startDate);
      input.endDate = fmtDate(parseDate(c.endDate) + delta);
    }
    moveCard.mutate(input, { onError: reportError });
  }

  function shiftMonth(delta: number) {
    const d = new Date(monthMs);
    setMonthMs(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + delta, 1));
  }

  const monthLabel = `${new Date(monthMs).getUTCFullYear()}년 ${monthIndex + 1}월`;

  return (
    <div className="cal">
      <div className="cal-toolbar">
        <button type="button" onClick={() => shiftMonth(-1)}>
          ‹
        </button>
        <strong className="cal-month">{monthLabel}</strong>
        <button type="button" onClick={() => shiftMonth(1)}>
          ›
        </button>
        <button type="button" onClick={() => setMonthMs(startOfMonth(todayUtc()))}>
          오늘
        </button>
        <label className="cal-toggle">
          <input
            type="checkbox"
            checked={hideCompleted}
            onChange={(e) => setHideCompleted(e.target.checked)}
          />
          완료 숨기기
        </label>
        <span className="cal-hint">카드를 다른 날짜로 드래그해 일정 변경</span>
      </div>
      <div className="cal-weekdays">
        {WEEKDAYS.map((w) => (
          <div key={w} className="cal-weekday">
            {w}
          </div>
        ))}
      </div>
      <div className="cal-grid">
        {days.map((dayMs) => {
          const d = new Date(dayMs);
          const otherMonth = d.getUTCMonth() !== monthIndex;
          const dayCards = byDay.get(dayMs) ?? [];
          return (
            <div
              key={dayMs}
              className={`cal-cell${otherMonth ? " other" : ""}${dayMs === today ? " today" : ""}`}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => {
                e.preventDefault();
                const id = e.dataTransfer.getData("text/plain");
                if (id) reschedule(id, dayMs);
              }}
            >
              <div className="cal-date">{d.getUTCDate()}</div>
              <div className="cal-cell-cards">
                {dayCards.map((c) => (
                  <div
                    key={c.id}
                    className={`cal-chip${c.completed ? " completed" : ""}`}
                    draggable
                    onDragStart={(e) => e.dataTransfer.setData("text/plain", c.id)}
                    title={c.title}
                  >
                    <input
                      type="checkbox"
                      className="cal-chip-check"
                      checked={c.completed}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(e) =>
                        updateCard.mutate({ cardId: c.id, completed: e.target.checked })
                      }
                    />
                    <span className="cal-chip-title">{c.title || "(제목 없음)"}</span>
                    {!c.allDay && c.startTime && (
                      <span className="cal-chip-time">{c.startTime.slice(0, 5)}</span>
                    )}
                    {c.endDate && c.endDate !== c.startDate && (
                      <span className="cal-chip-span" title="멀티데이">
                        ›
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
      {graph.isLoading && <p className="loading">불러오는 중...</p>}
    </div>
  );
}
