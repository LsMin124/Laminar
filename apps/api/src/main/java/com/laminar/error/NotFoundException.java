package com.laminar.error;

/**
 * 리소스 부재(404) — "없음"의 의도를 타입으로 명시한다(DX-5).
 *
 * <p>기존 관례는 {@code IllegalArgumentException("x not found")} → 블랭킷 400이었다. 신규 코드는 이 타입을 쓸 것. 격리 관점에서
 * "남의 리소스"도 존재를 노출하지 않기 위해 404로 응답할 때 사용한다. 기존 47곳의 전환은 소유 조회 헬퍼 통합(DX-1②)과 함께 일괄 진행 예정.
 */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
