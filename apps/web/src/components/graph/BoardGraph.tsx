import { useMemo, useRef, useState } from "react";
import type {
  CardResponse,
  CardRelationResponse,
  GroupResponse,
  GroupRelationResponse,
} from "../../lib/types";
import "./BoardGraph.css";

interface BoardGraphProps {
  cards: CardResponse[];
  groups: GroupResponse[];
  cardRelations: CardRelationResponse[];
  groupRelations: GroupRelationResponse[];
  onCardClick?: (cardId: string) => void;
  /** 카드 연결 핸들에서 다른 카드로 드래그 시 관계 생성 (P3a). */
  onCreateRelation?: (fromCardId: string, toCardId: string) => void;
  /** 카드 노드 본체 드래그 배치 시 좌표 저장 (P3c, attrs.canvasX/Y). */
  onMoveCard?: (cardId: string, x: number, y: number) => void;
  /** P4b 탭 스코프 — 이 집합에 없는 카드는 흐리게(범위 밖). null이면 전체. */
  scopedCardIds?: Set<string> | null;
}

interface NodePos {
  id: string;
  x: number;
  y: number;
  label: string;
  kind: "card" | "group";
  color: string;
}

type Drag =
  | { kind: "move"; id: string; offX: number; offY: number; sx: number; sy: number; x: number; y: number }
  | { kind: "connect"; fromId: string; sx: number; sy: number; px: number; py: number };

const IMPORTANCE_COLORS: Record<string, string> = {
  NORMAL: "#4b5563",
  CF: "#6366f1",
  URGENT: "#ef4444",
  PURCHASE: "#f59e0b",
  PERPETUAL_VER: "#10b981",
  ARTICLE: "#8b5cf6",
  PROCESS: "#06b6d4",
};

const RADIUS_CARD = 220;
const RADIUS_GROUP = 90;
const VIEW = 560;
const HIT_RADIUS = 18;
const CLICK_SLOP = 6;

function num(v: unknown): number | null {
  return typeof v === "number" && Number.isFinite(v) ? v : null;
}

