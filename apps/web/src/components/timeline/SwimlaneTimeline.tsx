import { useLayoutEffect, useMemo, useRef, useState } from "react";
import { addDays, differenceInCalendarDays, format, isToday, parseISO } from "date-fns";
import type {
  CardRelationResponse,
  CardResponse,
  GroupResponse,
  TabResponse,
} from "../../lib/types";
import "./SwimlaneTimeline.css";

const COL_W = 170;
const LANE_H = 66;
const GAP = 5;
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

// 태스크 사슬(연결 SEQUENCE 묶음)별 색 — 한 사슬을 시각적으로 추적.
const CHAIN_PALETTE = [
  "#22d3ee",
  "#a78bfa",
  "#34d399",
  "#f59e0b",
  "#f472b6",
  "#60a5fa",
  "#fb7185",
  "#4ade80",
];

interface Props {
  anchor: Date;
  tabs: TabResponse[];
  groups: GroupResponse[];
  tabGroups: Record<string, string[]>;
  groupMembers: Record<string, string[]>;
  cards: CardResponse[];
  cardRelations: CardRelationResponse[];
  onCardClick: (cardId: string) => void;
  onCreateCard: (groupId: string, dateIso: string) => void;
  onAddNextStep: (groupId: string, fromCard: CardResponse) => void;
  onConnect: (fromCardId: string, toCardId: string) => void;
}

interface Placed {
  card: CardResponse;
  startIdx: number;
  endIdx: number;
  lane: number;
}

