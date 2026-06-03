package com.laminar.error;

/**
 * 도메인 충돌(중복 등록·예약 겹침·상태 전이 위반) — HTTP 409로 매핑 (N-2).
 *
 * <p>이전에는 이런 충돌이 {@code IllegalStateException}으로 던져져 {@code GlobalExceptionHandler}의 블랭킷 403(인가
 * 관례)로 잘못 매핑되었다. 그 결과 (1) 가입 중복이 403으로 노출되어 enumeration이 가능했고 (2) 인가와 무관한 상태 충돌이 403 + 원본 메시지로 새어
 * 나갔다. 충돌 전용 타입으로 분리해 정확한 409 + 큐레이트된 안전 메시지를 반환한다.
 *
 * <p>메시지는 클라이언트에 그대로 노출되므로 민감정보를 담지 말 것(도메인 사실만).
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
