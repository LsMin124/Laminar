import { useMemo } from "react";
import { format } from "date-fns";
import type { CardResponse } from "../../lib/types";
import {
  buildMonthGrid,
  isToday,
  layoutCardsOnMonth,
} from "../../lib/calendar";
import "./MonthGrid.css";

interface MonthGridProps {
  anchor: Date;
  cards: CardResponse[];
  dateMemos: { date: string; bodyMd: string }[];
  onCardClick?: (card: CardResponse) => void;
  onCellClick?: (iso: string) => void;
}

const WEEKDAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];

const IMPORTANCE_COLOR: Record<string, string> = {
  NORMAL: "var(--c-normal)",
  CF: "var(--c-cf)",
  URGENT: "var(--c-urgent)",
  PURCHASE: "var(--c-purchase)",
  PERPETUAL_VER: "var(--c-perpetual)",
  ARTICLE: "var(--c-article)",
  PROCESS: "var(--c-process)",
};

export function MonthGrid({
  anchor,
  cards,
  dateMemos,
  onCardClick,
  onCellClick,
}: MonthGridProps) {
  const grid = useMemo(() => buildMonthGrid(anchor), [anchor]);
  const { segments, maxLanesPerWeek } = useMemo(
    () => layoutCardsOnMonth(grid, cards),
    [grid, cards],
  );
  const memoMap = useMemo(() => {
    const m = new Map<string, string>();
    dateMemos.forEach((d) => m.set(d.date, d.bodyMd));
    return m;
  }, [dateMemos]);

  return (
    <div className="month-grid">
      <div className="month-grid-header">
        {WEEKDAY_LABELS.map((label, i) => (
          <div
            key={label}
            className={`month-grid-weekday${i === 0 ? " sunday" : i === 6 ? " saturday" : ""}`}
          >
            {label}
          </div>
        ))}
      </div>
      <div className="month-grid-body">
        {grid.weeks.map((week, weekIndex) => {
          const weekSegments = segments.filter(
            (s) => s.weekIndex === weekIndex,
          );
          const laneCount = Math.max(maxLanesPerWeek[weekIndex], 1);
          return (
            <div
              key={weekIndex}
              className="month-grid-week"
              style={{ "--lane-count": laneCount } as React.CSSProperties}
            >
              <div className="month-grid-cells">
                {week.map((cell, dayIndex) => (
                  <button
                    type="button"
                    key={cell.iso}
                    className={`month-grid-cell${cell.inMonth ? "" : " out-of-month"}${
                      isToday(cell.date) ? " today" : ""
                    }${dayIndex === 0 ? " sunday" : dayIndex === 6 ? " saturday" : ""}`}
                    onClick={() => onCellClick?.(cell.iso)}
                  >
                    <span className="month-grid-cell-num">
                      {format(cell.date, "d")}
                    </span>
                    {memoMap.has(cell.iso) && (
                      <span
                        className="month-grid-cell-memo"
                        title={memoMap.get(cell.iso)!}
                      >
                        {memoMap.get(cell.iso)!.slice(0, 24)}
                      </span>
                    )}
                  </button>
                ))}
              </div>
              <div className="month-grid-segments">
                {weekSegments.map((seg) => (
                  <button
                    type="button"
                    key={`${seg.card.id}-${seg.weekIndex}`}
                    className={`month-grid-segment importance-${seg.card.importance.toLowerCase()}${
                      seg.card.completed ? " completed" : ""
                    }${seg.continuesLeft ? " continues-left" : ""}${
                      seg.continuesRight ? " continues-right" : ""
                    }`}
                    style={{
                      gridColumnStart: seg.startCol + 1,
                      gridColumnEnd: seg.endCol + 2,
                      gridRow: seg.lane + 1,
                      background: IMPORTANCE_COLOR[seg.card.importance],
                    }}
                    title={seg.card.title}
                    onClick={(e) => {
                      e.stopPropagation();
                      onCardClick?.(seg.card);
                    }}
                  >
                    {!seg.continuesLeft && (
                      <span className="seg-title">{seg.card.title}</span>
                    )}
                  </button>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
