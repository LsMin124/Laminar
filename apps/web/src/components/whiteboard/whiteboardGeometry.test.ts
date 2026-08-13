import { describe, expect, test } from "vitest";
import {
  anchorToward,
  edgeCurve,
  rectsIntersect,
  sideAnchor,
  sideToward,
  unionBounds,
} from "./whiteboardGeometry";

describe("rectsIntersect", () => {
  test("겹치면 true, 떨어져 있으면 false", () => {
    expect(rectsIntersect({ x: 0, y: 0, w: 10, h: 10 }, { x: 5, y: 5, w: 10, h: 10 })).toBe(true);
    expect(rectsIntersect({ x: 0, y: 0, w: 10, h: 10 }, { x: 20, y: 0, w: 5, h: 5 })).toBe(false);
  });

  test("경계 접촉도 교차로 본다", () => {
    expect(rectsIntersect({ x: 0, y: 0, w: 10, h: 10 }, { x: 10, y: 0, w: 5, h: 5 })).toBe(true);
  });
});

describe("unionBounds", () => {
  test("빈 목록이면 null", () => {
    expect(unionBounds([])).toBeNull();
  });

  test("여러 사각형의 합집합 경계", () => {
    expect(
      unionBounds([
        { x: 0, y: 0, w: 10, h: 10 },
        { x: 20, y: 5, w: 10, h: 10 },
      ]),
    ).toEqual({ x: 0, y: 0, w: 30, h: 15 });
  });
});

describe("anchorToward", () => {
  test("오른쪽 대상이면 오른쪽 변 중앙에서 나간다", () => {
    expect(anchorToward({ x: 0, y: 0, w: 10, h: 10 }, 100, 5)).toEqual({ x: 10, y: 5 });
  });
});

describe("sideToward · sideAnchor (WB-D)", () => {
  const r = { x: 0, y: 0, w: 100, h: 50 };
  test("수평 우세면 left/right, 수직 우세면 top/bottom", () => {
    expect(sideToward(r, 300, 25)).toBe("right");
    expect(sideToward(r, -300, 25)).toBe("left");
    expect(sideToward(r, 50, 300)).toBe("bottom");
    expect(sideToward(r, 50, -300)).toBe("top");
  });
  test("변 중점 좌표", () => {
    expect(sideAnchor(r, "right")).toEqual({ x: 100, y: 25 });
    expect(sideAnchor(r, "top")).toEqual({ x: 50, y: 0 });
  });
});

describe("edgeCurve (WB-D)", () => {
  test("수평 배치 — 오른쪽 변에서 나가 왼쪽 변으로 들어오는 베지어", () => {
    const c = edgeCurve({ x: 0, y: 0, w: 100, h: 50 }, { x: 300, y: 0, w: 100, h: 50 });
    expect(c.a).toEqual({ x: 100, y: 25 });
    expect(c.b).toEqual({ x: 300, y: 25 });
    expect(c.d.startsWith("M 100 25 C ")).toBe(true);
    // 대칭 배치라 라벨은 두 앵커의 정중앙.
    expect(c.label).toEqual({ x: 200, y: 25 });
  });
});
