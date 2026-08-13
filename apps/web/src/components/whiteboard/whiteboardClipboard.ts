/**
 * 화이트보드 내부 클립보드 — 선택 노드와 (양끝이 모두 선택에 포함된) 엣지를 좌상단 기준
 * 상대 좌표 스냅샷으로 담는다. 복사 시 OS 클립보드에 접두사 텍스트로도 기록해 탭/세션 간
 * 붙여넣기를 지원하고, 클립보드 권한이 없을 때를 위한 모듈 폴백 저장소를 함께 쓴다.
 */
import type { WhiteboardEdge, WhiteboardNode } from "../../lib/whiteboard";
import { unionBounds, type Rect } from "./whiteboardGeometry";

interface NodeSnapshot {
  kind: WhiteboardNode["kind"];
  dx: number;
  dy: number;
  width: number | null;
  height: number | null;
  text: string | null;
  bodyMd: string | null;
  attrs: Record<string, unknown>;
}

interface EdgeSnapshot {
  /** nodes 배열 인덱스. */
  from: number;
  to: number;
  label: string | null;
}

export interface WbClipboard {
  nodes: NodeSnapshot[];
  edges: EdgeSnapshot[];
  /** 스냅샷 전체 경계 크기 — 붙여넣기 시 중심 정렬용. */
  w: number;
  h: number;
}

const TEXT_PREFIX = "laminar-wb:1:";
const MAX_NODES = 300;

let fallbackStore: WbClipboard | null = null;

export function snapshotSelection(
  nodes: WhiteboardNode[],
  edges: WhiteboardEdge[],
  selectedIds: ReadonlySet<string>,
  rectOf: (n: WhiteboardNode) => Rect,
): WbClipboard | null {
  const picked = nodes.filter((n) => selectedIds.has(n.id));
  if (picked.length === 0) return null;
  const bounds = unionBounds(picked.map(rectOf));
  if (!bounds) return null;
  const index = new Map(picked.map((n, i) => [n.id, i]));
  return {
    nodes: picked.map((n) => {
      const r = rectOf(n);
      return {
        kind: n.kind,
        dx: r.x - bounds.x,
        dy: r.y - bounds.y,
        width: n.width,
        height: n.height,
        text: n.text,
        bodyMd: n.bodyMd,
        attrs: { ...n.attrs },
      };
    }),
    edges: edges.flatMap((e) => {
      const from = index.get(e.fromNodeId);
      const to = index.get(e.toNodeId);
      return from === undefined || to === undefined ? [] : [{ from, to, label: e.label }];
    }),
    w: bounds.w,
    h: bounds.h,
  };
}

export function serializeClipboard(c: WbClipboard): string {
  return TEXT_PREFIX + JSON.stringify(c);
}

function isFiniteNumber(v: unknown): v is number {
  return typeof v === "number" && Number.isFinite(v);
}

const NODE_KINDS: ReadonlySet<string> = new Set([
  "MD",
  "IMAGE",
  "STICKY",
  "SHAPE",
  "TEXT",
  "PEN",
  "SECTION",
]);

function parseNode(v: unknown): NodeSnapshot | null {
  if (typeof v !== "object" || v === null) return null;
  const o = v as Record<string, unknown>;
  if (typeof o.kind !== "string" || !NODE_KINDS.has(o.kind)) return null;
  if (!isFiniteNumber(o.dx) || !isFiniteNumber(o.dy)) return null;
  const attrsOk = typeof o.attrs === "object" && o.attrs !== null && !Array.isArray(o.attrs);
  return {
    // NODE_KINDS 검증을 통과했으므로 union으로 안전 — Set.has는 타입을 좁혀주지 않는다.
    kind: o.kind as WhiteboardNode["kind"],
    dx: o.dx,
    dy: o.dy,
    width: isFiniteNumber(o.width) ? o.width : null,
    height: isFiniteNumber(o.height) ? o.height : null,
    text: typeof o.text === "string" ? o.text : null,
    bodyMd: typeof o.bodyMd === "string" ? o.bodyMd : null,
    attrs: attrsOk ? (o.attrs as Record<string, unknown>) : {},
  };
}

/** OS 클립보드 텍스트에서 스냅샷 복원 — 접두사·형태·엣지 인덱스 범위를 검증, 아니면 null. */
export function parseClipboardText(text: string): WbClipboard | null {
  if (!text.startsWith(TEXT_PREFIX)) return null;
  try {
    const raw = JSON.parse(text.slice(TEXT_PREFIX.length)) as unknown;
    if (typeof raw !== "object" || raw === null) return null;
    const o = raw as Record<string, unknown>;
    if (!Array.isArray(o.nodes) || !Array.isArray(o.edges)) return null;
    if (o.nodes.length === 0 || o.nodes.length > MAX_NODES) return null;
    const nodes: NodeSnapshot[] = [];
    for (const n of o.nodes) {
      const parsed = parseNode(n);
      if (!parsed) return null;
      nodes.push(parsed);
    }
    const edges: EdgeSnapshot[] = [];
    for (const e of o.edges) {
      if (typeof e !== "object" || e === null) return null;
      const eo = e as Record<string, unknown>;
      if (!isFiniteNumber(eo.from) || !isFiniteNumber(eo.to)) return null;
      const from = Math.trunc(eo.from);
      const to = Math.trunc(eo.to);
      if (from < 0 || from >= nodes.length || to < 0 || to >= nodes.length || from === to) return null;
      edges.push({ from, to, label: typeof eo.label === "string" ? eo.label : null });
    }
    return {
      nodes,
      edges,
      w: isFiniteNumber(o.w) ? o.w : 0,
      h: isFiniteNumber(o.h) ? o.h : 0,
    };
  } catch {
    return null;
  }
}

/** OS 클립보드 접근이 막힌 환경 대비 — 같은 세션 안에서만 유효한 폴백. */
export function setFallbackClipboard(c: WbClipboard): void {
  fallbackStore = c;
}

export function getFallbackClipboard(): WbClipboard | null {
  return fallbackStore;
}
