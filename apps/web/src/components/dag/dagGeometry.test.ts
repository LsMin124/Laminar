import { describe, expect, test } from "vitest";
import type { Card } from "../../lib/graphTypes";
import { BACKLOG_W, EDGE_STUB, PX_PER_DAY, barWidth, cardMeta, edgePath } from "./dagGeometry";

function card(over: Partial<Card>): Card {
  return {
    id: "c1",
    tabId: "t1",
    title: "카드",
    bodyMd: null,
    bodyExcerpt: null,
    startDate: null,
    endDate: null,
    startTime: null,
    allDay: true,
    importance: "NORMAL",
    completed: false,
    priority: 100,
    canvasY: 0,
    ...over,
  };
}

describe("edgePath", () => {
  test("같은 행이면 곧은 수평선", () => {
    expect(edgePath(0, 50, 200, 50)).toBe("M 0 50 H 200");
  });

  test("전방 여유가 충분하면 중간 x 대칭 엘보", () => {
    expect(edgePath(0, 0, 100, 80)).toBe("M 0 0 H 50 V 80 H 100");
  });

  test("겹침/인접이면 스텁으로 빠져나와 행 사이 레인 경유 — 화살표는 항상 우향 진입", () => {
    expect(edgePath(100, 0, 90, 80)).toBe(
      `M 100 0 H ${100 + EDGE_STUB} V 40 H ${90 - EDGE_STUB} V 80 H 90`,
    );
  });
});

describe("cardMeta", () => {
  test("날짜 없으면 미정", () => {
    expect(cardMeta(card({}))).toBe("미정");
  });

  test("단일일 → M/D", () => {
    expect(cardMeta(card({ startDate: "2026-06-04" }))).toBe("6/4");
  });

  test("멀티데이 → 범위 표기, 동일 시·종료일은 범위 미표기", () => {
    expect(cardMeta(card({ startDate: "2026-06-04", endDate: "2026-06-09" }))).toBe("6/4–6/9");
    expect(cardMeta(card({ startDate: "2026-06-04", endDate: "2026-06-04" }))).toBe("6/4");
  });

  test("시간 지정(allDay=false) → HH:mm 부착", () => {
    expect(cardMeta(card({ startDate: "2026-06-04", allDay: false, startTime: "14:00:00" }))).toBe(
      "6/4 14:00",
    );
  });
});

describe("barWidth", () => {
  test("날짜 미정 → 백로그 폭", () => {
    expect(barWidth(card({}))).toBe(BACKLOG_W);
  });

  test("단일일 → 1일 폭", () => {
    expect(barWidth(card({ startDate: "2026-06-04" }))).toBe(PX_PER_DAY);
  });

  test("멀티데이 → 일수×PX_PER_DAY (양끝 포함)", () => {
    expect(barWidth(card({ startDate: "2026-06-04", endDate: "2026-06-06" }))).toBe(
      3 * PX_PER_DAY,
    );
  });
});
