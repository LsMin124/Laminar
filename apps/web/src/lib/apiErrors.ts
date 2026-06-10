/**
 * 백엔드 오류 code → 사용자 메시지 매핑 (DX-4).
 *
 * 백엔드 ApiErrorResponse.code(ErrorCode enum 이름)로 분기한다 — 과거의 영문 메시지 부분 문자열
 * 매칭(`m.includes("cycle")` 등, DagCanvas·CalendarView 복붙)은 백엔드가 문구만 다듬어도 조용히
 * 깨지는 계약이라 제거했다. 코드 목록은 백엔드 `com.laminar.error.ErrorCode`와 동기 유지할 것.
 */
import { ApiError } from "./api";
import { MAX_SPAN_DAYS } from "../components/dag/dagGeometry";

const CODE_MESSAGES: Record<string, string> = {
  CARD_CYCLE: "두 카드를 연결하면 순환이 생겨 차단되었습니다.",
  CARD_BEFORE_PREDECESSOR: "선행 카드보다 앞 날짜로 옮길 수 없습니다.",
  CARD_SPAN_EXCEEDED: `기간은 최대 ${MAX_SPAN_DAYS}일까지입니다.`,
};

/**
 * ApiError → 사용자 메시지. 우선순위: 표면별 override > 공통 코드 매핑 > 409 일반 충돌 문구 > fallback.
 * overrides는 같은 코드라도 표면별 안내가 달라야 할 때(예: 캘린더에는 "캔버스에서 화살표를 끊으세요" 힌트).
 */
export function apiErrorMessage(
  err: unknown,
  fallback: string,
  overrides?: Record<string, string>,
): string {
  if (!(err instanceof ApiError)) return fallback;
  const body = err.body as { code?: string | null } | string | null;
  const code = typeof body === "object" && body?.code ? body.code : null;
  if (code) {
    const msg = overrides?.[code] ?? CODE_MESSAGES[code];
    if (msg) return msg;
  }
  if (err.status === 409) return "충돌이 발생했습니다.";
  return fallback;
}
