import { useMemo } from "react";
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

export function BoardGraph({
  cards,
  groups,
  cardRelations,
  groupRelations,
  onCardClick,
}: BoardGraphProps) {
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

  if (cards.length === 0 && groups.length === 0) {
    return <p className="board-graph-empty">그래프에 표시할 노드가 없습니다.</p>;
  }

  return (
    <div className="board-graph">
      <svg viewBox={`0 0 ${VIEW} ${VIEW}`} className="board-graph-svg">
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
        {Array.from(positions.values()).map((pos) => (
          <g
            key={pos.id}
            className={`node node-${pos.kind}`}
            transform={`translate(${pos.x}, ${pos.y})`}
            onClick={() => pos.kind === "card" && onCardClick?.(pos.id)}
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
        ))}
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
        </li>
      </ul>
    </div>
  );
}
