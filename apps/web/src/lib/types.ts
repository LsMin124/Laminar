/**
 * 백엔드 응답 타입 — JpaEntity → DTO record와 동일 형식.
 */

export type Uuid = string;
export type IsoDate = string;
export type IsoDateTime = string;

export interface AuthResponse {
  userId: Uuid;
  email: string;
  displayName: string;
  emailVerified: boolean;
}

export interface WorkspaceResponse {
  id: Uuid;
  name: string;
  slug: string;
  ownerUserId: Uuid;
  defaultTimezone: string;
  settings: Record<string, unknown>;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export type BoardDefaultView = "CALENDAR" | "LIST";

export interface BoardResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  name: string;
  slug: string;
  defaultView: BoardDefaultView;
  iconName: string | null;
  iconColor: string | null;
  settings: Record<string, unknown>;
  priority: number;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export type CardImportance =
  | "NORMAL"
  | "CF"
  | "URGENT"
  | "PURCHASE"
  | "PERPETUAL_VER"
  | "ARTICLE"
  | "PROCESS";

export type CardOrigin =
  | "MANUAL"
  | "RRULE_EXPANSION"
  | "GCAL_PULL"
  | "EQUIPMENT_RESERVATION";

export interface CardResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  boardId: Uuid | null;
  title: string;
  slug: string | null;
  bodyMd: string | null;
  startDate: IsoDate | null;
  endDate: IsoDate | null;
  startTime: string | null;
  allDay: boolean;
  timeZone: string | null;
  importance: CardImportance;
  completed: boolean;
  linkedPerpetualId: Uuid | null;
  rrule: string | null;
  origin: CardOrigin;
  priority: number;
  attrs: Record<string, unknown>;
  archivedAt: IsoDateTime | null;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface DateMemoResponse {
  boardId: Uuid;
  userId: Uuid;
  date: IsoDate;
  bodyMd: string;
  attrs: Record<string, unknown>;
}

export interface CalendarViewResponse {
  boardId: Uuid;
  from: IsoDate;
  to: IsoDate;
  cards: CardResponse[];
  dateMemos: DateMemoResponse[];
}

export interface RenderedBodyResponse {
  cardId: Uuid;
  html: string;
}
