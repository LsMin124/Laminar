import type { Card, CardRelation, GroupRelation } from "../../lib/dag";
import { BAR_H, edgePath } from "./dagGeometry";
import type { GroupRect } from "./DagGroups";

interface NodeGeom {
  x: number;
  y: number;
  w: number;
}

/**
 * SVG 엣지 레이어 — 그룹 간 화살표(쿨 톤·가는 선, 아래 레이어) + 카드 관계 화살표(웜 오렌지) +
 * 연결 드래그 임시 라인. 모두 from 우측끝 → to 좌측끝 직각 경로(edgePath). 클릭 시 삭제.
 */
export function DagEdges({
  maxX,
  maxY,
  groupRelations,
  groupRects,
  relations,
  cards,
  nodeGeom,
  isVisible,
  linkLine,
  onDeleteGroupRelation,
  onDeleteRelation,
}: {
  maxX: number;
  maxY: number;
  groupRelations: GroupRelation[];
  groupRects: Map<string, GroupRect>;
  relations: CardRelation[];
  cards: Card[];
  nodeGeom: (c: Card) => NodeGeom;
  isVisible: (c: Card) => boolean;
  linkLine: { sx: number; sy: number; x: number; y: number } | null;
  onDeleteGroupRelation: (id: string) => void;
  onDeleteRelation: (id: string) => void;
}) {
  return (
    <svg className="dag-edges" width={maxX} height={maxY} aria-hidden="true">
      <defs>
        <marker
          id="dag-arrow"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
        >
          <path d="M0,0 L10,5 L0,10 z" style={{ fill: "var(--accent-soft)" }} />
        </marker>
        <marker
          id="dag-group-arrow"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="6"
          markerHeight="6"
          orient="auto-start-reverse"
        >
          <path d="M0,0 L10,5 L0,10 z" className="dag-group-arrowhead" />
        </marker>
      </defs>
      {/* 그룹 간 화살표 — 카드 엣지와 색으로 구분(쿨 톤 실선·가는 선, 형태는 카드와 동일한 직각). 카드 엣지 아래 레이어. */}
      {groupRelations.map((rel) => {
        const fr = groupRects.get(rel.fromGroupId);
        const tr = groupRects.get(rel.toGroupId);
        if (!fr || !tr) return null;
        // 카드 엣지와 동일하게 직각(꺾인) 경로 — from 박스 우측끝 → to 박스 좌측끝.
        const d = edgePath(fr.x + fr.w, fr.y + fr.h / 2, tr.x, tr.y + tr.h / 2);
        return (
          <path
            key={rel.id}
            className="dag-group-edge"
            d={d}
            markerEnd="url(#dag-group-arrow)"
            onClick={() => onDeleteGroupRelation(rel.id)}
          />
        );
      })}
      {relations.map((rel) => {
        const from = cards.find((c) => c.id === rel.fromCardId);
        const to = cards.find((c) => c.id === rel.toCardId);
        if (!from || !to || !isVisible(from) || !isVisible(to)) return null;
        const fg = nodeGeom(from);
        const tg = nodeGeom(to);
        const d = edgePath(fg.x + fg.w, fg.y + BAR_H / 2, tg.x, tg.y + BAR_H / 2);
        return (
          <path
            key={rel.id}
            className="dag-edge"
            d={d}
            markerEnd="url(#dag-arrow)"
            onClick={() => onDeleteRelation(rel.id)}
          />
        );
      })}
      {linkLine && (
        <line
          className="dag-link-temp"
          x1={linkLine.sx}
          y1={linkLine.sy}
          x2={linkLine.x}
          y2={linkLine.y}
        />
      )}
    </svg>
  );
}
