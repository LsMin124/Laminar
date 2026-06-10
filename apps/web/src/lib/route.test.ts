import { describe, expect, test } from "vitest";
import { EQUIPMENT_DOC_ID, formatRoute, parseRoute, type Route } from "./route";

const S = "11111111-1111-1111-1111-111111111111";
const T = "22222222-2222-2222-2222-222222222222";
const C = "33333333-3333-3333-3333-333333333333";

describe("parseRoute", () => {
  test("루트·비-/s 경로(/reset 등)는 주제 미지정", () => {
    expect(parseRoute("/")).toEqual({ subjectId: null, tabId: null, view: "canvas", doc: null });
    expect(parseRoute("/reset")).toMatchObject({ subjectId: null });
    expect(parseRoute("/s")).toMatchObject({ subjectId: null });
  });

  test("주제만 / 주제+탭", () => {
    expect(parseRoute(`/s/${S}`)).toMatchObject({ subjectId: S, tabId: null, doc: null });
    expect(parseRoute(`/s/${S}/t/${T}`)).toMatchObject({ subjectId: S, tabId: T });
  });

  test("문서: 카드·그룹·탭·주제는 id 필수, 장비는 sentinel id 부여", () => {
    expect(parseRoute(`/s/${S}/t/${T}/d/card/${C}`).doc).toEqual({ kind: "card", id: C });
    expect(parseRoute(`/s/${S}/d/subject/${S}`).doc).toEqual({ kind: "subject", id: S });
    expect(parseRoute(`/s/${S}/t/${T}/d/equipment`).doc).toEqual({
      kind: "equipment",
      id: EQUIPMENT_DOC_ID,
    });
    // id 없는 카드 doc은 무시
    expect(parseRoute(`/s/${S}/t/${T}/d/card`).doc).toBeNull();
  });

  test("모르는 doc kind는 무시(전방 호환)", () => {
    expect(parseRoute(`/s/${S}/d/banana/${C}`).doc).toBeNull();
  });

  test("view 쿼리: calendar만 인식, 그 외는 canvas", () => {
    expect(parseRoute(`/s/${S}/t/${T}`, "?view=calendar").view).toBe("calendar");
    expect(parseRoute(`/s/${S}/t/${T}`, "?view=banana").view).toBe("canvas");
    expect(parseRoute(`/s/${S}/t/${T}`).view).toBe("canvas");
  });
});

describe("formatRoute", () => {
  test("주제 없으면 루트", () => {
    expect(formatRoute({ subjectId: null, tabId: null, view: "canvas", doc: null })).toBe("/");
  });

  test("계층 조립 + 기본 view 생략", () => {
    expect(formatRoute({ subjectId: S, tabId: null, view: "canvas", doc: null })).toBe(`/s/${S}`);
    expect(formatRoute({ subjectId: S, tabId: T, view: "canvas", doc: null })).toBe(
      `/s/${S}/t/${T}`,
    );
    expect(
      formatRoute({ subjectId: S, tabId: T, view: "calendar", doc: null }),
    ).toBe(`/s/${S}/t/${T}?view=calendar`);
  });

  test("장비 doc은 id 생략 형태로", () => {
    expect(
      formatRoute({
        subjectId: S,
        tabId: T,
        view: "canvas",
        doc: { kind: "equipment", id: EQUIPMENT_DOC_ID },
      }),
    ).toBe(`/s/${S}/t/${T}/d/equipment`);
  });

  test("parse ↔ format 왕복 보존", () => {
    const cases: Route[] = [
      { subjectId: S, tabId: T, view: "canvas", doc: { kind: "card", id: C } },
      { subjectId: S, tabId: T, view: "calendar", doc: null },
      { subjectId: S, tabId: null, view: "canvas", doc: { kind: "subject", id: S } },
      { subjectId: S, tabId: T, view: "canvas", doc: { kind: "equipment", id: EQUIPMENT_DOC_ID } },
    ];
    for (const r of cases) {
      const url = formatRoute(r);
      const q = url.indexOf("?");
      const parsed = q < 0 ? parseRoute(url) : parseRoute(url.slice(0, q), url.slice(q));
      expect(parsed).toEqual(r);
    }
  });
});
