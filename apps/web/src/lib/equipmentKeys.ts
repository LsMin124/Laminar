import type { QueryClient } from "@tanstack/react-query";

/**
 * 장비 표면 쿼리 키 정본 (DX-2′ 키 팩토리) — dagKeys와 동형. 키 모양 변경은 여기만.
 */
export const equipmentKeys = {
  equipment: ["equipment"] as const,
  /** prefix 무효화용(범위 무관 전체 예약 뷰 갱신). */
  reservationsAll: ["reservations"] as const,
  reservations: (equipmentId: string | null, fromIso: string, toIso: string) =>
    ["reservations", equipmentId, fromIso, toIso] as const,
  myReservations: ["my-reservations"] as const,
  logColumns: (equipmentId: string | null) => ["log-columns", equipmentId] as const,
  logs: (equipmentId: string | null) => ["logs", equipmentId] as const,
  sharedCalendars: ["shared-calendars"] as const,
  /** prefix 무효화용. */
  announcementsAll: ["announcements"] as const,
  announcements: (calendarId: string | null, fromIso: string, toIso: string) =>
    ["announcements", calendarId, fromIso, toIso] as const,
};

/** 예약 변경 공통 무효화 — 장비별 범위 뷰 + 내 예약 통합뷰. */
export function invalidateReservations(qc: QueryClient): void {
  qc.invalidateQueries({ queryKey: equipmentKeys.reservationsAll });
  qc.invalidateQueries({ queryKey: equipmentKeys.myReservations });
}
