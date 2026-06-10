import { useMemo, useState } from "react";
import { useMoveCard, useTabGraph, useUpdateCard, type Card } from "../../lib/dag";
import { MS_DAY, parseDate, fmtDate, todayUtc, startOfMonth } from "../../lib/dateUtil";
import { apiErrorMessage } from "../../lib/apiErrors";
import { useDialogs } from "../ui/DialogProvider";
import "./CalendarView.css";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

/** 카테고리 색(hex)을 막대 배경용 저알파 rgba로 — 어두운 셀 위에 옅은 틴트. */
function hexToRgba(hex: string, alpha: number): string {
  const h = hex.replace("#", "");
  const n =
    h.length === 3
      ? h
          .split("")
          .map((c) => c + c)
          .join("")
      : h;
  const r = parseInt(n.slice(0, 2), 16);
  const g = parseInt(n.slice(2, 4), 16);
  const b = parseInt(n.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/** 한 주(7칸) 안에서 카드가 차지하는 막대 세그먼트 — 칸 범위 + 주 경계 연속 여부 + 적층 레인. */
type Seg = {
  card: Card;
  startCol: number;
  endCol: number;
  isStart: boolean; // 카드 시작일이 이 주 안 (false면 이전 주에서 이어짐)
  isEnd: boolean; // 카드 종료일이 이 주 안 (false면 다음 주로 이어짐)
  lane: number;
};

/**
 * 캘린더 투영 — DAG 캔버스와 같은 탭 데이터(useTabGraph 캐시 공유)를 월 그리드에 투영.
 * 멀티데이 카드는 주 단위 가로 스팬 막대로 그린다. 막대를 다른 날로 드래그하면 startDate 변경(span 보존).
 */
export function CalendarView({ tabId }: { tabId: string }) {
  const graph = useTabGraph(tabId);
  const moveCard = useMoveCard(tabId);
  const updateCard = useUpdateCard(tabId);
  const dialogs = useDialogs();
  const [monthMs, setMonthMs] = useState(() => startOfMonth(todayUtc()));
  const [hideCompleted, setHideCompleted] = useState(false);

  const cards = useMemo(() => graph.data?.cards ?? [], [graph.data]);
  const cardCategoryIds = graph.data?.cardCategoryIds ?? {};
  const categoryColorById = useMemo(() => {
    const m = new Map<string, string | null>();
    for (const cat of graph.data?.categories ?? []) m.set(cat.id, cat.color);
    return m;
  }, [graph.data]);
  const today = todayUtc();
  const monthIndex = new Date(monthMs).getUTCMonth();

  const days = useMemo(() => {
    const gridStart = monthMs - new Date(monthMs).getUTCDay() * MS_DAY;
    return Array.from({ length: 42 }, (_, i) => gridStart + i * MS_DAY);
  }, [monthMs]);

  // 6개 주 행. 각 주에서 카드가 차지하는 칸 범위를 막대 세그먼트로 산출 + 겹침은 레인으로 적층.
  const weeks = useMemo(() => {
    const out: { weekStart: number; days: number[]; segs: Seg[]; lanes: number }[] = [];
    for (let w = 0; w < 6; w++) {
      const weekDays = days.slice(w * 7, w * 7 + 7);
      const weekStart = weekDays[0];
      const weekEnd = weekDays[6];
      const raw: Omit<Seg, "lane">[] = [];
      for (const c of cards) {
        if (!c.startDate || (hideCompleted && c.completed)) continue;
        const cs = parseDate(c.startDate);
        const ce = c.endDate ? Math.max(cs, parseDate(c.endDate)) : cs;
        if (ce < weekStart || cs > weekEnd) continue;
        raw.push({
          card: c,
          startCol: Math.max(0, Math.round((cs - weekStart) / MS_DAY)),
          endCol: Math.min(6, Math.round((ce - weekStart) / MS_DAY)),
          isStart: cs >= weekStart,
          isEnd: ce <= weekEnd,
        });
      }
      raw.sort((a, b) => a.startCol - b.startCol || a.endCol - b.endCol);
      const laneEnd: number[] = [];
      const segs: Seg[] = raw.map((s) => {
        let lane = laneEnd.findIndex((end) => end < s.startCol);
        if (lane === -1) lane = laneEnd.length;
        laneEnd[lane] = s.endCol;
        return { ...s, lane };
      });
      out.push({ weekStart, days: weekDays, segs, lanes: laneEnd.length });
    }
    return out;
  }, [days, cards, hideCompleted]);

  async function reportError(err: unknown) {
    // 오류 code 기반 매핑(lib/apiErrors) — 캘린더에서는 화살표를 끊을 수 없어 안내를 override(DX-4).
    const msg = apiErrorMessage(err, "작업에 실패했습니다.", {
      CARD_BEFORE_PREDECESSOR: "선행 카드보다 앞 날짜로 옮길 수 없습니다. (캔버스에서 화살표를 끊고 이동하세요)",
    });
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
        <span className="cal-hint">막대를 다른 날짜로 드래그해 일정 변경</span>
      </div>
      <div className="cal-weekdays">
        {WEEKDAYS.map((w) => (
          <div key={w} className="cal-weekday">
            {w}
          </div>
        ))}
      </div>
      <div className="cal-grid">
        {weeks.map((week, wi) => (
          <div
            key={wi}
            className="cal-week"
            style={{ minHeight: Math.max(84, 26 + week.lanes * 20) }}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              const id = e.dataTransfer.getData("text/plain");
              if (!id) return;
              const rect = e.currentTarget.getBoundingClientRect();
              const col = Math.min(
                6,
                Math.max(0, Math.floor((e.clientX - rect.left) / (rect.width / 7))),
              );
              reschedule(id, week.weekStart + col * MS_DAY);
            }}
          >
            {week.days.map((dayMs) => {
              const d = new Date(dayMs);
              const otherMonth = d.getUTCMonth() !== monthIndex;
              return (
                <div
                  key={dayMs}
                  className={`cal-cell${otherMonth ? " other" : ""}${dayMs === today ? " today" : ""}`}
                >
                  <div className="cal-date">{d.getUTCDate()}</div>
                </div>
              );
            })}
            <div className="cal-bars">
              {week.segs.map((s) => {
                const span = s.endCol - s.startCol + 1;
                const catId = cardCategoryIds[s.card.id];
                const catColor = catId ? (categoryColorById.get(catId) ?? null) : null;
                return (
                  <div
                    key={s.card.id}
                    className={`cal-bar${s.card.completed ? " completed" : ""}${
                      s.isStart ? "" : " cont-left"
                    }${s.isEnd ? "" : " cont-right"}`}
                    draggable
                    onDragStart={(e) => e.dataTransfer.setData("text/plain", s.card.id)}
                    title={s.card.title}
                    style={{
                      left: `calc(${((s.startCol / 7) * 100).toFixed(4)}% + 2px)`,
                      width: `calc(${((span / 7) * 100).toFixed(4)}% - 4px)`,
                      top: 24 + s.lane * 20,
                      ...(catColor
                        ? { borderColor: catColor, background: hexToRgba(catColor, 0.22) }
                        : {}),
                    }}
                  >
                    {s.isStart && (
                      <input
                        type="checkbox"
                        className="cal-bar-check"
                        checked={s.card.completed}
                        onClick={(e) => e.stopPropagation()}
                        onChange={(e) =>
                          updateCard.mutate({ cardId: s.card.id, completed: e.target.checked })
                        }
                      />
                    )}
                    <span className="cal-bar-title">{s.card.title || "(제목 없음)"}</span>
                    {s.isStart && !s.card.allDay && s.card.startTime && (
                      <span className="cal-bar-time">{s.card.startTime.slice(0, 5)}</span>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>
      {graph.isLoading && <p className="loading">불러오는 중...</p>}
    </div>
  );
}
