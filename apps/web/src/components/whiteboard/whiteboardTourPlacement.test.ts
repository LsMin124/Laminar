import { describe, expect, test } from "vitest";
import { placeTourPopup } from "./whiteboardTourPlacement";

describe("placeTourPopup", () => {
  test("hole이 없으면 컨테이너 중앙에 배치한다", () => {
    expect(placeTourPopup(null, { w: 280, h: 160 }, { w: 1000, h: 800 })).toEqual({ x: 360, y: 320 });
  });

  test("기본은 스팟라이트 아래(GAP 12px)에 배치한다", () => {
    const pos = placeTourPopup({ x: 100, y: 100, w: 80, h: 30 }, { w: 280, h: 160 }, { w: 1000, h: 800 });
    expect(pos).toEqual({ x: 100, y: 142 });
  });

  test("아래 공간이 부족하면 위로 뒤집는다", () => {
    const pos = placeTourPopup({ x: 100, y: 700, w: 80, h: 30 }, { w: 280, h: 160 }, { w: 1000, h: 800 });
    expect(pos.y).toBe(528);
  });

  test("우측 경계를 넘으면 안쪽으로 클램프한다", () => {
    const pos = placeTourPopup({ x: 950, y: 100, w: 40, h: 30 }, { w: 280, h: 160 }, { w: 1000, h: 800 });
    expect(pos.x).toBe(708);
  });

  test("컨테이너보다 큰 팝업도 좌상단 MARGIN 안쪽에 고정된다", () => {
    const pos = placeTourPopup({ x: 0, y: 0, w: 10, h: 10 }, { w: 400, h: 300 }, { w: 320, h: 200 });
    expect(pos).toEqual({ x: 12, y: 12 });
  });
});
