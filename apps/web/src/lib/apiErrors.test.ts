import { describe, expect, test } from "vitest";
import { ApiError } from "./api";
import { apiErrorMessage } from "./apiErrors";
import { MAX_SPAN_DAYS } from "./cardRules";

const FALLBACK = "기본 안내";

function apiErr(status: number, body: unknown): ApiError {
  return new ApiError(status, body, `HTTP ${status}`);
}

describe("apiErrorMessage", () => {
  test("ApiError가 아니면 fallback을 반환한다", () => {
    expect(apiErrorMessage(new Error("boom"), FALLBACK)).toBe(FALLBACK);
    expect(apiErrorMessage(undefined, FALLBACK)).toBe(FALLBACK);
  });

  test("코드 매핑: CARD_CYCLE → 순환 차단 안내", () => {
    expect(apiErrorMessage(apiErr(409, { code: "CARD_CYCLE" }), FALLBACK)).toContain("순환");
  });

  test("코드 매핑: CARD_SPAN_EXCEEDED 문구는 cardRules 한도와 동기", () => {
    expect(apiErrorMessage(apiErr(400, { code: "CARD_SPAN_EXCEEDED" }), FALLBACK)).toContain(
      `${MAX_SPAN_DAYS}일`,
    );
  });

  test("표면별 override가 공통 매핑보다 우선한다", () => {
    const msg = apiErrorMessage(apiErr(409, { code: "CARD_CYCLE" }), FALLBACK, {
      CARD_CYCLE: "캘린더 전용 안내",
    });
    expect(msg).toBe("캘린더 전용 안내");
  });

  test("모르는 코드 + 409는 일반 충돌 문구", () => {
    expect(apiErrorMessage(apiErr(409, { code: "SOMETHING_NEW" }), FALLBACK)).toBe(
      "충돌이 발생했습니다.",
    );
  });

  test("코드 없음 + 비409는 fallback (문자열 body 포함)", () => {
    expect(apiErrorMessage(apiErr(400, { message: "bad" }), FALLBACK)).toBe(FALLBACK);
    expect(apiErrorMessage(apiErr(500, "internal"), FALLBACK)).toBe(FALLBACK);
  });
});