export function BoardGraph({
  cards,
  groups,
  cardRelations,
  groupRelations,
  onCardClick,
  onCreateRelation,
  onMoveCard,
  scopedCardIds,
}: BoardGraphProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [drag, setDrag] = useState<Drag | null>(null);
  // 세션 내 드래그 배치 오버라이드 — PATCH→refetch 지연 중 스냅백 방지(attrs와 일관).
  const [placed, setPlaced] = useState<Map<string, { x: number; y: number }>>(
    new Map(),
  );

  const positions = useMemo(() => {
    const map = new Map<string, NodePos>();
    const cx = VIEW / 2;
    const cy = VIEW / 2;

    groups.forEach((g, i) => {
      const angle = (i / Math.max(groups.length, 1)) * Math.PI * 2 - Math.PI / 2;
      map.set(g.id, {
        id: g.id,
        kind: "group",
        label: g.name,
        color: g.color ?? "#9ca3af",
        x: cx + Math.cos(angle) * RADIUS_GROUP,
        y: cy + Math.sin(angle) * RADIUS_GROUP,
      });
    });

    cards.forEach((c, i) => {
      const angle = (i / Math.max(cards.length, 1)) * Math.PI * 2 - Math.PI / 2;
      const p = placed.get(c.id);
      const ax = num(c.attrs?.canvasX);
      const ay = num(c.attrs?.canvasY);
      map.set(c.id, {
        id: c.id,
        kind: "card",
        label: c.title,
        color: IMPORTANCE_COLORS[c.importance] ?? "#4b5563",
        x: p?.x ?? ax ?? cx + Math.cos(angle) * RADIUS_CARD,
        y: p?.y ?? ay ?? cy + Math.sin(angle) * RADIUS_CARD,
      });
    });

    return map;
  }, [cards, groups, placed]);

  function toSvg(clientX: number, clientY: number): { x: number; y: number } {
    const svg = svgRef.current;
    const matrix = svg?.getScreenCTM();
    if (!svg || !matrix) return { x: 0, y: 0 };
    const pt = svg.createSVGPoint();
    pt.x = clientX;
    pt.y = clientY;
    const p = pt.matrixTransform(matrix.inverse());
    return { x: p.x, y: p.y };
  }

  /** 드래그 중인 노드는 라이브 좌표, 그 외엔 레이아웃 좌표. 간선도 이걸로 따라옴. */
  function display(id: string): NodePos | undefined {
    const base = positions.get(id);
    if (base && drag?.kind === "move" && drag.id === id) {
      return { ...base, x: drag.x, y: drag.y };
    }
    return base;
  }

  function cardAt(x: number, y: number, exclude: string): string | null {
    for (const pos of positions.values()) {
      if (
        pos.kind === "card" &&
        pos.id !== exclude &&
        Math.hypot(pos.x - x, pos.y - y) < HIT_RADIUS
      ) {
        return pos.id;
      }
    }
    return null;
  }

  if (cards.length === 0 && groups.length === 0) {
    return <p className="board-graph-empty">그래프에 표시할 노드가 없습니다.</p>;
  }

  return (
    <div className="board-graph">
      <svg
        ref={svgRef}
        viewBox={`0 0 ${VIEW} ${VIEW}`}
        className="board-graph-svg"
        onPointerMove={(e) => {
          if (!drag) return;
          const p = toSvg(e.clientX, e.clientY);
          if (drag.kind === "move") {
            setDrag({ ...drag, x: p.x - drag.offX, y: p.y - drag.offY });
          } else {
            setDrag({ ...drag, px: p.x, py: p.y });
          }
        }}
        onPointerUp={(e) => {
          if (!drag) return;
          const p = toSvg(e.clientX, e.clientY);
          if (drag.kind === "move") {
            const moved = Math.hypot(drag.x - drag.sx, drag.y - drag.sy);
            if (moved >= CLICK_SLOP) {
              const x = drag.x;
              const y = drag.y;
              setPlaced((m) => new Map(m).set(drag.id, { x, y }));
              onMoveCard?.(drag.id, x, y);
            } else {
              onCardClick?.(drag.id);
            }
          } else {
            const target = cardAt(p.x, p.y, drag.fromId);
            const moved = Math.hypot(p.x - drag.sx, p.y - drag.sy);
            if (target && onCreateRelation) {
              onCreateRelation(drag.fromId, target);
            } else if (moved < CLICK_SLOP) {
              onCardClick?.(drag.fromId);
            }
          }
          setDrag(null);
        }}
      >
        <defs>
          <marker
            id="arrow"
            viewBox="0 0 10 10"
            refX="10"
            refY="5"
            markerWidth="5"
            markerHeight="5"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#6b7280" />
          </marker>
        </defs>
        {cardRelations.map((r) => {
          const from = display(r.fromCardId);
          const to = display(r.toCardId);
          if (!from || !to) return null;
          return (
            <line
              key={r.id}
              x1={from.x}
              y1={from.y}
              x2={to.x}
              y2={to.y}
              className="edge edge-card"
              markerEnd="url(#arrow)"
            />
          );
        })}
        {groupRelations.map((r) => {
          const from = display(r.fromGroupId);
          const to = display(r.toGroupId);
          if (!from || !to) return null;
          return (
            <line
              key={r.id}
              x1={from.x}
              y1={from.y}
              x2={to.x}
              y2={to.y}
              className="edge edge-group"
              markerEnd="url(#arrow)"
            />
          );
        })}
        {drag?.kind === "connect" && (
          <line
            x1={drag.sx}
            y1={drag.sy}
            x2={drag.px}
            y2={drag.py}
            className="edge edge-drawing"
            markerEnd="url(#arrow)"
            style={{
              stroke: "var(--accent, #6366f1)",
              strokeWidth: 2,
              strokeDasharray: "5 3",
              opacity: 0.85,
            }}
          />
        )}
        {Array.from(positions.values()).map((pos) => {
          const d = display(pos.id) ?? pos;
          const isCard = pos.kind === "card";
          const r = isCard ? 12 : 18;
          const dimmed =
            !!scopedCardIds && isCard && !scopedCardIds.has(pos.id);
          return (
            <g
              key={pos.id}
              className={`node node-${pos.kind}`}
              transform={`translate(${d.x}, ${d.y})`}
              style={dimmed ? { opacity: 0.18 } : undefined}
            >
              <circle
                r={r}
                fill={pos.color}
                stroke="var(--surface)"
                strokeWidth="2"
                style={{ cursor: isCard && onMoveCard ? "grab" : "pointer" }}
                onPointerDown={
                  isCard && onMoveCard
                    ? (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        svgRef.current?.setPointerCapture(e.pointerId);
                        const p = toSvg(e.clientX, e.clientY);
                        setDrag({
                          kind: "move",
                          id: pos.id,
                          offX: p.x - d.x,
                          offY: p.y - d.y,
                          sx: d.x,
                          sy: d.y,
                          x: d.x,
                          y: d.y,
                        });
                      }
                    : undefined
                }
                onClick={
                  isCard && !onMoveCard
                    ? () => onCardClick?.(pos.id)
                    : undefined
                }
              />
              {isCard && onCreateRelation && (
                <circle
                  className="connect-handle"
                  cx={r + 4}
                  cy={-(r + 4)}
                  r={5}
                  fill="var(--surface)"
                  stroke="var(--accent, #6366f1)"
                  strokeWidth="2"
                  style={{ cursor: "crosshair" }}
                  onPointerDown={(e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    svgRef.current?.setPointerCapture(e.pointerId);
                    setDrag({
                      kind: "connect",
                      fromId: pos.id,
                      sx: d.x,
                      sy: d.y,
                      px: d.x,
                      py: d.y,
                    });
                  }}
                >
                  <title>드래그해 다른 카드와 화살표 연결</title>
                </circle>
              )}
              <text
                y={pos.kind === "group" ? -26 : 24}
                textAnchor="middle"
                className="node-label"
              >
                {pos.label.length > 16
                  ? `${pos.label.slice(0, 16)}...`
                  : pos.label}
              </text>
            </g>
          );
        })}
      </svg>
      <ul className="board-graph-legend">
        <li>
          <span className="dot dot-group" /> 그룹 ({groups.length})
        </li>
        <li>
          <span className="dot dot-card" /> 카드 ({cards.length})
        </li>
        <li className="board-graph-legend-spacer">
          관계: 카드 {cardRelations.length} · 그룹 {groupRelations.length}
          {onMoveCard ? " · 카드 드래그=이동, 모서리 점 드래그=화살표" : ""}
        </li>
      </ul>
    </div>
  );
}
