import { useRef, useState } from "react";
import {
  useCreateWhiteboardEdge,
  useCreateWhiteboardNode,
  useDeleteWhiteboardEdge,
  useDeleteWhiteboardNode,
  useUpdateWhiteboardNode,
  useWhiteboard,
} from "../../lib/queries";
import type { WhiteboardNodeResponse } from "../../lib/types";
import { useDialogs } from "../ui/DialogProvider";
import "./WhiteboardCanvas.css";

interface DragState {
  id: string;
  x: number;
  y: number;
  offsetX: number;
  offsetY: number;
  moved: boolean;
}

/**
 * 독립 화이트보드 — 타임라인/캘린더와 무관한 자유 캔버스 (자체 node/edge 엔티티).
 * 빈 곳 더블클릭 → 노드 생성, 노드 드래그 → 이동(저장), 클릭 → 본문 편집, "⇢" → 연결 시작.
 */
export function WhiteboardCanvas({ boardId }: { boardId: string }) {
  const wb = useWhiteboard(boardId);
  const createNode = useCreateWhiteboardNode(boardId);
  const updateNode = useUpdateWhiteboardNode(boardId);
  const deleteNode = useDeleteWhiteboardNode(boardId);
  const createEdge = useCreateWhiteboardEdge(boardId);
  const deleteEdge = useDeleteWhiteboardEdge(boardId);
  const dialogs = useDialogs();

  const canvasRef = useRef<HTMLDivElement>(null);
  const [drag, setDrag] = useState<DragState | null>(null);
  const [linkSource, setLinkSource] = useState<string | null>(null);

  const nodes = wb.data?.nodes ?? [];
  const edges = wb.data?.edges ?? [];
  const nodeById = new Map(nodes.map((n) => [n.id, n]));

  const contentW = nodes.reduce((m, n) => Math.max(m, n.x + n.width + 320), 2400);
  const contentH = nodes.reduce((m, n) => Math.max(m, n.y + n.height + 320), 1500);

  function posOf(n: WhiteboardNodeResponse): { x: number; y: number } {
    return drag && drag.id === n.id ? { x: drag.x, y: drag.y } : { x: n.x, y: n.y };
  }

  function canvasPoint(clientX: number, clientY: number) {
    const el = canvasRef.current;
    if (!el) return { x: 0, y: 0 };
    const r = el.getBoundingClientRect();
    return {
      x: clientX - r.left + el.scrollLeft,
      y: clientY - r.top + el.scrollTop,
    };
  }

  async function handleCanvasDoubleClick(e: React.MouseEvent<HTMLDivElement>) {
    if (e.target !== e.currentTarget) return; // 노드가 아닌 빈 캔버스에서만
    const p = canvasPoint(e.clientX, e.clientY);
    const text = await dialogs.prompt({
      title: "새 노드",
      placeholder: "노드 내용",
    });
    if (text === null) return;
    createNode.mutate({
      text: text.trim(),
      x: Math.max(0, p.x - 90),
      y: Math.max(0, p.y - 44),
    });
  }

  function onNodePointerDown(
    e: React.PointerEvent<HTMLDivElement>,
    node: WhiteboardNodeResponse,
  ) {
    e.stopPropagation();
    const p = canvasPoint(e.clientX, e.clientY);
    e.currentTarget.setPointerCapture(e.pointerId);
    setDrag({
      id: node.id,
      x: node.x,
      y: node.y,
      offsetX: p.x - node.x,
      offsetY: p.y - node.y,
      moved: false,
    });
  }

  function onNodePointerMove(e: React.PointerEvent<HTMLDivElement>) {
    if (!drag) return;
    const p = canvasPoint(e.clientX, e.clientY);
    const nx = Math.max(0, p.x - drag.offsetX);
    const ny = Math.max(0, p.y - drag.offsetY);
    setDrag((d) =>
      d ? { ...d, x: nx, y: ny, moved: d.moved || nx !== d.x || ny !== d.y } : d,
    );
  }

  async function onNodePointerUp(node: WhiteboardNodeResponse) {
    if (!drag || drag.id !== node.id) return;
    const moved = drag.moved;
    const { x, y } = drag;
    setDrag(null);
    if (moved) {
      updateNode.mutate({ nodeId: node.id, x, y });
      return;
    }
    // 이동 없는 클릭 → 연결 또는 본문 편집
    if (linkSource && linkSource !== node.id) {
      createEdge.mutate({ fromNodeId: linkSource, toNodeId: node.id });
      setLinkSource(null);
    } else if (linkSource === node.id) {
      setLinkSource(null);
    } else {
      const text = await dialogs.prompt({
        title: "노드 편집",
        placeholder: "노드 내용",
        defaultValue: node.text,
      });
      if (text !== null && text !== node.text) {
        updateNode.mutate({ nodeId: node.id, text });
      }
    }
  }

  async function handleDeleteNode(node: WhiteboardNodeResponse) {
    const ok = await dialogs.confirm({
      title: "노드 삭제",
      message: "이 노드와 연결된 엣지가 삭제됩니다.",
      confirmLabel: "삭제",
      danger: true,
    });
    if (ok) deleteNode.mutate(node.id);
  }

  async function handleDeleteEdge(edgeId: string) {
    const ok = await dialogs.confirm({
      title: "연결 삭제",
      confirmLabel: "삭제",
      danger: true,
    });
    if (ok) deleteEdge.mutate(edgeId);
  }

  return (
    <div className={`wb${linkSource ? " linking" : ""}`}>
      <div className="wb-toolbar">
        <span className="wb-hint">
          빈 곳 <strong>더블클릭</strong> → 노드 · 드래그 → 이동 · 클릭 → 편집 ·
          "⇢" → 연결
        </span>
        {linkSource && (
          <span className="wb-link-active">
            연결할 노드를 클릭하세요
            <button type="button" onClick={() => setLinkSource(null)}>
              취소
            </button>
          </span>
        )}
      </div>
      <div
        className="wb-canvas"
        ref={canvasRef}
        onDoubleClick={handleCanvasDoubleClick}
      >
        <div
          className="wb-surface"
          style={{ width: `${contentW}px`, height: `${contentH}px` }}
        >
          <svg
            className="wb-edges"
            width={contentW}
            height={contentH}
            aria-hidden="true"
          >
            <defs>
              <marker
                id="wb-arrowhead"
                viewBox="0 0 10 10"
                refX="9"
                refY="5"
                markerWidth="7"
                markerHeight="7"
                orient="auto-start-reverse"
              >
                <path d="M0,0 L10,5 L0,10 z" fill="var(--accent-bright)" />
              </marker>
            </defs>
            {edges.map((edge) => {
              const from = nodeById.get(edge.fromNodeId);
              const to = nodeById.get(edge.toNodeId);
              if (!from || !to) return null;
              const fp = posOf(from);
              const tp = posOf(to);
              const x1 = fp.x + from.width / 2;
              const y1 = fp.y + from.height / 2;
              const x2 = tp.x + to.width / 2;
              const y2 = tp.y + to.height / 2;
              const mx = (x1 + x2) / 2;
              const my = (y1 + y2) / 2;
              return (
                <g key={edge.id}>
                  <line
                    x1={x1}
                    y1={y1}
                    x2={x2}
                    y2={y2}
                    className="wb-edge"
                    markerEnd="url(#wb-arrowhead)"
                    onClick={() => handleDeleteEdge(edge.id)}
                  />
                  {edge.label && (
                    <text
                      x={mx}
                      y={my}
                      className="wb-edge-label"
                      textAnchor="middle"
                    >
                      {edge.label}
                    </text>
                  )}
                </g>
              );
            })}
          </svg>

          {nodes.map((node) => {
            const p = posOf(node);
            return (
              <div
                key={node.id}
                className={`wb-node${linkSource === node.id ? " linking-source" : ""}`}
                style={{
                  left: `${p.x}px`,
                  top: `${p.y}px`,
                  width: `${node.width}px`,
                  minHeight: `${node.height}px`,
                  borderColor: node.color ?? undefined,
                }}
                onPointerDown={(e) => onNodePointerDown(e, node)}
                onPointerMove={onNodePointerMove}
                onPointerUp={() => onNodePointerUp(node)}
              >
                <div className="wb-node-text">{node.text || "(빈 노드)"}</div>
                <div className="wb-node-actions">
                  <button
                    type="button"
                    className="wb-node-link"
                    onPointerDown={(e) => e.stopPropagation()}
                    onClick={(e) => {
                      e.stopPropagation();
                      setLinkSource(node.id);
                    }}
                    title="다른 노드와 연결"
                  >
                    ⇢
                  </button>
                  <button
                    type="button"
                    className="wb-node-del"
                    onPointerDown={(e) => e.stopPropagation()}
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDeleteNode(node);
                    }}
                    title="노드 삭제"
                  >
                    ✕
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </div>
      {wb.isLoading && <p className="loading">화이트보드 불러오는 중...</p>}
    </div>
  );
}
