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
  /** P3b 자동그룹 — groupId → 멤버 cardId 목록. */
  groupMembers: Record<string, string[]>;
  /** P4b 탭 스코프 — tabId → 멤버 groupId 목록 (탭=그룹, 구상안 §3.3). */
  tabGroups: Record<string, string[]>;
}

// 독립 화이트보드 (그래프 뷰) — 타임라인/캘린더와 무관한 자체 엔티티.
export interface WhiteboardNodeResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  boardId: Uuid;
  text: string;
  x: number;
  y: number;
  width: number;
  height: number;
  color: string | null;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface WhiteboardEdgeResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  boardId: Uuid;
  fromNodeId: Uuid;
  toNodeId: Uuid;
  label: string | null;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface WhiteboardResponse {
  boardId: Uuid;
  nodes: WhiteboardNodeResponse[];
  edges: WhiteboardEdgeResponse[];
}

export interface TabResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  boardId: Uuid;
  parentTabId: Uuid | null;
  name: string;
  priority: number;
  visible: boolean;
  collapsed: boolean;
  showLabel: boolean;
  labelColor: string | null;
  attrs: Record<string, unknown>;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface PerpetualNoteResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  boardId: Uuid;
  tabId: Uuid | null;
  parentPerpetualId: Uuid | null;
  title: string;
  bodyMd: string | null;
  priority: number;
  attrs: Record<string, unknown>;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export type PerpetualColumnType =
  | "TEXT"
  | "NUMBER"
  | "DATE"
  | "BOOLEAN"
  | "ENUM"
  | "JSON";

export interface PerpetualColumnDefinitionResponse {
  id: Uuid;
  workspaceId: Uuid;
  boardId: Uuid;
  name: string;
  type: PerpetualColumnType;
  enumValues: string[] | null;
  priority: number;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface PerpetualColumnValueResponse {
  perpetualNoteId: Uuid;
  columnDefinitionId: Uuid;
  value: string | null;
}

export interface PerpetualVersionResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  perpetualNoteId: Uuid;
  cardId: Uuid | null;
  versionNumber: number;
  summary: string | null;
  bodyDiffMd: string | null;
  currentDiff: boolean;
  committedAt: IsoDateTime;
  createdAt: IsoDateTime;
}

export interface EquipmentResponse {
  id: Uuid;
  workspaceId: Uuid;
  createdBy: Uuid | null;
  name: string;
  description: string | null;
  location: string | null;
  active: boolean;
  defaultLogColumns: Record<string, unknown>[];
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface EquipmentReservationResponse {
  id: Uuid;
  workspaceId: Uuid;
  equipmentId: Uuid;
  reservedBy: Uuid;
  startAt: IsoDateTime;
  endAt: IsoDateTime;
  purpose: string | null;
  rrule: string | null;
  cardId: Uuid | null;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
}

export interface AdminBoardSummaryResponse {
  id: Uuid;
  workspaceId: Uuid;
  userId: Uuid;
  name: string;
  slug: string;
  priority: number;
}

export interface AdminCardMetadataResponse {
  id?: Uuid;
  cardId?: Uuid;
  title?: string;
  userId?: Uuid;
  boardId?: Uuid;
  importance?: string;
  startDate?: IsoDate | null;
  endDate?: IsoDate | null;
  completed?: boolean;
  createdAt?: IsoDateTime;
  updatedAt?: IsoDateTime;
  [key: string]: unknown;
}

export interface AdminCardBodyRevealResponse {
  cardId: Uuid;
  userId: Uuid;
  title: string;
  bodyMd: string | null;
}

export interface AuditLogResponse {
  id: Uuid;
  workspaceId: Uuid;
  actorUserId: Uuid | null;
  action: string;
  targetType: string;
  targetId: Uuid | null;
  summary: string | null;
  payload: Record<string, unknown>;
  occurredAt: IsoDateTime;
}
