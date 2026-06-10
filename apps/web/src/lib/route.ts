/**
 * URL 라우트 정의 + 순수 파서/포매터 (DX-3).
 *
 * 스킴: /s/{subjectId}[/t/{tabId}][/d/{kind}[/{docId}]][?view=calendar]
 * - 활성 주제·탭·활성 문서(doctab)의 정본은 URL — 새로고침·딥링크·뒤로가기 복원.
 * - view는 부가 상태라 쿼리(기본 canvas는 생략). 열린 문서 "목록"은 세션 상태로 남고
 *   활성 문서만 URL에 싣는다(딥링크 진입 시 그 문서 하나를 연다).
 * - 장비 doc은 단일(싱글톤)이라 id 세그먼트 생략(/d/equipment).
 * - /reset 등 비-/s 경로는 전부 "주제 미지정"으로 파싱된다(인증 화면 분기는 App이 담당).
 */

/** 열린 문서 종류 — 카드·그룹·탭·주제(UUID 키) + 장비(싱글톤 doc). */
export type DocKind = "card" | "group" | "tab" | "subject" | "equipment";

/** 장비 doc의 고정 sentinel id(UUID와 충돌 없음) — doctab 키·URL 복원 공용. */
export const EQUIPMENT_DOC_ID = "equipment";

export interface RouteDoc {
  kind: DocKind;
  id: string;
}

export interface Route {
  subjectId: string | null;
  tabId: string | null;
  view: "canvas" | "calendar";
  doc: RouteDoc | null;
}

const DOC_KINDS: ReadonlySet<string> = new Set([
  "card",
  "group",
  "tab",
  "subject",
  "equipment",
]);

export function parseRoute(pathname: string, search = ""): Route {
  const route: Route = { subjectId: null, tabId: null, view: "canvas", doc: null };
  if (new URLSearchParams(search).get("view") === "calendar") route.view = "calendar";

  const seg = pathname.split("/").filter(Boolean);
  if (seg[0] !== "s" || !seg[1]) return route;
  route.subjectId = seg[1];

  let i = 2;
  if (seg[i] === "t" && seg[i + 1]) {
    route.tabId = seg[i + 1];
    i += 2;
  }
  if (seg[i] === "d" && seg[i + 1] && DOC_KINDS.has(seg[i + 1])) {
    const kind = seg[i + 1] as DocKind;
    const id = kind === "equipment" ? EQUIPMENT_DOC_ID : (seg[i + 2] ?? null);
    if (id) route.doc = { kind, id };
  }
  return route;
}

export function formatRoute(route: Route): string {
  if (!route.subjectId) return "/";
  let path = `/s/${route.subjectId}`;
  if (route.tabId) path += `/t/${route.tabId}`;
  if (route.doc) {
    path += route.doc.kind === "equipment" ? "/d/equipment" : `/d/${route.doc.kind}/${route.doc.id}`;
  }
  if (route.view === "calendar") path += "?view=calendar";
  return path;
}
