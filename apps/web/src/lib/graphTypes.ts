/**
 * DAG 작업면(주제>탭>카드) 공유 타입 — lib/dag.ts 리소스별 분리 (DX-2).
 *
 * 백엔드 contract(/api/subjects·/api/tabs·graph 페이로드)에 정합. 훅은 리소스별 모듈
 * (subjects/tabs/graph/cards/groups/categories)에, 타입은 여기 한 곳에 둔다.
 */
type Uuid = string;
export type IsoDate = string;

export interface Subject {
  id: Uuid;
  name: string;
  slug: string;
  ownerUserId: Uuid;
  /** 주제 종별 — LAB 승격 시 장비·가입 표면 활성(서버 SubjectResponse.kind 거울, LAB재설계 L4). */
  kind: "PERSONAL" | "LAB";
  /** 주제(워크스페이스) 전반 개요 — 독립 마크다운 문서. */
  bodyMd: string | null;
}

export interface Tab {
  id: Uuid;
  name: string;
  slug: string;
  /** 탭 종류 — DAG 캔버스(카드=시간축) vs 화이트보드(자유 배치 노드). 서버 TabKind 거울. */
  kind: "DAG" | "WHITEBOARD";
  priority: number;
  /** 탭(보드)별 메모/개요 — 독립 마크다운 문서. */
  bodyMd: string | null;
}

export interface Card {
  id: Uuid;
  tabId: Uuid | null;
  title: string;
  /** 전체 본문 — 그래프 응답엔 null(페이로드 경감). 단건 GET /api/cards/{id}에서만 채워진다. */
  bodyMd: string | null;
  /** 본문 평문 발췌(서버 산출) — 그래프 노드 미리보기용. 단건 GET에선 null. */
  bodyExcerpt: string | null;
  startDate: IsoDate | null;
  endDate: IsoDate | null;
  startTime: string | null;
  allDay: boolean;
  importance: string;
  completed: boolean;
  priority: number;
  canvasY: number | null;
}

export interface CardRelation {
  id: Uuid;
  fromCardId: Uuid;
  toCardId: Uuid;
  relationKind: string;
  summary: string | null;
}

export interface Group {
  id: Uuid;
  name: string;
  color: string | null;
  /**
   * 그룹도 카드처럼 독립 마크다운 문서를 가진다(서브그래프 목표·메모).
   * 그래프 응답엔 null(페이로드 경감) — 단건 GET /api/groups/{id}에서만 채워진다.
   */
  bodyMd: string | null;
}

/** 그룹 간 화살표 — 카드 관계(CardRelation)와 별개 레이어. 캔버스에서 쿨 톤·점선으로 구분 렌더. */
export interface GroupRelation {
  id: Uuid;
  fromGroupId: Uuid;
  toGroupId: Uuid;
  relationKind: string;
  summary: string | null;
}

/** 카드 카테고리 — 주제(subject) 단위로 공유되는 명명 분류(이름 + 색). 카드 좌측 스트라이프에 반영. */
export interface Category {
  id: Uuid;
  name: string;
  color: string | null;
}

export interface TabGraph {
  tabId: Uuid;
  cards: Card[];
  cardRelations: CardRelation[];
  groups: Group[];
  /** 그룹 간 화살표 목록. */
  groupRelations: GroupRelation[];
  /** groupId → 멤버 cardId 목록. */
  groupMembers: Record<string, string[]>;
  /** 현재 주제의 카테고리 목록(모든 탭 공유). */
  categories: Category[];
  /** cardId → categoryId. 미분류 카드는 키 없음. */
  cardCategoryIds: Record<string, string>;
}
