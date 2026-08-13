/**
 * 화이트보드 기하 — 자유 배치라 DAG의 시간축 elbow와 달리 두 노드 중심을 잇는 직선이 각 사각형
 * 경계와 만나는 점을 앵커로 쓴다(최근접 변 진입). 직선 엣지 + 화살표.
 */

export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

/** rect 중심에서 (tx,ty) 방향으로 나가는 반직선이 rect 경계와 만나는 점. */
export function anchorToward(r: Rect, tx: number, ty: number): { x: number; y: number } {
  const cx = r.x + r.w / 2;
  const cy = r.y + r.h / 2;
  const dx = tx - cx;
  const dy = ty - cy;
  if (dx === 0 && dy === 0) return { x: cx, y: cy };
  const hw = r.w / 2;
  const hh = r.h / 2;
  const tX = dx === 0 ? Infinity : hw / Math.abs(dx);
  const tY = dy === 0 ? Infinity : hh / Math.abs(dy);
  const t = Math.min(tX, tY);
  return { x: cx + dx * t, y: cy + dy * t };
}

export type Side = "top" | "right" | "bottom" | "left";

/** rect에서 (tx,ty)를 향해 나가는 진출 변 — 수평 우세면 left/right, 수직 우세면 top/bottom (WB-D 4방향 앵커). */
export function sideToward(r: Rect, tx: number, ty: number): Side {
  const dx = tx - (r.x + r.w / 2);
  const dy = ty - (r.y + r.h / 2);
  if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? "right" : "left";
  return dy >= 0 ? "bottom" : "top";
}

/** 변의 중점 앵커. */
export function sideAnchor(r: Rect, side: Side): { x: number; y: number } {
  if (side === "top") return { x: r.x + r.w / 2, y: r.y };
  if (side === "bottom") return { x: r.x + r.w / 2, y: r.y + r.h };
  if (side === "left") return { x: r.x, y: r.y + r.h / 2 };
  return { x: r.x + r.w, y: r.y + r.h / 2 };
}

const SIDE_NORMAL: Record<Side, { x: number; y: number }> = {
  top: { x: 0, y: -1 },
  bottom: { x: 0, y: 1 },
  left: { x: -1, y: 0 },
  right: { x: 1, y: 0 },
};

const CURVE_MIN = 40;
const CURVE_MAX = 160;

export interface EdgeCurve {
  /** SVG path d — 진출 변에 수직으로 나가는 3차 베지어. */
  d: string;
  /** 시작 앵커(엔드포인트 핸들 위치). */
  a: { x: number; y: number };
  /** 끝 앵커. */
  b: { x: number; y: number };
  /** t=0.5 지점 — 라벨 좌표. */
  label: { x: number; y: number };
}

/** WB-D 곡선 커넥터 — 4방향 변 중점에서 변에 수직으로 진출하는 3차 베지어 + 라벨 위치. */
export function edgeCurve(from: Rect, to: Rect): EdgeCurve {
  const fromSide = sideToward(from, to.x + to.w / 2, to.y + to.h / 2);
  const toSide = sideToward(to, from.x + from.w / 2, from.y + from.h / 2);
  const a = sideAnchor(from, fromSide);
  const b = sideAnchor(to, toSide);
  const k = Math.min(CURVE_MAX, Math.max(CURVE_MIN, Math.hypot(b.x - a.x, b.y - a.y) / 2));
  const na = SIDE_NORMAL[fromSide];
  const nb = SIDE_NORMAL[toSide];
  const c1 = { x: a.x + na.x * k, y: a.y + na.y * k };
  const c2 = { x: b.x + nb.x * k, y: b.y + nb.y * k };
  return {
    d: `M ${a.x} ${a.y} C ${c1.x} ${c1.y}, ${c2.x} ${c2.y}, ${b.x} ${b.y}`,
    a,
    b,
    // 3차 베지어 t=0.5 = (a + 3·c1 + 3·c2 + b) / 8
    label: {
      x: (a.x + 3 * c1.x + 3 * c2.x + b.x) / 8,
      y: (a.y + 3 * c1.y + 3 * c2.y + b.y) / 8,
    },
  };
}

export const NEW_NODE_W = 240;
export const NEW_NODE_H = 150;
export const MIN_NODE_W = 140;
export const MIN_NODE_H = 90;
export const MIN_SCALE = 0.25;
export const MAX_SCALE = 3;
export const ZOOM_STEP = 1.1;

/** a가 b를 완전히 포함하는가 — 섹션은 마퀴에 완전히 들어와야 선택된다(WB-E). */
export function rectContains(a: Rect, b: Rect): boolean {
  return b.x >= a.x && b.y >= a.y && b.x + b.w <= a.x + a.w && b.y + b.h <= a.y + a.h;
}

/** 두 사각형이 겹치는지(경계 접촉 포함) — 마퀴 선택 판정. */
export function rectsIntersect(a: Rect, b: Rect): boolean {
  return a.x <= b.x + b.w && a.x + a.w >= b.x && a.y <= b.y + b.h && a.y + a.h >= b.y;
}

/** 여러 사각형의 합집합 경계. 빈 목록이면 null. */
export function unionBounds(rects: Rect[]): Rect | null {
  if (rects.length === 0) return null;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const r of rects) {
    minX = Math.min(minX, r.x);
    minY = Math.min(minY, r.y);
    maxX = Math.max(maxX, r.x + r.w);
    maxY = Math.max(maxY, r.y + r.h);
  }
  return { x: minX, y: minY, w: maxX - minX, h: maxY - minY };
}
