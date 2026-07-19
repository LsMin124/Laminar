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

export const NEW_NODE_W = 240;
export const NEW_NODE_H = 150;
export const MIN_NODE_W = 140;
export const MIN_NODE_H = 90;
export const MIN_SCALE = 0.25;
export const MAX_SCALE = 3;
export const ZOOM_STEP = 1.1;

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
