package com.laminar.error;

import java.time.OffsetDateTime;

/**
 * 표준 에러 응답 envelope (공통 규칙: status/error/message). 본문 메시지는 4xx(클라이언트 오류)에만 노출하며 5xx는 일반화 문구만 — 내부
 * 구현/스택 누출 방지.
 *
 * <p>{@code code}는 기계 판독용(DX-4, nullable) — 프론트는 메시지 문자열이 아니라 이 코드로 분기한다({@link ErrorCode}).
 */
public record ApiErrorResponse(
    OffsetDateTime timestamp, int status, String error, String message, String path, String code) {}
