package com.laminar.error;

/**
 * 인가 거부(403) — scope/권한 위반의 의도를 타입으로 명시한다(DX-5).
 *
 * <p>기존 관례는 {@code IllegalStateException} → 블랭킷 403이었으나, 그 타입은 진짜 상태 버그(컨텍스트 미설정 등)와 권한 거부를 겸직한다.
 * 신규 인가 거부는 이 타입을 쓸 것. 메시지는 핸들러가 그대로 노출하므로 큐레이트된 문구만 담는다.
 */
public class ForbiddenException extends RuntimeException {

  public ForbiddenException(String message) {
    super(message);
  }
}
