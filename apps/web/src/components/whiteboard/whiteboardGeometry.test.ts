import { describe, expect, test } from "vitest";
import { anchorToward, rectsIntersect, unionBounds } from "./whiteboardGeometry";

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
