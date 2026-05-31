import { useLayoutEffect, useMemo, useRef, useState } from "react";
import { addDays, format, isSameDay, isToday, parseISO } from "date-fns";
import type {
  CardRelationResponse,
  CardResponse,
  GroupResponse,
  TabResponse,
} from "../../lib/types";
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
  cardRelations: CardRelationResponse[];
  onCardClick: (cardId: string) => void;
  onCreateCard: (groupId: string, dateIso: string) => void;
  onAddNextStep: (groupId: string, fromCard: CardResponse) => void;
}

interface Arrow {
  id: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  label: string;
}

/** 사각형 중심에서 (tx,ty) 방향으로 테두리와 만나는 점. */
function edgePoint(
  cx: number,
  cy: number,
  hw: number,
  hh: number,
  tx: number,
  ty: number,
): [number, number] {
  const dx = tx - cx;
  const dy = ty - cy;
  if (dx === 0 && dy === 0) return [cx, cy];
  const sx = dx !== 0 ? hw / Math.abs(dx) : Infinity;
  const sy = dy !== 0 ? hh / Math.abs(dy) : Infinity;
  const s = Math.min(sx, sy);
  return [cx + dx * s, cy + dy * s];
}

/**
 * 스윔레인 타임라인 (재정렬 — 원본 Laminar 뷰) — 가로축 = 날짜 열, 세로축 = 탭 섹션 행.
 * 각 탭 섹션 안에 그룹(점선 밴드)별로 카드를 날짜 위치에 배치, 카드 간 관계는 SVG 화살표로 오버레이.
 * (멀티데이 스팬·인라인 생성은 후속.)
 */
