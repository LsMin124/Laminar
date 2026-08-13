import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useDialogs } from "../ui/DialogProvider";
import { isSupportedImage, uploadImageAttachment } from "../../lib/attachments";
import {
  useCreateEdge,
  useCreateNode,
  useDeleteEdge,
  useDeleteNode,
  useRestoreEdge,
  useRestoreNode,
  useUpdateEdge,
  useUpdateNode,
  useWhiteboardGraph,
  type WhiteboardEdge,
  type WhiteboardNode as WbNode,
} from "../../lib/whiteboard";
import { WhiteboardEdges } from "./WhiteboardEdges";
import { WhiteboardNode } from "./WhiteboardNode";
import { WhiteboardTour } from "./WhiteboardTour";
import { COLORABLE_KINDS, DEFAULT_COLOR, WB_PALETTE, type WbShape } from "./whiteboardPalette";
import { WbHistory } from "./whiteboardHistory";
import {
  getFallbackClipboard,
  parseClipboardText,
  serializeClipboard,
  setFallbackClipboard,
  snapshotSelection,
  type WbClipboard,
} from "./whiteboardClipboard";
import { useWhiteboardShortcuts } from "./useWhiteboardShortcuts";
import {
  MAX_SCALE,
  MIN_NODE_H,
  MIN_NODE_W,
  MIN_SCALE,
  NEW_NODE_H,
  NEW_NODE_W,
  ZOOM_STEP,
  rectsIntersect,
  unionBounds,
  type Rect,
} from "./whiteboardGeometry";
import "./WhiteboardCanvas.css";

/** 진행 중 포인터 제스처 — 팬/이동(다중)/리사이즈/연결/마퀴 선택. dragRef(비렌더)로 추적. */
type DragRef =
  | { kind: "pan"; sx: number; sy: number; panX: number; panY: number }
  | {
      kind: "move";
      starts: Record<string, { x: number; y: number }>;
      swx: number;
      swy: number;
      moved: boolean;
    }
  | { kind: "resize"; id: string; ow: number; oh: number; swx: number; swy: number; x: number; y: number }
  | { kind: "link"; fromId: string }
  | { kind: "marquee"; swx: number; swy: number; base: ReadonlySet<string> };

const FIT_PADDING = 64;
const DUPLICATE_OFFSET = 24;
const HOME_VIEW = { scale: 1, panX: 80, panY: 80 };
const TOUR_DONE_KEY = "laminar.wbTourDone";

function tourAlreadySeen(): boolean {
  try {
    return window.localStorage.getItem(TOUR_DONE_KEY) === "1";
  } catch {
    // 저장소 접근 불가 환경 — 매 방문 자동 표시로 방해하지 않는다(? 버튼으로 열 수 있음).
    return true;
  }
}
function markTourSeen() {
  try {
    window.localStorage.setItem(TOUR_DONE_KEY, "1");
  } catch {
    // 기록 실패는 무해 — 다음 방문에 안내가 한 번 더 보일 뿐.
  }
}

/** WB-B 노드 kind별 생성 기본 크기. */
const CREATE_SIZE: Record<"STICKY" | "SHAPE" | "TEXT", { w: number; h: number }> = {
  STICKY: { w: 170, h: 170 },
  SHAPE: { w: 160, h: 110 },
  TEXT: { w: 220, h: 60 },
};

function clamp(v: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, v));
}

function normRect(x1: number, y1: number, x2: number, y2: number): Rect {
  return { x: Math.min(x1, x2), y: Math.min(y1, y2), w: Math.abs(x2 - x1), h: Math.abs(y2 - y1) };
}

/**
 * 매 렌더 재생성되는 핸들러의 항등성을 고정 — memo된 자식(WhiteboardNode)이 함수 prop 때문에
 * 매번 재렌더되는 것을 막는다. 최신 구현은 ref로 참조하므로 stale closure 없음(pasteRef 관용구).
 */
function useStableHandler<A extends unknown[]>(fn: (...args: A) => void): (...args: A) => void {
  const implRef = useRef(fn);
  useEffect(() => {
    implRef.current = fn;
  });
  // 항등성은 useCallback([])이 보장하고, ref는 호출 시점에만 읽는다(렌더 중 ref 접근 금지 규칙).
  return useCallback((...args: A) => implRef.current(...args), []);
}

/**
 * 화이트보드 캔버스 — FigJam 준거 조작: 배경 드래그=마퀴 선택, Space·휠클릭 드래그=팬,
 * 휠=스크롤·Ctrl(⌘)+휠/핀치=커서 기준 줌. 다중 선택 그룹 이동, Ctrl+C/V·Ctrl+D 복제(연결 포함),
 * 배경 더블클릭=md 노드, 이미지=드롭·붙여넣기·"+ 이미지", nub 드래그=연결.
 */
