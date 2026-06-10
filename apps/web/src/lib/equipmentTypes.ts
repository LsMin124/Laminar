/**
 * 장비(공용 자원) 표면 공유 타입 — lib/equipment.ts 리소스별 분리 (DX-2′).
 *
 * 훅은 리소스별 모듈(equipment/reservations/equipmentLogs/sharedCalendars)에, 타입은 여기 한 곳에.
 */

export interface Equipment {
  id: string;
  subjectId: string;
  createdBy: string | null;
  name: string;
  description: string | null;
  location: string | null;
  active: boolean;
  /** 로그 시트 컬럼 정의(JSONB) — 로그 기능 증분에서 사용, 현재는 빈 배열로 둠. */
  defaultLogColumns: Record<string, unknown>[];
  createdAt: string;
  updatedAt: string;
}

/** 예약 — subject-shared·시간 겹침 차단(409). 시각은 ISO OffsetDateTime 문자열. */
export interface Reservation {
  id: string;
  subjectId: string;
  equipmentId: string;
  reservedBy: string;
  startAt: string;
  endAt: string;
  purpose: string | null;
  rrule: string | null;
  cardId: string | null;
  createdAt: string;
  updatedAt: string;
}

export type LogColumnType = "TEXT" | "NUMBER" | "ENUM" | "BOOL" | "DATETIME";

export interface LogColumn {
  id: string;
  equipmentId: string;
  columnKey: string;
  columnLabel: string;
  columnType: LogColumnType;
  enumValues: string[] | null;
  required: boolean;
  priority: number;
  defaultValue: string | null;
}

export interface LogEntry {
  id: string;
  equipmentId: string;
  loggedBy: string;
  loggedAt: string;
  reservationId: string | null;
  values: Record<string, unknown>;
  notes: string | null;
  createdAt: string;
}

/** 공용 캘린더 — 여러 개 가능, 장비 연동 1:1(equipmentId) 또는 일반(null). */
export interface SharedCalendar {
  id: string;
  equipmentId: string | null;
  name: string;
  color: string | null;
  defaultView: string | null;
  announcementOnly: boolean;
  createdAt: string;
}

export interface Announcement {
  id: string;
  sharedCalendarId: string;
  postedBy: string;
  startAt: string;
  endAt: string | null;
  title: string;
  bodyMd: string | null;
  createdAt: string;
}
