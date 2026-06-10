import type { Card } from "../../lib/graphTypes";
import { MS_DAY, parseDate, shortDate } from "../../lib/dateUtil";

/**
 * DAG 캔버스 좌표·렌더 상수 + 순수 기하/표시 헬퍼.
 * 좌표계(originMs·dateToX)와 상태 의존 geometry(nodeGeom)는 컨테이너가 보유하고,
 * 여기엔 상태 없는 값만 둔다 — DagNode/DagEdges/DagGroups가 prop-drill 없이 공유한다.
 */

export const PX_PER_DAY = 130;
export const LEFT_PAD = 80;
export const BACKLOG_W = 180;
export const BAR_H = 92;
export const BACKLOG_X = 8;
// 우측(미래) 무한스크롤 여유 — 좌측 origin 30일 버퍼에 대응. 끝까지 끌면 자동 확장되므로 휴식 헤드룸만 확보.
export const FORWARD_BUFFER_DAYS = 60;
// 진입 시 오늘을 뷰포트 가로 이 비율 지점에 배치(0.5=중앙, 0.4=살짝 좌측 → 미래 쪽을 더 넓게).
export const TODAY_VIEW_RATIO = 0.4;
export const EDGE_ZONE = 48;
export const PAN_SPEED = 14;
export const EDGE_STUB = 16;

/**
 * 카드 간 직각(꺾인) 엣지 경로 — 대각선 없이 가로·세로만.
 * - 같은 행: 곧은 수평선.
 * - 전방 여유 충분: A끝 → 중간 x에서 수직 → B시작 (대칭 엘보).
 * - 인접(다음날=간격 0)·겹침(B가 A보다 좌측): A 우측으로 스텁만큼 빠져나와 두 행 사이 레인으로
 *   되돌아온 뒤 B 좌측으로 우향 진입 → 항상 화살표가 오른쪽을 향하고 좌석이 끼이지 않는다.
 */
export function edgePath(sx: number, sy: number, ex: number, ey: number): string {
  if (Math.abs(ey - sy) < 1) return `M ${sx} ${sy} H ${ex}`;
  if (ex - sx >= 2 * EDGE_STUB) {
    const mx = (sx + ex) / 2;
    return `M ${sx} ${sy} H ${mx} V ${ey} H ${ex}`;
  }
  const x1 = sx + EDGE_STUB;
  const x2 = ex - EDGE_STUB;
  const my = (sy + ey) / 2;
  return `M ${sx} ${sy} H ${x1} V ${my} H ${x2} V ${ey} H ${ex}`;
}

/** 카드 개략 날짜/시간 — "6/4", "6/4–6/9"(멀티데이), "6/4 14:00"(시간지정), 날짜 없으면 "미정". */
export function cardMeta(c: Card): string {
  if (!c.startDate) return "미정";
  let r = shortDate(c.startDate);
  if (c.endDate && c.endDate !== c.startDate) r += `–${shortDate(c.endDate)}`;
  if (!c.allDay && c.startTime) r += ` ${c.startTime.slice(0, 5)}`;
  return r;
}

/** 카드 막대 폭(px) — 멀티데이는 일수×PX_PER_DAY, 단일일=1일폭, 날짜 미정=백로그폭. */
export function barWidth(c: Card): number {
  if (!c.startDate) return BACKLOG_W;
  if (c.endDate) {
    const span = (parseDate(c.endDate) - parseDate(c.startDate)) / MS_DAY + 1;
    return Math.max(PX_PER_DAY, span * PX_PER_DAY);
  }
  return PX_PER_DAY;
}
