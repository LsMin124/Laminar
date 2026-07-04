import { useEffect, useRef, useState } from "react";
import { useDialogs } from "../ui/DialogProvider";
import {
  useCreateEdge,
  useCreateNode,
  useDeleteEdge,
  useDeleteNode,
  useUpdateEdge,
  useUpdateNode,
  useWhiteboardGraph,
  type WhiteboardEdge,
  type WhiteboardNode as WbNode,
} from "../../lib/whiteboard";
import { WhiteboardEdges } from "./WhiteboardEdges";
import { WhiteboardNode } from "./WhiteboardNode";
import {
  MAX_SCALE,
  MIN_NODE_H,
  MIN_NODE_W,
  MIN_SCALE,
  NEW_NODE_H,
  NEW_NODE_W,
  ZOOM_STEP,
  type Rect,
} from "./whiteboardGeometry";
import "./WhiteboardCanvas.css";

/** 진행 중 포인터 제스처 — 팬/이동/리사이즈/연결. dragRef(비렌더)로 추적, active/link state로 렌더. */
type DragRef =
  | { kind: "pan"; sx: number; sy: number; panX: number; panY: number }
  | {
      kind: "move";
      id: string;
      ox: number;
      oy: number;
      swx: number;
      swy: number;
      w: number;
      h: number;
      moved: boolean;
    }
  | { kind: "resize"; id: string; ow: number; oh: number; swx: number; swy: number; x: number; y: number }
  | { kind: "link"; fromId: string };

function clamp(v: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, v));
}

/**
 * 화이트보드 캔버스 — 자유 배치 노드 + 관계 화살표 + 줌/무한 팬(transform 월드 레이어).
 * DAG 캔버스(x=시간축·네이티브 스크롤)와 달리 world↔screen 변환을 직접 관리한다.
 * 배경 더블클릭=노드 생성, 노드 nub 드래그=연결, 휠=커서 기준 줌, 배경 드래그=팬.
 */