export function WhiteboardCanvas({ tabId }: { tabId: string }) {
  const graph = useWhiteboardGraph(tabId);
  const createNode = useCreateNode(tabId);
  const updateNode = useUpdateNode(tabId);
  const deleteNode = useDeleteNode(tabId);
  const createEdge = useCreateEdge(tabId);
  const deleteEdge = useDeleteEdge(tabId);
  const updateEdge = useUpdateEdge(tabId);
  const restoreNode = useRestoreNode(tabId);
  const restoreEdge = useRestoreEdge(tabId);
  const dialogs = useDialogs();
  // WB-C undo/redo 스택 — 탭이 바뀌면 새로 시작(다른 탭의 명령이 섞이지 않게).
  // eslint-disable-next-line react-hooks/exhaustive-deps -- tabId 변경 시 새 스택 생성이 목적(값 참조 아님)
  const history = useMemo(() => new WbHistory(), [tabId]);

  const outerRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const lastPointerRef = useRef<{ x: number; y: number } | null>(null);
  const [view, setView] = useState(HOME_VIEW);
  const dragRef = useRef<DragRef | null>(null);
  const [moving, setMoving] = useState<Record<string, { x: number; y: number }> | null>(null);
  const [resizing, setResizing] = useState<{
    id: string;
    x: number;
    y: number;
    w: number;
    h: number;
  } | null>(null);
  const [link, setLink] = useState<{ fromId: string; sx: number; sy: number; x: number; y: number } | null>(
    null,
  );
  const [marquee, setMarquee] = useState<Rect | null>(null);
  const [selectedIds, setSelectedIds] = useState<ReadonlySet<string>>(new Set<string>());
  const [editingId, setEditingId] = useState<string | null>(null);
  const [spaceHeld, setSpaceHeld] = useState(false);
  // 온보딩 투어 — 첫 방문에만 자동 시작, 이후엔 툴바 ? 버튼으로 재실행.
  const [tourOpen, setTourOpen] = useState(() => !tourAlreadySeen());

  const nodes = graph.data?.nodes ?? [];
  const edges = graph.data?.edges ?? [];

  function sizeOf(n: WbNode) {
    return { w: n.width ?? NEW_NODE_W, h: n.height ?? NEW_NODE_H };
  }
  function rectOf(n: WbNode): Rect {
    if (resizing && resizing.id === n.id) {
      return { x: resizing.x, y: resizing.y, w: resizing.w, h: resizing.h };
    }
    const s = sizeOf(n);
    const m = moving?.[n.id];
    return { x: m?.x ?? n.x, y: m?.y ?? n.y, w: s.w, h: s.h };
  }
  function toWorld(clientX: number, clientY: number) {
    const r = outerRef.current?.getBoundingClientRect();
    if (!r) return { x: 0, y: 0 };
    return {
      x: (clientX - r.left - view.panX) / view.scale,
      y: (clientY - r.top - view.panY) / view.scale,
    };
  }
  function viewportCenterWorld() {
    const r = outerRef.current?.getBoundingClientRect();
    const cx = (r?.left ?? 0) + (r ? r.width / 2 : 200);
    const cy = (r?.top ?? 0) + (r ? r.height / 2 : 200);
    return toWorld(cx, cy);
  }
  function pasteWorldPos() {
    const p = lastPointerRef.current;
    return p ? toWorld(p.x, p.y) : viewportCenterWorld();
  }

  // 휠 — FigJam 준거: 기본=스크롤(팬), Ctrl/⌘(트랙패드 핀치 포함)=커서 기준 줌. passive:false로 preventDefault.
  useEffect(() => {
    const el = outerRef.current;
    if (!el) return;
    function onWheel(e: WheelEvent) {
      e.preventDefault();
      if (e.ctrlKey || e.metaKey) {
        const r = el!.getBoundingClientRect();
        const px = e.clientX - r.left;
        const py = e.clientY - r.top;
        const factor = Math.exp(-e.deltaY * 0.0022);
        setView((v) => {
          const ns = clamp(v.scale * factor, MIN_SCALE, MAX_SCALE);
          const wx = (px - v.panX) / v.scale;
          const wy = (py - v.panY) / v.scale;
          return { scale: ns, panX: px - wx * ns, panY: py - wy * ns };
        });
        return;
      }
      const dx = e.shiftKey && e.deltaX === 0 ? e.deltaY : e.deltaX;
      const dy = e.shiftKey && e.deltaX === 0 ? 0 : e.deltaY;
      setView((v) => ({ ...v, panX: v.panX - dx, panY: v.panY - dy }));
    }
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, []);

  // 붙여넣기 — 노드 스냅샷(내부 복사) 우선, 그다음 이미지. input/textarea·노드 편집 중엔 무시.
  const pasteRef = useRef<(e: ClipboardEvent) => void>(() => {});
  useEffect(() => {
    pasteRef.current = (e: ClipboardEvent) => {
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === "INPUT" || t.tagName === "TEXTAREA" || t.isContentEditable)) return;
      if (editingId) return;
      const at = pasteWorldPos();
      const text = e.clipboardData?.getData("text/plain") ?? "";
      const snap = parseClipboardText(text);
      if (snap) {
        e.preventDefault();
        void pasteSnapshot(snap, at.x, at.y);
        return;
      }
      const item = Array.from(e.clipboardData?.items ?? []).find((i) => i.type.startsWith("image/"));
      const file = item?.getAsFile();
      if (file) {
        e.preventDefault();
        void createImageNode(file, at.x, at.y);
        return;
      }
      const fallback = getFallbackClipboard();
      if (fallback && !text) {
        e.preventDefault();
        void pasteSnapshot(fallback, at.x, at.y);
      }
    };
  });
  useEffect(() => {
    const h = (e: ClipboardEvent) => pasteRef.current(e);
    window.addEventListener("paste", h);
    return () => window.removeEventListener("paste", h);
  }, []);

  function beginPanGesture(e: React.PointerEvent<Element>) {
    e.preventDefault();
    outerRef.current?.setPointerCapture(e.pointerId);
    dragRef.current = { kind: "pan", sx: e.clientX, sy: e.clientY, panX: view.panX, panY: view.panY };
  }

  /** 노드 pointerdown — Space/휠클릭이면 팬, Shift는 선택 토글, 그 외 (그룹) 이동 시작. */
  function beginMove(e: React.PointerEvent<HTMLDivElement>, n: WbNode) {
    if (e.button === 1 || (e.button === 0 && spaceHeld)) {
      e.stopPropagation();
      beginPanGesture(e);
      return;
    }
    if (e.button !== 0) return;
    e.stopPropagation();
    if (e.shiftKey && selectedIds.has(n.id)) {
      const next = new Set(selectedIds);
      next.delete(n.id);
      setSelectedIds(next);
      return;
    }
    const ids: ReadonlySet<string> = e.shiftKey
      ? new Set([...selectedIds, n.id])
      : selectedIds.has(n.id)
        ? selectedIds
        : new Set([n.id]);
    setSelectedIds(ids);
    outerRef.current?.setPointerCapture(e.pointerId);
    const w = toWorld(e.clientX, e.clientY);
    const starts: Record<string, { x: number; y: number }> = {};
    for (const node of nodes) {
      if (ids.has(node.id)) starts[node.id] = { x: node.x, y: node.y };
    }
    dragRef.current = { kind: "move", starts, swx: w.x, swy: w.y, moved: false };
    setMoving(starts);
  }

  function beginResize(e: React.PointerEvent<HTMLSpanElement>, n: WbNode) {
    if (e.button !== 0) return;
    e.stopPropagation();
    outerRef.current?.setPointerCapture(e.pointerId);
    const w = toWorld(e.clientX, e.clientY);
    const s = sizeOf(n);
    dragRef.current = { kind: "resize", id: n.id, ow: s.w, oh: s.h, swx: w.x, swy: w.y, x: n.x, y: n.y };
    setResizing({ id: n.id, x: n.x, y: n.y, w: s.w, h: s.h });
  }

  function beginLink(e: React.PointerEvent<HTMLSpanElement>, n: WbNode) {
    if (e.button !== 0) return;
    e.stopPropagation();
    outerRef.current?.setPointerCapture(e.pointerId);
    const s = sizeOf(n);
    const w = toWorld(e.clientX, e.clientY);
    dragRef.current = { kind: "link", fromId: n.id };
    setLink({ fromId: n.id, sx: n.x + s.w, sy: n.y + s.h / 2, x: w.x, y: w.y });
  }

  /** 배경 pointerdown — Space/휠클릭=팬, 좌클릭=마퀴 선택(Shift=기존 선택에 추가). */
  function onBgPointerDown(e: React.PointerEvent<HTMLDivElement>) {
    const t = e.target as HTMLElement;
    if (t !== outerRef.current && !t.classList.contains("wb-world")) return;
    if (e.button === 1 || (e.button === 0 && spaceHeld)) {
      beginPanGesture(e);
      return;
    }
    if (e.button !== 0) return;
    outerRef.current?.setPointerCapture(e.pointerId);
    const w = toWorld(e.clientX, e.clientY);
    const base: ReadonlySet<string> = e.shiftKey ? new Set(selectedIds) : new Set<string>();
    if (!e.shiftKey) setSelectedIds(new Set<string>());
    dragRef.current = { kind: "marquee", swx: w.x, swy: w.y, base };
    setMarquee({ x: w.x, y: w.y, w: 0, h: 0 });
  }

  function onPointerMove(e: React.PointerEvent<HTMLDivElement>) {
    lastPointerRef.current = { x: e.clientX, y: e.clientY };
    const d = dragRef.current;
    if (!d) return;
    if (d.kind === "pan") {
      setView((v) => ({ ...v, panX: d.panX + (e.clientX - d.sx), panY: d.panY + (e.clientY - d.sy) }));
      return;
    }
    const w = toWorld(e.clientX, e.clientY);
    if (d.kind === "move") {
      const dx = w.x - d.swx;
      const dy = w.y - d.swy;
      if (Math.abs(dx) > 1 || Math.abs(dy) > 1) d.moved = true;
      setMoving(
        Object.fromEntries(Object.entries(d.starts).map(([id, p]) => [id, { x: p.x + dx, y: p.y + dy }])),
      );
    } else if (d.kind === "resize") {
      setResizing({
        id: d.id,
        x: d.x,
        y: d.y,
        w: Math.max(MIN_NODE_W, d.ow + (w.x - d.swx)),
        h: Math.max(MIN_NODE_H, d.oh + (w.y - d.swy)),
      });
    } else if (d.kind === "link") {
      setLink((l) => (l ? { ...l, x: w.x, y: w.y } : l));
    } else if (d.kind === "marquee") {
      const rect = normRect(d.swx, d.swy, w.x, w.y);
      setMarquee(rect);
      const hit = nodes.filter((n) => rectsIntersect(rect, rectOf(n))).map((n) => n.id);
      setSelectedIds(new Set([...d.base, ...hit]));
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
      if (moving && d.moved) {
        const before = d.starts;
        const after = Object.fromEntries(
          Object.entries(moving).map(([id, p]) => [id, { x: Math.round(p.x), y: Math.round(p.y) }]),
        );
        for (const [id, p] of Object.entries(after)) {
          updateNode.mutate({ nodeId: id, x: p.x, y: p.y });
        }
        history.push({
          undo: () => {
            for (const [id, p] of Object.entries(before)) {
              updateNode.mutate({ nodeId: id, x: Math.round(p.x), y: Math.round(p.y) });
            }
          },
          redo: () => {
            for (const [id, p] of Object.entries(after)) {
              updateNode.mutate({ nodeId: id, x: p.x, y: p.y });
            }
          },
        });
      }
      setMoving(null);
    } else if (d.kind === "resize") {
      if (resizing) {
        const nodeId = resizing.id;
        const before = { width: Math.round(d.ow), height: Math.round(d.oh) };
        const after = { width: Math.round(resizing.w), height: Math.round(resizing.h) };
        updateNode.mutate({ nodeId, ...after });
        history.push({
          undo: () => updateNode.mutate({ nodeId, ...before }),
          redo: () => updateNode.mutate({ nodeId, ...after }),
        });
      }
      setResizing(null);
    } else if (d.kind === "link") {
      const w = toWorld(e.clientX, e.clientY);
      const target = nodeAt(w.x, w.y, d.fromId);
      if (target) void createEdgeWithHistory(d.fromId, target.id);
      setLink(null);
    } else if (d.kind === "marquee") {
      setMarquee(null);
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

  async function createAtWorld(wx: number, wy: number) {
    try {
      const node = await createNode.mutateAsync({
        kind: "MD",
        x: Math.round(wx - NEW_NODE_W / 2),
        y: Math.round(wy - NEW_NODE_H / 2),
        width: NEW_NODE_W,
        height: NEW_NODE_H,
        text: "",
        bodyMd: "",
      });
      history.push({
        undo: () => deleteNode.mutate(node.id),
        redo: () => restoreNode.mutate(node.id),
      });
    } catch {
      void dialogs.alert("노드 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
  }

  /** 엣지 생성 + undo 기록 — restore로 같은 id가 유지된다. */
  async function createEdgeWithHistory(fromNodeId: string, toNodeId: string) {
    try {
      const edge = await createEdge.mutateAsync({ fromNodeId, toNodeId });
      history.push({
        undo: () => deleteEdge.mutate(edge.id),
        redo: () => restoreEdge.mutate(edge.id),
      });
    } catch {
      // 동일 연결이 이미 있는 경우(활성 유니크) — 새 정보가 없으니 조용히 무시한다.
    }
  }

  /** 이미지 노드 생성 → R2 업로드 → attrs.attachmentId patch. 업로드 실패 시 방금 만든 노드를 제거(고아 방지). */
  async function createImageNode(file: File, wx: number, wy: number) {
    if (!isSupportedImage(file)) return;
    const node = await createNode.mutateAsync({
      kind: "IMAGE",
      x: Math.round(wx - NEW_NODE_W / 2),
      y: Math.round(wy - NEW_NODE_H / 2),
      width: NEW_NODE_W,
      height: NEW_NODE_H,
      text: file.name,
    });
    try {
      const attachmentId = await uploadImageAttachment(file, "WHITEBOARD_NODE", node.id);
      await updateNode.mutateAsync({ nodeId: node.id, attrs: { attachmentId } });
      history.push({
        undo: () => deleteNode.mutate(node.id),
        redo: () => restoreNode.mutate(node.id),
      });
    } catch {
      deleteNode.mutate(node.id);
      // 업로드 실패를 무음으로 흘리면 "노드가 사라졌다"로만 보인다(Q8) — 원인 힌트와 함께 알린다.
      void dialogs.alert("이미지 업로드에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
  }

  function onBgDoubleClick(e: React.MouseEvent<HTMLDivElement>) {
    const t = e.target as HTMLElement;
    if (t !== outerRef.current && !t.classList.contains("wb-world")) return;
    const w = toWorld(e.clientX, e.clientY);
    // 노드 pointerdown이 outer에 setPointerCapture를 걸면 브라우저가 click/dblclick target을
    // 캡처 대상(.wb)으로 재지정한다 — target 검사만으론 노드 위 더블클릭을 배경으로 오인해
    // 새 노드를 만들어버리므로, 좌표 히트테스트로 노드 위면 생성 대신 편집에 진입한다.
    const hit = nodeAt(w.x, w.y, "");
    if (hit) {
      if (hit.kind !== "IMAGE") setEditingId(hit.id);
      return;
    }
    void createAtWorld(w.x, w.y);
  }

  function onDrop(e: React.DragEvent<HTMLDivElement>) {
    const file = Array.from(e.dataTransfer.files).find((f) => isSupportedImage(f));
    if (!file) return;
    e.preventDefault();
    const w = toWorld(e.clientX, e.clientY);
    void createImageNode(file, w.x, w.y);
  }

  function onPickImage(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file || !isSupportedImage(file)) return;
    const w = viewportCenterWorld();
    void createImageNode(file, w.x, w.y);
  }

  function addNodeAtCenter() {
    const w = viewportCenterWorld();
    void createAtWorld(w.x, w.y);
  }

  /** WB-B 노드 생성 — kind별 기본 크기·색으로 화면 중앙에(도형은 rect로 시작). undo 기록 포함. */
  async function createKindAtCenter(kind: "STICKY" | "SHAPE" | "TEXT") {
    const w = viewportCenterWorld();
    const size = CREATE_SIZE[kind];
    try {
      const node = await createNode.mutateAsync({
        kind,
        x: Math.round(w.x - size.w / 2),
        y: Math.round(w.y - size.h / 2),
        width: size.w,
        height: size.h,
        text: "",
        bodyMd: "",
        attrs:
          kind === "SHAPE"
            ? { color: DEFAULT_COLOR.SHAPE, shape: "rect" }
            : { color: DEFAULT_COLOR[kind] },
      });
      history.push({
        undo: () => deleteNode.mutate(node.id),
        redo: () => restoreNode.mutate(node.id),
      });
    } catch {
      void dialogs.alert("노드 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }
  }

  async function onEditLabel(edge: WhiteboardEdge) {
    const value = await dialogs.prompt({ title: "화살표 라벨", placeholder: edge.label ?? "관계 설명" });
    if (value === null) return;
    const before = edge.label;
    const after = value.trim() || null;
    updateEdge.mutate({ edgeId: edge.id, label: after });
    history.push({
      undo: () => updateEdge.mutate({ edgeId: edge.id, label: before }),
      redo: () => updateEdge.mutate({ edgeId: edge.id, label: after }),
    });
  }

  function deleteSelection() {
    if (selectedIds.size === 0) return;
    const ids = [...selectedIds];
    for (const id of ids) deleteNode.mutate(id);
    history.push({
      undo: () => {
        for (const id of ids) restoreNode.mutate(id);
      },
      redo: () => {
        for (const id of ids) deleteNode.mutate(id);
      },
    });
    setSelectedIds(new Set<string>());
  }

  /** 노드 ✕ — 그 노드가 다중 선택에 포함돼 있으면 선택 전체 삭제(FigJam 준거). */
  function deleteNodeOrSelection(id: string) {
    if (selectedIds.has(id) && selectedIds.size > 1) {
      deleteSelection();
      return;
    }
    deleteNode.mutate(id);
    history.push({
      undo: () => restoreNode.mutate(id),
      redo: () => deleteNode.mutate(id),
    });
    if (selectedIds.has(id)) {
      const next = new Set(selectedIds);
      next.delete(id);
      setSelectedIds(next);
    }
  }

  function copySelection() {
    const snap = snapshotSelection(nodes, edges, selectedIds, rectOf);
    if (!snap) return;
    setFallbackClipboard(snap);
    void navigator.clipboard?.writeText(serializeClipboard(snap)).catch(() => {
      // 클립보드 권한 없음 — 모듈 폴백으로 계속 동작.
    });
  }

  /** 스냅샷을 (wx,wy) 중심에 생성 — 노드 먼저, 양끝 포함 엣지까지 복원 후 새 노드들을 선택. */
  async function pasteSnapshot(c: WbClipboard, wx: number, wy: number) {
    const ox = wx - c.w / 2;
    const oy = wy - c.h / 2;
    try {
      const created = await Promise.all(
        c.nodes.map((s) =>
          createNode.mutateAsync({
            kind: s.kind,
            x: Math.round(ox + s.dx),
            y: Math.round(oy + s.dy),
            width: s.width,
            height: s.height,
            text: s.text,
            bodyMd: s.bodyMd,
            attrs: Object.keys(s.attrs).length > 0 ? s.attrs : undefined,
          }),
        ),
      );
      const createdEdges = await Promise.all(
        c.edges.map((es) =>
          createEdge.mutateAsync({
            fromNodeId: created[es.from].id,
            toNodeId: created[es.to].id,
            label: es.label ?? undefined,
          }),
        ),
      );
      setSelectedIds(new Set(created.map((n) => n.id)));
      const nodeIds = created.map((n) => n.id);
      const edgeIds = createdEdges.map((edge) => edge.id);
      history.push({
        undo: () => {
          for (const id of edgeIds) deleteEdge.mutate(id);
          for (const id of nodeIds) deleteNode.mutate(id);
        },
        redo: () => {
          for (const id of nodeIds) restoreNode.mutate(id);
          for (const id of edgeIds) restoreEdge.mutate(id);
        },
      });
    } catch {
      void dialogs.alert("일부 노드를 붙여넣지 못했습니다.");
    }
  }

  function duplicateSelection() {
    const snap = snapshotSelection(nodes, edges, selectedIds, rectOf);
    const b = unionBounds(nodes.filter((n) => selectedIds.has(n.id)).map(rectOf));
    if (!snap || !b) return;
    void pasteSnapshot(snap, b.x + b.w / 2 + DUPLICATE_OFFSET, b.y + b.h / 2 + DUPLICATE_OFFSET);
  }

  function nudgeSelection(dx: number, dy: number) {
    const moved = nodes
      .filter((n) => selectedIds.has(n.id))
      .map((n) => ({
        id: n.id,
        before: { x: Math.round(n.x), y: Math.round(n.y) },
        after: { x: Math.round(n.x + dx), y: Math.round(n.y + dy) },
      }));
    if (moved.length === 0) return;
    for (const m of moved) updateNode.mutate({ nodeId: m.id, ...m.after });
    history.push({
      undo: () => {
        for (const m of moved) updateNode.mutate({ nodeId: m.id, ...m.before });
      },
      redo: () => {
        for (const m of moved) updateNode.mutate({ nodeId: m.id, ...m.after });
      },
    });
  }

  /** Escape — 진행 중 제스처·선택 모두 해제. */
  function cancelGestures() {
    dragRef.current = null;
    setMoving(null);
    setResizing(null);
    setLink(null);
    setMarquee(null);
    setSelectedIds(new Set<string>());
  }

  function zoomAtCenter(nextScale: number) {
    const r = outerRef.current?.getBoundingClientRect();
    const px = r ? r.width / 2 : 0;
    const py = r ? r.height / 2 : 0;
    setView((v) => {
      const ns = clamp(nextScale, MIN_SCALE, MAX_SCALE);
      return {
        scale: ns,
        panX: px - ((px - v.panX) / v.scale) * ns,
        panY: py - ((py - v.panY) / v.scale) * ns,
      };
    });
  }

  /** 노드 전체가 보이게 맞춤(최대 100%). 노드 없으면 초기 뷰. */
  function zoomToFit() {
    const b = unionBounds(nodes.map(rectOf));
    const r = outerRef.current?.getBoundingClientRect();
    if (!b || !r) {
      setView(HOME_VIEW);
      return;
    }
    const scale = clamp(
      Math.min(r.width / (b.w + FIT_PADDING * 2), r.height / (b.h + FIT_PADDING * 2)),
      MIN_SCALE,
      1,
    );
    setView({
      scale,
      panX: (r.width - b.w * scale) / 2 - b.x * scale,
      panY: (r.height - b.h * scale) / 2 - b.y * scale,
    });
  }

  useWhiteboardShortcuts({
    enabled: editingId === null && !tourOpen,
    onDeleteSelection: deleteSelection,
    onSelectAll: () => setSelectedIds(new Set(nodes.map((n) => n.id))),
    onEscape: cancelGestures,
    onDuplicate: duplicateSelection,
    onCopy: copySelection,
    onNudge: nudgeSelection,
    onZoomFit: zoomToFit,
    onZoom100: () => zoomAtCenter(1),
    onSpaceChange: setSpaceHeld,
    onUndo: () => history.undo(),
    onRedo: () => history.redo(),
  });

  // memo된 WhiteboardNode에 넘기는 핸들러 — 항등성 고정으로 팬/줌/마퀴 프레임마다의 전체 노드
  // 재렌더(마크다운 재파싱 포함)를 차단한다.
  const handleBodyDown = useStableHandler(beginMove);
  const handleNubDown = useStableHandler(beginLink);
  const handleResizeDown = useStableHandler(beginResize);
  const handleDelete = useStableHandler(deleteNodeOrSelection);
  const handleSaveEdit = useStableHandler((id: string, patch: { text: string; bodyMd: string }) => {
    const node = nodes.find((n) => n.id === id);
    const before = node ? { text: node.text ?? "", bodyMd: node.bodyMd ?? "" } : null;
    updateNode.mutate({ nodeId: id, text: patch.text, bodyMd: patch.bodyMd });
    if (before && (before.text !== patch.text || before.bodyMd !== patch.bodyMd)) {
      history.push({
        undo: () => updateNode.mutate({ nodeId: id, ...before }),
        redo: () => updateNode.mutate({ nodeId: id, text: patch.text, bodyMd: patch.bodyMd }),
      });
    }
    setEditingId(null);
  });
  const handleCancelEdit = useCallback(() => setEditingId(null), []);
  const closeTour = useCallback(() => {
    markTourSeen();
    setTourOpen(false);
  }, []);

  // 선택 팔레트 바 — 색을 입힐 수 있는 선택 노드(스티키·도형·텍스트)가 있을 때만 표시.
  const colorableSelected = nodes.filter(
    (n) => selectedIds.has(n.id) && COLORABLE_KINDS.has(n.kind),
  );
  const shapeSelected = colorableSelected.some((n) => n.kind === "SHAPE");

  /** attrs 일괄 변경 + undo 기록 — before/after 스냅샷 쌍. */
  function applyAttrs(
    changes: { id: string; before: Record<string, unknown>; after: Record<string, unknown> }[],
  ) {
    if (changes.length === 0) return;
    for (const c of changes) updateNode.mutate({ nodeId: c.id, attrs: c.after });
    history.push({
      undo: () => {
        for (const c of changes) updateNode.mutate({ nodeId: c.id, attrs: c.before });
      },
      redo: () => {
        for (const c of changes) updateNode.mutate({ nodeId: c.id, attrs: c.after });
      },
    });
  }
  function applyColor(colorId: string) {
    applyAttrs(
      colorableSelected.map((n) => ({
        id: n.id,
        before: n.attrs,
        after: { ...n.attrs, color: colorId },
      })),
    );
  }
  function applyShape(shape: WbShape) {
    applyAttrs(
      colorableSelected
        .filter((n) => n.kind === "SHAPE")
        .map((n) => ({ id: n.id, before: n.attrs, after: { ...n.attrs, shape } })),
    );
  }

  // 휠 리스너 effect는 mount 1회만 실행되므로 ref 컨테이너(.wb)는 로딩/오류 중에도 항상 렌더한다 —
  // 조기 return하면 outerRef가 빈 채로 effect가 끝나 리스너가 영영 안 붙는다(스크롤·줌 무반응 회귀).
  const status = graph.isLoading ? "불러오는 중…" : graph.isError ? "화이트보드를 불러오지 못했습니다." : null;

  return (
    <div
      className={`wb${spaceHeld ? " hand" : ""}`}
      ref={outerRef}
      onPointerDown={onBgPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onDoubleClick={onBgDoubleClick}
      onDrop={onDrop}
      onDragOver={(e) => e.preventDefault()}
      style={{
        backgroundSize: `${24 * view.scale}px ${24 * view.scale}px`,
        backgroundPosition: `${view.panX}px ${view.panY}px`,
      }}
    >
      {status !== null && <div className="wb-empty">{status}</div>}
      <div
        className="wb-world"
        style={{ transform: `translate(${view.panX}px, ${view.panY}px) scale(${view.scale})` }}
      >
        <WhiteboardEdges
          nodes={nodes}
          edges={edges}
          rectOf={rectOf}
          linkLine={link}
          onDeleteEdge={(id) => {
            deleteEdge.mutate(id);
            history.push({
              undo: () => restoreEdge.mutate(id),
              redo: () => deleteEdge.mutate(id),
            });
          }}
          onEditLabel={onEditLabel}
        />
        {nodes.map((n) => {
          const r = rectOf(n);
          // 드래그/리사이즈 중이 아닌 노드는 원본 객체 그대로 넘겨 memo가 재렌더를 건너뛰게 한다.
          const shown =
            r.x === n.x &&
            r.y === n.y &&
            r.w === (n.width ?? NEW_NODE_W) &&
            r.h === (n.height ?? NEW_NODE_H)
              ? n
              : { ...n, x: r.x, y: r.y, width: r.w, height: r.h };
          return (
            <WhiteboardNode
              key={n.id}
              node={shown}
              selected={selectedIds.has(n.id)}
              editing={editingId === n.id}
              onBodyDown={handleBodyDown}
              onNubDown={handleNubDown}
              onResizeDown={handleResizeDown}
              onStartEdit={setEditingId}
              onSaveEdit={handleSaveEdit}
              onCancelEdit={handleCancelEdit}
              onDelete={handleDelete}
            />
          );
        })}
      </div>
      {marquee && (
        <div
          className="wb-marquee"
          style={{
            left: marquee.x * view.scale + view.panX,
            top: marquee.y * view.scale + view.panY,
            width: marquee.w * view.scale,
            height: marquee.h * view.scale,
          }}
        />
      )}
      <div className="wb-toolbar" onPointerDown={(e) => e.stopPropagation()}>
        <button type="button" data-tour="add-node" onClick={addNodeAtCenter}>
          + 노드
        </button>
        <button type="button" data-tour="add-image" onClick={() => fileInputRef.current?.click()}>
          + 이미지
        </button>
        <button type="button" onClick={() => void createKindAtCenter("STICKY")}>
          + 스티키
        </button>
        <button type="button" onClick={() => void createKindAtCenter("SHAPE")}>
          + 도형
        </button>
        <button type="button" onClick={() => void createKindAtCenter("TEXT")}>
          + 텍스트
        </button>
        <span className="wb-sep" />
        <span className="wb-tool-group" data-tour="zoom">
          <button type="button" onClick={() => zoomAtCenter(view.scale / ZOOM_STEP)} aria-label="축소">
            −
          </button>
          <span className="wb-zoom">{Math.round(view.scale * 100)}%</span>
          <button type="button" onClick={() => zoomAtCenter(view.scale * ZOOM_STEP)} aria-label="확대">
            ＋
          </button>
          <button type="button" onClick={zoomToFit} title="전체 맞춤 (Shift+1)">
            맞춤
          </button>
          <button type="button" onClick={() => zoomAtCenter(1)} title="100% (Ctrl+0)">
            100%
          </button>
        </span>
        <span className="wb-sep" />
        <button type="button" data-tour="help" title="사용 안내 다시 보기" onClick={() => setTourOpen(true)}>
          ?
        </button>
      </div>
      {colorableSelected.length > 0 && (
        <div className="wb-selbar" onPointerDown={(e) => e.stopPropagation()}>
          {WB_PALETTE.map((p) => (
            <button
              key={p.id}
              type="button"
              className="wb-swatch"
              style={{ background: p.fill }}
              title={`색상 ${p.id}`}
              aria-label={`색상 ${p.id}`}
              onClick={() => applyColor(p.id)}
            />
          ))}
          {shapeSelected && (
            <>
              <span className="wb-sep" />
              <button type="button" onClick={() => applyShape("rect")} title="사각형">
                ▭
              </button>
              <button type="button" onClick={() => applyShape("ellipse")} title="원">
                ◯
              </button>
              <button type="button" onClick={() => applyShape("diamond")} title="마름모">
                ◇
              </button>
            </>
          )}
        </div>
      )}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/png,image/jpeg,image/gif,image/webp"
        hidden
        onChange={onPickImage}
      />
      {status === null && nodes.length === 0 && (
        <div className="wb-hint">
          빈 화이트보드 — 더블클릭으로 노드 생성, 드래그로 선택.
          <br />
          Space·휠클릭 드래그=이동 · 휠=스크롤 · Ctrl+휠=줌 · 이미지는 드롭/붙여넣기.
        </div>
      )}
      {tourOpen && <WhiteboardTour containerRef={outerRef} onClose={closeTour} />}
    </div>
  );
}