interface Arrow {
  id: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  label: string;
  kind: string;
  chainColor?: string;
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
 * 멤버 카드를 기간 막대로 배치 + 레인 패킹(겹침 회피). 범위 밖은 제외, 경계는 클램프.
 */
function placeCards(
  cards: CardResponse[],
  anchor: Date,
  dayCount: number,
): { items: Placed[]; laneCount: number } {
  const spans: Omit<Placed, "lane">[] = [];
  for (const card of cards) {
    if (!card.startDate) continue;
    const ds = differenceInCalendarDays(parseISO(card.startDate), anchor);
    const de = card.endDate
      ? differenceInCalendarDays(parseISO(card.endDate), anchor)
      : ds;
    if (de < 0 || ds > dayCount - 1) continue; // 범위 밖
    spans.push({
      card,
      startIdx: Math.max(0, ds),
      endIdx: Math.min(dayCount - 1, Math.max(ds, de)),
    });
  }
  spans.sort((a, b) => a.startIdx - b.startIdx || a.endIdx - b.endIdx);
  const laneEnds: number[] = [];
  const items: Placed[] = [];
  for (const s of spans) {
    let lane = laneEnds.findIndex((end) => end < s.startIdx);
    if (lane === -1) {
      lane = laneEnds.length;
      laneEnds.push(s.endIdx);
    } else {
      laneEnds[lane] = s.endIdx;
    }
    items.push({ ...s, lane });
  }
  return { items, laneCount: laneEnds.length };
}

/**
 * 스윔레인 타임라인 (메인 오버뷰) — 가로축 = 연속 날짜(무한 스크롤), 세로축 = 탭 섹션.
 * 섹션 안 그룹(점선 밴드)별로 카드를 기간 막대로 배치(멀티데이=하나의 사각형, 레인 패킹).
 * 카드 간 관계는 SVG 화살표 오버레이(순차=실선 사슬색).
 */
export function SwimlaneTimeline({
  anchor,
  tabs,
  groups,
  tabGroups,
  groupMembers,
  cards,
  cardRelations,
  onCardClick,
  onCreateCard,
  onAddNextStep,
  onConnect,
}: Props) {
  const [dayCount, setDayCount] = useState(60);
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
  // SEQUENCE 관계의 연결요소(=태스크 사슬)마다 색 1개. 멤버 2개 이상만 색 부여.
  const chainColorByCard = useMemo(() => {
    const parent = new Map<string, string>();
    const find = (x: string): string => {
      let r = x;
      while (parent.get(r) !== r) r = parent.get(r)!;
      return r;
    };
    const union = (a: string, b: string) => {
      if (!parent.has(a)) parent.set(a, a);
      if (!parent.has(b)) parent.set(b, b);
      const ra = find(a);
      const rb = find(b);
      if (ra !== rb) parent.set(ra, rb);
    };
    for (const r of cardRelations) {
      if (r.relationKind === "SEQUENCE") union(r.fromCardId, r.toCardId);
    }
    const members = new Map<string, string[]>();
    for (const id of parent.keys()) {
      const root = find(id);
      if (!members.has(root)) members.set(root, []);
      members.get(root)!.push(id);
    }
    const roots = [...members.keys()]
      .filter((r) => members.get(r)!.length >= 2)
      .sort();
    const color = new Map<string, string>();
    roots.forEach((root, i) => {
      const c = CHAIN_PALETTE[i % CHAIN_PALETTE.length];
      members.get(root)!.forEach((id) => color.set(id, c));
    });
    return color;
  }, [cardRelations]);
  const groupsById = useMemo(
    () => new Map(groups.map((g) => [g.id, g])),
    [groups],
  );
  const sections = useMemo(
    () => tabs.filter((t) => t.visible).sort((a, b) => a.priority - b.priority),
    [tabs],
  );

  const scrollRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const cardRefs = useRef(new Map<string, HTMLElement>());
  const didInitScroll = useRef(false);
  const [arrows, setArrows] = useState<Arrow[]>([]);
  const [dims, setDims] = useState({ w: 0, h: 0 });
  // 연결 모드 — 카드 A의 "연결 ⇢" 후 다른 카드 B 클릭 시 A→B 순차(SEQUENCE) 화살표.
  const [linkSource, setLinkSource] = useState<string | null>(null);

  const todayIdx = differenceInCalendarDays(new Date(), anchor);

  // 마운트 시 오늘 열로 스크롤.
  useLayoutEffect(() => {
    if (didInitScroll.current) return;
    const el = scrollRef.current;
    if (!el) return;
    if (todayIdx >= 0) {
      el.scrollLeft = Math.max(0, todayIdx * COL_W - COL_W * 2);
    }
    didInitScroll.current = true;
  }, [todayIdx]);

  // 오른쪽 끝 근처 스크롤 시 날짜 범위 확장(연속/무한 스크롤).
  function handleScroll() {
    const el = scrollRef.current;
    if (!el) return;
    if (el.scrollLeft + el.clientWidth >= el.scrollWidth - 800) {
      setDayCount((d) => d + 30);
    }
  }

  function activateCard(cardId: string) {
    if (linkSource && linkSource !== cardId) {
      onConnect(linkSource, cardId);
      setLinkSource(null);
    } else if (linkSource === cardId) {
      setLinkSource(null);
    } else {
      onCardClick(cardId);
    }
  }

  function trackCreate(e: React.MouseEvent<HTMLDivElement>, gid: string) {
    if (e.target !== e.currentTarget) return; // 막대가 아닌 빈 트랙에서만
    const rect = e.currentTarget.getBoundingClientRect();
    const idx = Math.max(
      0,
      Math.min(dayCount - 1, Math.floor((e.clientX - rect.left) / COL_W)),
    );
    onCreateCard(gid, format(addDays(anchor, idx), "yyyy-MM-dd"));
  }

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
        const isSeq = rel.relationKind === "SEQUENCE";
        const label = isSeq ? "" : rel.summary?.trim() || rel.relationKind;
        next.push({
          id: rel.id,
          x1,
          y1,
          x2,
          y2,
          label,
          kind: rel.relationKind,
          chainColor: isSeq ? chainColorByCard.get(rel.fromCardId) : undefined,
        });
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
  }, [
    cardRelations,
    chainColorByCard,
    dayCount,
    anchor,
    sections,
    tabGroups,
    groupMembers,
    cards,
  ]);

  function registerCard(id: string) {
    return (el: HTMLElement | null) => {
      if (el) cardRefs.current.set(id, el);
      else cardRefs.current.delete(id);
    };
  }

  const rowWidth = dayCount * COL_W;
  const headerStyle = {
    gridTemplateColumns: `repeat(${dayCount}, ${COL_W}px)`,
    width: `${rowWidth}px`,
  } as React.CSSProperties;
  const trackBg: React.CSSProperties = {
    width: `${rowWidth}px`,
    backgroundImage:
      "linear-gradient(to right, rgba(110,160,210,0.06) 0 1px, transparent 1px)",
    backgroundSize: `${COL_W}px 100%`,
  };

  return (
    <div className={`swimlane${linkSource ? " linking" : ""}`}>
      {linkSource && (
        <div className="swimlane-link-banner">
          <span>
            <strong>{cardsById.get(linkSource)?.title ?? "카드"}</strong> 다음으로
            이을 카드를 클릭하세요 (같은 날/다른 날 무관)
          </span>
          <button type="button" onClick={() => setLinkSource(null)}>
            취소
          </button>
        </div>
      )}
      <div className="swimlane-scroll" ref={scrollRef} onScroll={handleScroll}>
        <div className="swimlane-content" ref={contentRef}>
          {todayIdx >= 0 && todayIdx < dayCount && (
            <div
              className="swimlane-today-line"
              style={{ left: `${todayIdx * COL_W}px`, width: `${COL_W}px` }}
              aria-hidden="true"
            />
          )}
          <svg
            className="swimlane-arrows"
            width={dims.w}
            height={dims.h}
            aria-hidden="true"
          >
            <defs>
              <marker
                id="swimlane-arrowhead-seq"
                viewBox="0 0 10 10"
                refX="9"
                refY="5"
                markerWidth="7"
                markerHeight="7"
                orient="auto-start-reverse"
              >
                <path d="M0,0 L10,5 L0,10 z" fill="context-stroke" />
              </marker>
              <marker
                id="swimlane-arrowhead-rel"
                viewBox="0 0 10 10"
                refX="9"
                refY="5"
                markerWidth="6"
                markerHeight="6"
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
                    className={`swimlane-arrow-line ${a.kind === "SEQUENCE" ? "seq" : "rel"}`}
                    style={a.chainColor ? { stroke: a.chainColor } : undefined}
                    markerEnd={`url(#swimlane-arrowhead-${a.kind === "SEQUENCE" ? "seq" : "rel"})`}
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

          <div className="swimlane-header" style={headerStyle}>
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
                      그룹 없음 — 좌측에서 이 탭에 그룹을 추가하세요.
                    </p>
                  ) : (
                    groupIds.map((gid) => {
                      const group = groupsById.get(gid);
                      const memberCards = (groupMembers[gid] ?? [])
                        .map((cid) => cardsById.get(cid))
                        .filter((c): c is CardResponse => Boolean(c));
                      const total = memberCards.length;
                      const done = memberCards.filter((c) => c.completed).length;
                      const pct = total > 0 ? Math.round((done / total) * 100) : 0;
                      const { items, laneCount } = placeCards(
                        memberCards,
                        anchor,
                        dayCount,
                      );
                      const bandHeight = Math.max(1, laneCount) * LANE_H + GAP;
                      return (
                        <div key={gid} className="swimlane-group">
                          <div className="swimlane-group-head">
                            <span
                              className="swimlane-group-label"
                              style={
                                group?.color
                                  ? {
                                      borderColor: group.color,
                                      color: group.color,
                                    }
                                  : undefined
                              }
                            >
                              {group?.name ?? "그룹"}
                            </span>
                            {total > 0 && (
                              <span
                                className="swimlane-group-progress"
                                title={`${done}/${total} 완료 (${pct}%)`}
                              >
                                <span className="swimlane-group-progress-bar">
                                  <span style={{ width: `${pct}%` }} />
                                </span>
                                <span className="swimlane-group-progress-num">
                                  {done}/{total}
                                </span>
                              </span>
                            )}
                          </div>
                          <div
                            className="swimlane-track"
                            style={{ ...trackBg, height: `${bandHeight}px` }}
                            onDoubleClick={(e) => trackCreate(e, gid)}
                            title="빈 곳 더블클릭 → 그 날짜에 카드 추가"
                          >
                            {items.map(({ card, startIdx, endIdx, lane }) => {
                              const relCount = relCountById.get(card.id) ?? 0;
                              const chainColor = chainColorByCard.get(card.id);
                              const isGcal = card.origin === "GCAL_PULL";
                              const multi = endIdx > startIdx;
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
                                  className={`swimlane-bar${card.completed ? " completed" : ""}${linkSource === card.id ? " linking-source" : ""}`}
                                  style={{
                                    left: `${startIdx * COL_W + GAP}px`,
                                    top: `${lane * LANE_H + GAP}px`,
                                    width: `${(endIdx - startIdx + 1) * COL_W - 2 * GAP}px`,
                                    height: `${LANE_H - 2 * GAP}px`,
                                    borderLeftColor:
                                      IMPORTANCE_COLOR[card.importance] ?? "#6b7280",
                                  }}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    activateCard(card.id);
                                  }}
                                  onKeyDown={(e) => {
                                    if (e.key === "Enter" || e.key === " ") {
                                      e.preventDefault();
                                      activateCard(card.id);
                                    }
                                  }}
                                  title={card.title}
                                >
                                  <span className="swimlane-card-title">
                                    {chainColor && (
                                      <span
                                        className="swimlane-card-chain"
                                        style={{ background: chainColor }}
                                        title="태스크 사슬"
                                      />
                                    )}
                                    {card.title}
                                  </span>
                                  <span className="swimlane-card-date">
                                    {card.startDate}
                                    {multi && card.endDate
                                      ? ` ~ ${card.endDate}`
                                      : ""}
                                    {card.startTime ? ` ${card.startTime}` : ""}
                                  </span>
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
                                      {card.rrule && <span title="반복">⟳</span>}
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
                                  <span className="swimlane-card-actions">
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
                                      다음 →
                                    </button>
                                    <button
                                      type="button"
                                      className="swimlane-card-link"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        setLinkSource(card.id);
                                      }}
                                      title="이 카드를 다른 카드와 순차 연결 (같은 날/다른 날 무관)"
                                      aria-label="순차 연결 시작"
                                    >
                                      연결 ⇢
                                    </button>
                                  </span>
                                </div>
                              );
                            })}
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