export function WhiteboardCanvas({ tabId }: { tabId: string }) {
  const graph = useWhiteboardGraph(tabId);
  const createNode = useCreateNode(tabId);
  const updateNode = useUpdateNode(tabId);
  const deleteNode = useDeleteNode(tabId);
  const createEdge = useCreateEdge(tabId);
  const deleteEdge = useDeleteEdge(tabId);
  const updateEdge = useUpdateEdge(tabId);
  const dialogs = useDialogs();

  const outerRef = useRef<HTMLDivElement>(null);
  const [view, setView] = useState({ scale: 1, panX: 80, panY: 80 });
  const dragRef = useRef<DragRef | null>(null);
  const [active, setActive] = useState<{ id: string; x: number; y: number; w: number; h: number } | null>(
    null,
  );
  const [link, setLink] = useState<{ fromId: string; sx: number; sy: number; x: number; y: number } | null>(
    null,
  );
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);

  const nodes = graph.data?.nodes ?? [];
  const edges = graph.data?.edges ?? [];

  function sizeOf(n: WbNode) {
    return { w: n.width ?? NEW_NODE_W, h: n.height ?? NEW_NODE_H };
  }
  function rectOf(n: WbNode): Rect {
    if (active && active.id === n.id) return { x: active.x, y: active.y, w: active.w, h: active.h };
    const s = sizeOf(n);
    return { x: n.x, y: n.y, w: s.w, h: s.h };
  }
  function toWorld(clientX: number, clientY: number) {
    const r = outerRef.current?.getBoundingClientRect();
    if (!r) return { x: 0, y: 0 };
    return {
      x: (clientX - r.left - view.panX) / view.scale,
      y: (clientY - r.top - view.panY) / view.scale,
    };
  }

  // 줌 — 커서 아래 월드 점이 고정되도록 pan 보정. React onWheel은 passive라 네이티브 리스너로 preventDefault.
  useEffect(() => {
    const el = outerRef.current;
    if (!el) return;
    function onWheel(e: WheelEvent) {
      e.preventDefault();
      const r = el!.getBoundingClientRect();
      const px = e.clientX - r.left;
      const py = e.clientY - r.top;
      const factor = e.deltaY < 0 ? ZOOM_STEP : 1 / ZOOM_STEP;
      setView((v) => {
        const ns = clamp(v.scale * factor, MIN_SCALE, MAX_SCALE);
        const wx = (px - v.panX) / v.scale;
        const wy = (py - v.panY) / v.scale;
        return { scale: ns, panX: px - wx * ns, panY: py - wy * ns };
      });
    }
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, []);

  function beginMove(e: React.PointerEvent<HTMLDivElement>, n: WbNode) {
    e.stopPropagation();
    outerRef.current?.setPointerCapture(e.pointerId);
    const w = toWorld(e.clientX, e.clientY);
    const s = sizeOf(n);
    dragRef.current = {
      kind: "move",
      id: n.id,
      ox: n.x,
      oy: n.y,
      swx: w.x,
      swy: w.y,
      w: s.w,
      h: s.h,
      moved: false,
    };
    setActive({ id: n.id, x: n.x, y: n.y, w: s.w, h: s.h });
    setSelectedId(n.id);
  }

  function beginResize(e: React.PointerEvent<HTMLSpanElement>, n: WbNode) {
    e.stopPropagation();
    outerRef.current?.setPointerCapture(e.pointerId);
    const w = toWorld(e.clientX, e.clientY);
    const s = sizeOf(n);
    dragRef.current = { kind: "resize", id: n.id, ow: s.w, oh: s.h, swx: w.x, swy: w.y, x: n.x, y: n.y };
    setActive({ id: n.id, x: n.x, y: n.y, w: s.w, h: s.h });
  }

  function beginLink(e: React.PointerEvent<HTMLSpanElement>, n: WbNode) {
    e.stopPropagation();
    outerRef.current?.setPointerCapture(e.pointerId);
    const s = sizeOf(n);
    const w = toWorld(e.clientX, e.clientY);
    dragRef.current = { kind: "link", fromId: n.id };
    setLink({ fromId: n.id, sx: n.x + s.w, sy: n.y + s.h / 2, x: w.x, y: w.y });
  }

  function beginPan(e: React.PointerEvent<HTMLDivElement>) {
    const t = e.target as HTMLElement;
    // 진짜 배경(outer/world)에서만 — 엣지 선·라벨은 자체 클릭, 노드/nub는 stopPropagation.
    if (t !== outerRef.current && !t.classList.contains("wb-world")) return;
    setSelectedId(null);
    outerRef.current?.setPointerCapture(e.pointerId);
    dragRef.current = { kind: "pan", sx: e.clientX, sy: e.clientY, panX: view.panX, panY: view.panY };
  }

  function onPointerMove(e: React.PointerEvent<HTMLDivElement>) {
    const d = dragRef.current;
    if (!d) return;
    if (d.kind === "pan") {
      setView((v) => ({ ...v, panX: d.panX + (e.clientX - d.sx), panY: d.panY + (e.clientY - d.sy) }));
      return;
    }
    const w = toWorld(e.clientX, e.clientY);
    if (d.kind === "move") {
      if (Math.abs(w.x - d.swx) > 1 || Math.abs(w.y - d.swy) > 1) d.moved = true;
      setActive({ id: d.id, x: d.ox + (w.x - d.swx), y: d.oy + (w.y - d.swy), w: d.w, h: d.h });
    } else if (d.kind === "resize") {
      setActive({
        id: d.id,
        x: d.x,
        y: d.y,
        w: Math.max(MIN_NODE_W, d.ow + (w.x - d.swx)),
        h: Math.max(MIN_NODE_H, d.oh + (w.y - d.swy)),
      });
    } else if (d.kind === "link") {
      setLink((l) => (l ? { ...l, x: w.x, y: w.y } : l));
    }
  }

  function onPointerUp(e: React.PointerEvent<HTMLDivElement>) {
    const d = dragRef.current;
    dragRef.current = null;
    try {
      outerRef.current?.releasePointerCapture(e.pointerId);
    } catch {
      // 캡처 안 된 포인터 release는 무시.
    }
    if (!d) return;
    if (d.kind === "move") {
      if (active && d.moved) {
        updateNode.mutate({ nodeId: d.id, x: Math.round(active.x), y: Math.round(active.y) });
      }
      setActive(null);
    } else if (d.kind === "resize") {
      if (active) updateNode.mutate({ nodeId: d.id, width: Math.round(active.w), height: Math.round(active.h) });
      setActive(null);
    } else if (d.kind === "link") {
      const w = toWorld(e.clientX, e.clientY);
      const target = nodeAt(w.x, w.y, d.fromId);
      if (target) createEdge.mutate({ fromNodeId: d.fromId, toNodeId: target.id });
      setLink(null);
    }
  }

  function nodeAt(wx: number, wy: number, excludeId: string): WbNode | null {
    for (let i = nodes.length - 1; i >= 0; i--) {
      const n = nodes[i];
      if (n.id === excludeId) continue;
      const r = rectOf(n);
      if (wx >= r.x && wx <= r.x + r.w && wy >= r.y && wy <= r.y + r.h) return n;
    }
    return null;
  }

  function createAtWorld(wx: number, wy: number) {
    createNode.mutate({
      kind: "MD",
      x: Math.round(wx - NEW_NODE_W / 2),
      y: Math.round(wy - NEW_NODE_H / 2),
      width: NEW_NODE_W,
      height: NEW_NODE_H,
      text: "",
      bodyMd: "",
    });
  }

  function onBgDoubleClick(e: React.MouseEvent<HTMLDivElement>) {
    const t = e.target as HTMLElement;
    if (t !== outerRef.current && !t.classList.contains("wb-world")) return;
    const w = toWorld(e.clientX, e.clientY);
    createAtWorld(w.x, w.y);
  }

  function addNodeAtCenter() {
    const r = outerRef.current?.getBoundingClientRect();
    const cx = (r?.left ?? 0) + (r ? r.width / 2 : 200);
    const cy = (r?.top ?? 0) + (r ? r.height / 2 : 200);
    const w = toWorld(cx, cy);
    createAtWorld(w.x, w.y);
  }

  async function onEditLabel(edge: WhiteboardEdge) {
    const value = await dialogs.prompt({ title: "화살표 라벨", placeholder: edge.label ?? "관계 설명" });
    if (value === null) return;
    updateEdge.mutate({ edgeId: edge.id, label: value.trim() || null });
  }

  function zoomBy(factor: number) {
    const r = outerRef.current?.getBoundingClientRect();
    const px = r ? r.width / 2 : 0;
    const py = r ? r.height / 2 : 0;
    setView((v) => {
      const ns = clamp(v.scale * factor, MIN_SCALE, MAX_SCALE);
      return { scale: ns, panX: px - ((px - v.panX) / v.scale) * ns, panY: py - ((py - v.panY) / v.scale) * ns };
    });
  }

  if (graph.isLoading) return <div className="wb-empty">불러오는 중…</div>;
  if (graph.isError) return <div className="wb-empty">화이트보드를 불러오지 못했습니다.</div>;

  return (
    <div
      className="wb"
      ref={outerRef}
      onPointerDown={beginPan}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onDoubleClick={onBgDoubleClick}
      style={{
        backgroundSize: `${24 * view.scale}px ${24 * view.scale}px`,
        backgroundPosition: `${view.panX}px ${view.panY}px`,
      }}
    >
      <div
        className="wb-world"
        style={{ transform: `translate(${view.panX}px, ${view.panY}px) scale(${view.scale})` }}
      >
        <WhiteboardEdges
          nodes={nodes}
          edges={edges}
          rectOf={rectOf}
          linkLine={link}
          onDeleteEdge={(id) => deleteEdge.mutate(id)}
          onEditLabel={onEditLabel}
        />
        {nodes.map((n) => {
          const r = rectOf(n);
          const shown =
            active && active.id === n.id ? { ...n, x: r.x, y: r.y, width: r.w, height: r.h } : n;
          return (
            <WhiteboardNode
              key={n.id}
              node={shown}
              selected={selectedId === n.id}
              editing={editingId === n.id}
              onBodyDown={beginMove}
              onNubDown={beginLink}
              onResizeDown={beginResize}
              onSelect={setSelectedId}
              onStartEdit={setEditingId}
              onSaveEdit={(id, patch) => {
                updateNode.mutate({ nodeId: id, text: patch.text, bodyMd: patch.bodyMd });
                setEditingId(null);
              }}
              onCancelEdit={() => setEditingId(null)}
              onDelete={(id) => {
                deleteNode.mutate(id);
                setSelectedId(null);
              }}
            />
          );
        })}
      </div>
      <div className="wb-toolbar" onPointerDown={(e) => e.stopPropagation()}>
        <button type="button" onClick={addNodeAtCenter}>
          + 노드
        </button>
        <span className="wb-sep" />
        <button type="button" onClick={() => zoomBy(1 / ZOOM_STEP)} aria-label="축소">
          −
        </button>
        <span className="wb-zoom">{Math.round(view.scale * 100)}%</span>
        <button type="button" onClick={() => zoomBy(ZOOM_STEP)} aria-label="확대">
          ＋
        </button>
        <button type="button" onClick={() => setView({ scale: 1, panX: 80, panY: 80 })}>
          리셋
        </button>
      </div>
      {nodes.length === 0 && (
        <div className="wb-hint">빈 화이트보드 — 배경을 더블클릭하거나 “+ 노드”로 시작하세요.</div>
      )}
    </div>
  );
}