export function SwimlaneTimeline({
  anchor,
  dayCount,
  tabs,
  groups,
  tabGroups,
  groupMembers,
  cards,
  cardRelations,
  onCardClick,
  onCreateCard,
  onAddNextStep,
}: Props) {
  const days = useMemo(
    () => Array.from({ length: dayCount }, (_, i) => addDays(anchor, i)),
    [anchor, dayCount],
  );
  const cardsById = useMemo(() => new Map(cards.map((c) => [c.id, c])), [cards]);
  const relCountById = useMemo(() => {
    const m = new Map<string, number>();
    for (const r of cardRelations) {
      m.set(r.fromCardId, (m.get(r.fromCardId) ?? 0) + 1);
      m.set(r.toCardId, (m.get(r.toCardId) ?? 0) + 1);
    }
    return m;
  }, [cardRelations]);
  const groupsById = useMemo(
    () => new Map(groups.map((g) => [g.id, g])),
    [groups],
  );
  const sections = useMemo(
    () => tabs.filter((t) => t.visible).sort((a, b) => a.priority - b.priority),
    [tabs],
  );

  const contentRef = useRef<HTMLDivElement>(null);
  const cardRefs = useRef(new Map<string, HTMLElement>());
  const [arrows, setArrows] = useState<Arrow[]>([]);
  const [dims, setDims] = useState({ w: 0, h: 0 });

  // 렌더된 카드 DOM을 측정해 보이는 카드끼리만 화살표 좌표 계산.
  useLayoutEffect(() => {
    const content = contentRef.current;
    if (!content) return;

    function measure() {
      const root = contentRef.current;
      if (!root) return;
      const base = root.getBoundingClientRect();
      setDims({ w: root.scrollWidth, h: root.scrollHeight });
      const next: Arrow[] = [];
      for (const rel of cardRelations) {
        const from = cardRefs.current.get(rel.fromCardId);
        const to = cardRefs.current.get(rel.toCardId);
        if (!from || !to) continue; // 둘 다 화면에 있을 때만
        const fr = from.getBoundingClientRect();
        const tr = to.getBoundingClientRect();
        const fcx = fr.left - base.left + fr.width / 2;
        const fcy = fr.top - base.top + fr.height / 2;
        const tcx = tr.left - base.left + tr.width / 2;
        const tcy = tr.top - base.top + tr.height / 2;
        const [x1, y1] = edgePoint(fcx, fcy, fr.width / 2, fr.height / 2, tcx, tcy);
        const [x2, y2] = edgePoint(tcx, tcy, tr.width / 2, tr.height / 2, fcx, fcy);
        const label = rel.summary?.trim() || rel.relationKind;
        next.push({ id: rel.id, x1, y1, x2, y2, label });
      }
      setArrows(next);
    }

    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(content);
    window.addEventListener("resize", measure);
    return () => {
      ro.disconnect();
      window.removeEventListener("resize", measure);
    };
  }, [cardRelations, days, sections, tabGroups, groupMembers, cards]);

  function dayIndexOf(iso: string | null): number {
    if (!iso) return -1;
    const d = parseISO(iso);
    return days.findIndex((day) => isSameDay(day, d));
  }

  const gridStyle = {
    gridTemplateColumns: `repeat(${dayCount}, ${COL_W}px)`,
    width: `${dayCount * COL_W}px`,
  } as React.CSSProperties;

  function registerCard(id: string) {
    return (el: HTMLElement | null) => {
      if (el) cardRefs.current.set(id, el);
      else cardRefs.current.delete(id);
    };
  }

  return (
    <div className="swimlane">
      <div className="swimlane-scroll">
        <div className="swimlane-content" ref={contentRef}>
          <svg
            className="swimlane-arrows"
            width={dims.w}
            height={dims.h}
            aria-hidden="true"
          >
            <defs>
              <marker
                id="swimlane-arrowhead"
                viewBox="0 0 10 10"
                refX="9"
                refY="5"
                markerWidth="7"
                markerHeight="7"
                orient="auto-start-reverse"
              >
                <path d="M0,0 L10,5 L0,10 z" fill="var(--text-dim)" />
              </marker>
            </defs>
            {arrows.map((a) => {
              const mx = (a.x1 + a.x2) / 2;
              const my = (a.y1 + a.y2) / 2;
              return (
                <g key={a.id}>
                  <line
                    x1={a.x1}
                    y1={a.y1}
                    x2={a.x2}
                    y2={a.y2}
                    className="swimlane-arrow-line"
                    markerEnd="url(#swimlane-arrowhead)"
                  />
                  {a.label && (
                    <text
                      x={mx}
                      y={my}
                      className="swimlane-arrow-label"
                      textAnchor="middle"
                      dominantBaseline="middle"
                    >
                      {a.label.length > 14 ? `${a.label.slice(0, 13)}…` : a.label}
                    </text>
                  )}
                </g>
              );
            })}
          </svg>

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
                                {(byDay.get(i) ?? []).map((card) => {
                                  const relCount = relCountById.get(card.id) ?? 0;
                                  const isGcal = card.origin === "GCAL_PULL";
                                  const hasMeta =
                                    relCount > 0 ||
                                    Boolean(card.rrule) ||
                                    Boolean(card.linkedPerpetualId) ||
                                    isGcal ||
                                    card.completed;
                                  return (
                                    <div
                                      key={card.id}
                                      ref={registerCard(card.id)}
                                      role="button"
                                      tabIndex={0}
                                      className={`swimlane-card${card.completed ? " completed" : ""}`}
                                      style={{
                                        borderLeftColor:
                                          IMPORTANCE_COLOR[card.importance] ??
                                          "#6b7280",
                                      }}
                                      onClick={() => onCardClick(card.id)}
                                      onKeyDown={(e) => {
                                        if (e.key === "Enter" || e.key === " ") {
                                          e.preventDefault();
                                          onCardClick(card.id);
                                        }
                                      }}
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
                                        {card.startTime
                                          ? ` ${card.startTime}`
                                          : ""}
                                      </span>
                                      {card.bodyMd && (
                                        <span className="swimlane-card-summary">
                                          {card.bodyMd.slice(0, 48)}
                                        </span>
                                      )}
                                      {hasMeta && (
                                        <span className="swimlane-card-meta">
                                          {relCount > 0 && (
                                            <span
                                              className="swimlane-card-badge"
                                              title="관계"
                                            >
                                              ↔{relCount}
                                            </span>
                                          )}
                                          {card.rrule && (
                                            <span title="반복">⟳</span>
                                          )}
                                          {card.linkedPerpetualId && (
                                            <span
                                              className="swimlane-card-perp"
                                              title="영구노트 연결"
                                            >
                                              ◆
                                            </span>
                                          )}
                                          {isGcal && (
                                            <span
                                              className="swimlane-card-gcal"
                                              title="Google 캘린더"
                                            >
                                              G
                                            </span>
                                          )}
                                          {card.completed && (
                                            <span
                                              className="swimlane-card-done"
                                              title="완료"
                                            >
                                              ✓
                                            </span>
                                          )}
                                        </span>
                                      )}
                                      <button
                                        type="button"
                                        className="swimlane-card-next"
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          onAddNextStep(gid, card);
                                        }}
                                        title="다음 단계 카드 (다음날·같은 그룹·순차 연결)"
                                        aria-label="다음 단계 카드 추가"
                                      >
                                        다음 단계 →
                                      </button>
                                    </div>
                                  );
                                })}
                                <button
                                  type="button"
                                  className="swimlane-cell-add"
                                  onClick={() =>
                                    onCreateCard(
                                      gid,
                                      format(days[i], "yyyy-MM-dd"),
                                    )
                                  }
                                  title="이 날짜에 카드 추가"
                                  aria-label="카드 추가"
                                >
                                  +
                                </button>
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
    </div>
  );
}
