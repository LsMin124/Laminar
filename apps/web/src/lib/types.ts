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

export type AttachmentParentType =
  | "CARD"
  | "PERPETUAL_VERSION"
  | "EQUIPMENT_LOG"
  | "DATE_MEMO"
  | "SAMPLE_MANAGER_LINK";

export interface AttachmentResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  uploadedBy: Uuid;
  parentType: AttachmentParentType;
  parentId: Uuid;
  storageKey: string;
  originalName: string | null;
  mime: string | null;
  sizeBytes: number | null;
  sha256: string | null;
  accessCheckRequired: boolean;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface PresignedUrlResponse {
  url: string;
  storageKey: string | null;
  expiresInSeconds: number;
}

export type WorkspaceRole = "OWNER" | "MEMBER" | "VIEWER";

export interface MemberResponse {
  workspaceId: Uuid;
  userId: Uuid;
  email: string | null;
  displayName: string | null;
  role: WorkspaceRole;
  joinedAt: IsoDateTime;
}

export interface PendingInvitationResponse {
  id: Uuid;
  email: string;
  role: WorkspaceRole;
  invitedBy: Uuid;
  expiresAt: IsoDateTime;
  createdAt: IsoDateTime;
}

export interface GroupResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  boardId: Uuid;
  name: string;
  color: string | null;
  priority: number;
  attrs: Record<string, unknown>;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface CardRelationResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  boardId: Uuid;
  fromCardId: Uuid;
  toCardId: Uuid;
  relationKind: string;
  summary: string | null;
  bodyMd: string | null;
  attrs: Record<string, unknown>;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface GroupRelationResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  boardId: Uuid;
  fromGroupId: Uuid;
  toGroupId: Uuid;
  relationKind: string;
  summary: string | null;
  bodyMd: string | null;
  attrs: Record<string, unknown>;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface BoardGraphResponse {
  boardId: Uuid;
  cards: CardResponse[];
  groups: GroupResponse[];
  cardRelations: CardRelationResponse[];
  groupRelations: GroupRelationResponse[];
}
