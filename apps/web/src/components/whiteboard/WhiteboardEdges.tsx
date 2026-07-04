import type { WhiteboardEdge, WhiteboardNode } from "../../lib/whiteboard";
import { anchorToward, type Rect } from "./whiteboardGeometry";

/**
 * 화이트보드 SVG 엣지 레이어 — 노드 사이 직선 화살표(웜 오렌지) + 연결 드래그 임시 라인.
 * 월드 좌표계(변환 컨테이너 안)에 그리며 stroke는 non-scaling으로 줌 무관하게 일정 두께.
 * svg 자체는 pointer-events 없음(노드/배경 조작 비간섭) — 선·라벨만 클릭 가능(CSS).
 */
export function WhiteboardEdges({
  nodes,
  edges,
  rectOf,
  linkLine,
  onDeleteEdge,
  onEditLabel,
}: {
  nodes: WhiteboardNode[];
  edges: WhiteboardEdge[];
  rectOf: (n: WhiteboardNode) => Rect;
  linkLine: { sx: number; sy: number; x: number; y: number } | null;
  onDeleteEdge: (id: string) => void;
  onEditLabel: (edge: WhiteboardEdge) => void;
}) {
  const byId = new Map(nodes.map((n) => [n.id, n]));
  return (
    <svg className="wb-edges" width={1} height={1} aria-hidden="true">
      <defs>
        <marker
          id="wb-arrow"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
        >
          <path d="M0,0 L10,5 L0,10 z" style={{ fill: "var(--accent-soft)" }} />
        </marker>
      </defs>
      {edges.map((e) => {
        const from = byId.get(e.fromNodeId);
        const to = byId.get(e.toNodeId);
        if (!from || !to) return null;
        const fr = rectOf(from);
        const tr = rectOf(to);
        const s = anchorToward(fr, tr.x + tr.w / 2, tr.y + tr.h / 2);
        const t = anchorToward(tr, fr.x + fr.w / 2, fr.y + fr.h / 2);
        const mx = (s.x + t.x) / 2;
        const my = (s.y + t.y) / 2;
        return (
          <g key={e.id}>
            <line
              className="wb-edge"
              x1={s.x}
              y1={s.y}
              x2={t.x}
              y2={t.y}
              markerEnd="url(#wb-arrow)"
              vectorEffect="non-scaling-stroke"
              onClick={() => onDeleteEdge(e.id)}
            />
            <text
              className={`wb-edge-label${e.label ? "" : " empty"}`}
              x={mx}
              y={my}
              onClick={(ev) => {
                ev.stopPropagation();
                onEditLabel(e);
              }}
            >
              {e.label || "＋"}
            </text>
          </g>
        );
      })}
      {linkLine && (
        <line
          className="wb-link-temp"
          x1={linkLine.sx}
          y1={linkLine.sy}
          x2={linkLine.x}
          y2={linkLine.y}
          vectorEffect="non-scaling-stroke"
        />
      )}
    </svg>
  );
}
