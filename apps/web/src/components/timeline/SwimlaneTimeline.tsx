import { useMemo } from "react";
import { addDays, format, isSameDay, isToday, parseISO } from "date-fns";
import type { CardResponse, GroupResponse, TabResponse } from "../../lib/types";
import "./SwimlaneTimeline.css";

const COL_W = 170;
const WD = ["일", "월", "화", "수", "목", "금", "토"];

const IMPORTANCE_COLOR: Record<string, string> = {
  NORMAL: "#6b7280",
  CF: "#6366f1",
  URGENT: "#dc2626",
  PURCHASE: "#d97706",
  PERPETUAL_VER: "#059669",
  ARTICLE: "#7c3aed",
  PROCESS: "#0891b2",
};

interface Props {
  anchor: Date;
  dayCount: number;
  tabs: TabResponse[];
  groups: GroupResponse[];
  tabGroups: Record<string, string[]>;
  groupMembers: Record<string, string[]>;
  cards: CardResponse[];
  onCardClick: (cardId: string) => void;
}

/**
 * 스윔레인 타임라인 (재정렬 — 원본 Laminar 뷰) — 가로축 = 날짜 열, 세로축 = 탭 섹션 행.
 * 각 탭 섹션 안에 그룹(점선 밴드)별로 카드를 날짜 위치에 배치. (화살표·멀티데이 스팬·인라인 생성은 후속.)
 */
export function SwimlaneTimeline({
  anchor,
  dayCount,
  tabs,
  groups,
  tabGroups,
  groupMembers,
  cards,
  onCardClick,
}: Props) {
  const days = useMemo(
    () => Array.from({ length: dayCount }, (_, i) => addDays(anchor, i)),
    [anchor, dayCount],
  );
  const cardsById = useMemo(
    () => new Map(cards.map((c) => [c.id, c])),
    [cards],
  );
  const groupsById = useMemo(
    () => new Map(groups.map((g) => [g.id, g])),
    [groups],
  );
  const sections = useMemo(
    () => tabs.filter((t) => t.visible).sort((a, b) => a.priority - b.priority),
    [tabs],
  );

  function dayIndexOf(iso: string | null): number {
    if (!iso) return -1;
    const d = parseISO(iso);
    return days.findIndex((day) => isSameDay(day, d));
  }

  const gridStyle = {
    gridTemplateColumns: `repeat(${dayCount}, ${COL_W}px)`,
    width: `${dayCount * COL_W}px`,
  } as React.CSSProperties;

  return (
    <div className="swimlane">
      <div className="swimlane-scroll">
        <div className="swimlane-header" style={gridStyle}>
          {days.map((d) => (
            <div
              key={d.toISOString()}
              className={`swimlane-day${isToday(d) ? " today" : ""}${
                d.getDay() === 0 ? " sunday" : d.getDay() === 6 ? " saturday" : ""
              }`}
            >
              <span className="swimlane-day-date">{format(d, "MM-dd")}</span>
              <span className="swimlane-day-wd">({WD[d.getDay()]})</span>
            </div>
          ))}
        </div>

        {sections.length === 0 ? (
          <p className="swimlane-empty">
            표시할 탭(섹션)이 없습니다. 좌측 탭 패널에서 탭을 추가하세요.
          </p>
        ) : (
          sections.map((tab) => {
            const groupIds = tabGroups[tab.id] ?? [];
            const count = groupIds.reduce(
              (n, gid) => n + (groupMembers[gid]?.length ?? 0),
              0,
            );
            return (
              <section key={tab.id} className="swimlane-section">
                <div className="swimlane-section-head">
                  <span className="swimlane-section-name">{tab.name}</span>
                  <span className="swimlane-section-count">{count}</span>
                </div>
                {groupIds.length === 0 ? (
                  <p className="swimlane-section-empty">
                    그룹 없음 — 그래프 뷰의 스코프 바에서 그룹을 이 탭에 추가하세요.
                  </p>
                ) : (
                  groupIds.map((gid) => {
                    const group = groupsById.get(gid);
                    const byDay = new Map<number, CardResponse[]>();
                    (groupMembers[gid] ?? []).forEach((cid) => {
                      const card = cardsById.get(cid);
                      if (!card) return;
                      const idx = dayIndexOf(card.startDate);
                      if (idx < 0) return;
                      byDay.set(idx, [...(byDay.get(idx) ?? []), card]);
                    });
                    return (
                      <div key={gid} className="swimlane-group">
                        <span
                          className="swimlane-group-label"
                          style={
                            group?.color
                              ? { borderColor: group.color, color: group.color }
                              : undefined
                          }
                        >
                          {group?.name ?? "그룹"}
                        </span>
                        <div className="swimlane-grid" style={gridStyle}>
                          {days.map((_, i) => (
                            <div key={i} className="swimlane-cell">
                              {(byDay.get(i) ?? []).map((card) => (
                                <button
                                  key={card.id}
                                  type="button"
                                  className={`swimlane-card${card.completed ? " completed" : ""}`}
                                  style={{
                                    borderLeftColor:
                                      IMPORTANCE_COLOR[card.importance] ??
                                      "#6b7280",
                                  }}
                                  onClick={() => onCardClick(card.id)}
                                  title={card.title}
                                >
                                  <span className="swimlane-card-title">
                                    {card.title}
                                  </span>
                                  <span className="swimlane-card-date">
                                    {card.startDate}
                                    {card.endDate &&
                                    card.endDate !== card.startDate
                                      ? ` ~ ${card.endDate}`
                                      : ""}
                                    {card.startTime ? ` ${card.startTime}` : ""}
                                  </span>
                                  {card.bodyMd && (
                                    <span className="swimlane-card-summary">
                                      {card.bodyMd.slice(0, 48)}
                                    </span>
                                  )}
                                </button>
                              ))}
                            </div>
                          ))}
                        </div>
                      </div>
                    );
                  })
                )}
              </section>
            );
          })
        )}
      </div>
    </div>
  );
}
