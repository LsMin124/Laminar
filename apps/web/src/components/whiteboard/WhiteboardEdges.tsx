import type { WhiteboardEdge, WhiteboardNode } from "../../lib/whiteboard";
import { edgeCurve, type Rect } from "./whiteboardGeometry";

/**
 * 화이트보드 SVG 엣지 레이어 — 4방향 변 중점 앵커에서 진출하는 3차 베지어 곡선(WB-D) + 연결
 * 드래그 임시 라인. 월드 좌표계(변환 컨테이너 안)에 그리며 stroke는 non-scaling으로 줌 무관 두께.
 * svg 자체는 pointer-events 없음(노드/배경 조작 비간섭) — 선·라벨·엔드포인트 핸들만 클릭 가능(CSS).
 * 엣지에 호버하면 양 끝 핸들이 나타나고, 핸들 드래그로 다른 노드에 재연결한다.
 */
export function WhiteboardEdges({
  nodes,
  edges,
  rectOf,
  linkLine,
  onDeleteEdge,
  onEditLabel,
  onEndpointDown,
}: {
  nodes: WhiteboardNode[];
  edges: WhiteboardEdge[];
  rectOf: (n: WhiteboardNode) => Rect;
  linkLine: { sx: number; sy: number; x: number; y: number } | null;
  onDeleteEdge: (id: string) => void;
  onEditLabel: (edge: WhiteboardEdge) => void;
  onEndpointDown: (
    e: React.PointerEvent<SVGCircleElement>,
    edge: WhiteboardEdge,
    end: "from" | "to",
  ) => void;
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
        const curve = edgeCurve(rectOf(from), rectOf(to));
        return (
          <g key={e.id} className="wb-edge-g">
            <path
              className="wb-edge"
              d={curve.d}
              markerEnd="url(#wb-arrow)"
              vectorEffect="non-scaling-stroke"
              onClick={() => onDeleteEdge(e.id)}
            />
            <text
              className={`wb-edge-label${e.label ? "" : " empty"}`}
              x={curve.label.x}
              y={curve.label.y}
              onClick={(ev) => {
                ev.stopPropagation();
                onEditLabel(e);
              }}
            >
              {e.label || "＋"}
            </text>
            <circle
              className="wb-edge-endpoint"
              cx={curve.a.x}
              cy={curve.a.y}
              r={6}
              vectorEffect="non-scaling-stroke"
              onPointerDown={(ev) => onEndpointDown(ev, e, "from")}
            />
            <circle
              className="wb-edge-endpoint"
              cx={curve.b.x}
              cy={curve.b.y}
              r={6}
              vectorEffect="non-scaling-stroke"
              onPointerDown={(ev) => onEndpointDown(ev, e, "to")}
            />
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
