import { describe, expect, test } from "vitest";
import { DEFAULT_COLOR, paletteEntry, shapeOf, WB_PALETTE } from "./whiteboardPalette";

describe("paletteEntry", () => {
  test("알려진 색 id는 해당 항목을 반환한다", () => {
    expect(paletteEntry("orange", "STICKY").fill).toBe("#d97757");
  });

  test("모르는 id·비문자열은 kind 기본색으로 폴백한다", () => {
    expect(paletteEntry("nope", "STICKY").id).toBe(DEFAULT_COLOR.STICKY);
    expect(paletteEntry(undefined, "SHAPE").id).toBe(DEFAULT_COLOR.SHAPE);
  });

  test("색 개념이 없는 kind는 gray로 폴백한다", () => {
    expect(paletteEntry(123, "MD").id).toBe("gray");
  });

  test("팔레트 id는 중복이 없다", () => {
    expect(new Set(WB_PALETTE.map((p) => p.id)).size).toBe(WB_PALETTE.length);
  });
});

describe("shapeOf", () => {
  test("ellipse·diamond는 그대로, 그 외는 rect", () => {
    expect(shapeOf({ shape: "ellipse" })).toBe("ellipse");
    expect(shapeOf({ shape: "diamond" })).toBe("diamond");
    expect(shapeOf({ shape: "star" })).toBe("rect");
    expect(shapeOf({})).toBe("rect");
  });
});
