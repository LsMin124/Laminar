package com.laminar.error;

/**
 * 입력/도메인 규칙 위반(400) 중 프론트가 기계 판독할 코드가 필요한 경우(DX-4).
 *
 * <p>일반 검증 실패는 {@code IllegalArgumentException}(블랭킷 400) 관례를 유지한다 — 이 타입은 프론트가 코드로 분기해 사용자 메시지를 달리
 * 보여줘야 하는 규칙 위반에만 쓴다(예: 카드 최대 스팬 초과).
 *
 * <p>메시지는 클라이언트에 그대로 노출되므로 민감정보를 담지 말 것(도메인 사실만).
 */
public class BadRequestException extends RuntimeException {

  private final ErrorCode code;

  public BadRequestException(String message, ErrorCode code) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
