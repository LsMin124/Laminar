/**
 * 날짜 유틸 — DAG 캔버스·캘린더가 공유하는 순수 날짜 함수.
 * 카드 날짜는 모두 UTC 자정 기준 "YYYY-MM-DD" 문자열 ↔ epoch ms로 다룬다(타임존 흔들림 방지).
 */

export const MS_DAY = 86400000;

/** "YYYY-MM-DD" → UTC 자정 epoch ms. */
export function parseDate(s: string): number {
  const [y, m, d] = s.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
}

/** epoch ms → "YYYY-MM-DD"(UTC). */
export function fmtDate(ms: number): string {
  const d = new Date(ms);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}-${p(d.getUTCMonth() + 1)}-${p(d.getUTCDate())}`;
}

/** 로컬 오늘을 UTC 자정 epoch ms로(캘린더·축의 '오늘' 기준). */
export function todayUtc(): number {
  const n = new Date();
  return Date.UTC(n.getFullYear(), n.getMonth(), n.getDate());
}

/** 해당 ms가 속한 달의 1일(UTC 자정). */
export function startOfMonth(ms: number): number {
  const d = new Date(ms);
  return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1);
}

/** "YYYY-MM-DD" → "M/D" 약식 표기. */
export function shortDate(iso: string): string {
  const [, m, d] = iso.split("-");
  return `${Number(m)}/${Number(d)}`;
}
