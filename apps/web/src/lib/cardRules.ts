/**
 * 카드 도메인 규칙 상수 — 화면(geometry)과 오류 메시지(apiErrors)가 공유한다.
 *
 * ⚠ 백엔드 `CardService.MAX_SPAN_DAYS`(검증 정본)와 동기 유지할 것 — 백엔드가 한도를 바꾸면
 * 여기도 같이 바꿔야 안내 문구·리사이즈 클램프가 거짓말하지 않는다(DX-17, ErrorCode와 같은 계약 노트).
 */
export const MAX_SPAN_DAYS = 30;
