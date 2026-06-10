import { describe, expect, test } from "vitest";
import { MS_DAY, fmtDate, parseDate, shortDate, startOfMonth, todayUtc } from "./dateUtil";

describe("dateUtil", () => {
  test("parseDate ↔ fmtDate 왕복", () => {
    expect(parseDate("2026-06-10")).toBe(Date.UTC(2026, 5, 10));
    expect(fmtDate(parseDate("2026-06-10"))).toBe("2026-06-10");
    expect(fmtDate(parseDate("2026-01-01"))).toBe("2026-01-01");
  });

  test("하루 차이는 정확히 MS_DAY", () => {
    expect(parseDate("2026-06-11") - parseDate("2026-06-10")).toBe(MS_DAY);
  });

  test("월말 경계: 2월 말 다음날 (평년·윤년)", () => {
    expect(fmtDate(parseDate("2026-02-28") + MS_DAY)).toBe("2026-03-01");
    expect(fmtDate(parseDate("2024-02-28") + MS_DAY)).toBe("2024-02-29");
  });

  test("startOfMonth는 해당 월 1일 UTC 자정", () => {
    expect(startOfMonth(parseDate("2026-06-15"))).toBe(parseDate("2026-06-01"));
    expect(startOfMonth(parseDate("2026-06-01"))).toBe(parseDate("2026-06-01"));
  });

  test("shortDate는 0 패딩 제거 M/D", () => {
    expect(shortDate("2026-06-04")).toBe("6/4");
    expect(shortDate("2026-12-25")).toBe("12/25");
  });

  test("todayUtc는 UTC 자정 정렬(MS_DAY 배수)", () => {
    expect(todayUtc() % MS_DAY).toBe(0);
  });
});
