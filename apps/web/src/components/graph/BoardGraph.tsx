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
  /** 카드 노드에서 다른 카드로 드래그 시 관계 생성 (P3). 없으면 읽기전용 그래프. */
  onCreateRelation?: (fromCardId: string, toCardId: string) => void;
}

interface NodePos {
  id: string;
  x: number;
  y: number;
  label: string;
  kind: "card" | "group";
  color: string;
}

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

interface DrawState {
  fromId: string;
  sx: number;
  sy: number;
  px: number;
  py: number;
}

export function BoardGraph({
  cards,
  groups,
  cardRelations,
  groupRelations,
  onCardClick,
  onCreateRelation,
}: BoardGraphProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [draw, setDraw] = useState<DrawState | null>(null);

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
      map.set(c.id, {
        id: c.id,
        kind: "card",
        label: c.title,
        color: IMPORTANCE_COLORS[c.importance] ?? "#4b5563",
        x: cx + Math.cos(angle) * RADIUS_CARD,
        y: cy + Math.sin(angle) * RADIUS_CARD,
      });
    });

    return map;
  }, [cards, groups]);

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
          if (!draw) return;
          const p = toSvg(e.clientX, e.clientY);
          setDraw((d) => (d ? { ...d, px: p.x, py: p.y } : d));
        }}
        onPointerUp={(e) => {
          if (!draw) return;
          const p = toSvg(e.clientX, e.clientY);
          const target = cardAt(p.x, p.y, draw.fromId);
          const moved = Math.hypot(p.x - draw.sx, p.y - draw.sy);
          if (target && onCreateRelation) {
            onCreateRelation(draw.fromId, target);
          } else if (moved < CLICK_SLOP) {
            onCardClick?.(draw.fromId);
          }
          setDraw(null);
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
          const from = positions.get(r.fromCardId);
          const to = positions.get(r.toCardId);
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
          const from = positions.get(r.fromGroupId);
          const to = positions.get(r.toGroupId);
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
        {draw && (
          <line
            x1={draw.sx}
            y1={draw.sy}
            x2={draw.px}
            y2={draw.py}
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
          const drawable = pos.kind === "card" && !!onCreateRelation;
          return (
            <g
              key={pos.id}
              className={`node node-${pos.kind}${drawable ? " node-draggable" : ""}`}
              transform={`translate(${pos.x}, ${pos.y})`}
              style={drawable ? { cursor: "crosshair" } : undefined}
              onPointerDown={
                drawable
                  ? (e) => {
                      e.preventDefault();
                      svgRef.current?.setPointerCapture(e.pointerId);
                      setDraw({
                        fromId: pos.id,
                        sx: pos.x,
                        sy: pos.y,
                        px: pos.x,
                        py: pos.y,
                      });
                    }
                  : undefined
              }
              onClick={
                pos.kind === "card" && !onCreateRelation
                  ? () => onCardClick?.(pos.id)
                  : undefined
              }
            >
              <circle
                r={pos.kind === "group" ? 18 : 12}
                fill={pos.color}
                stroke="var(--surface)"
                strokeWidth="2"
              />
              <text
                y={pos.kind === "group" ? -22 : 22}
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
          {onCreateRelation ? " · 카드에서 드래그해 화살표 연결" : ""}
        </li>
      </ul>
    </div>
  );
}
