package com.laminar.error;

/**
 * 기계 판독용 오류 코드 (DX-4) — 프론트가 메시지 문자열 매칭 대신 분기하는 안정 계약.
 *
 * <p>모든 오류에 부여하지 않는다: 프론트가 상태코드만으로 구분할 수 없고 사용자 메시지를 달리 보여줘야 하는 경우에만 추가한다. 코드 문자열은 응답
 * envelope({@link ApiErrorResponse#code()})에 enum 이름 그대로 실린다 — 이름 변경은 프론트 매핑({@code
 * apps/web/src/lib/apiErrors.ts})과 동기 변경할 것.
 */
public enum ErrorCode {
  /** 카드 관계 생성이 사이클을 만들어 차단(409). */
  CARD_CYCLE,
  /** 카드를 선행 카드 시작일보다 앞 날짜로 이동 시도(409, DAG 시간 강제). */
  CARD_BEFORE_PREDECESSOR,
  /** 카드 기간(start~end)이 최대 스팬 초과(400). */
  CARD_SPAN_EXCEEDED
}
