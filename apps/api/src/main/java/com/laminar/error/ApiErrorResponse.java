package com.laminar.error;

import java.time.OffsetDateTime;

/**
 * 표준 에러 응답 envelope (공통 규칙: status/error/message). 본문 메시지는 4xx(클라이언트 오류)에만 노출하며 5xx는 일반화 문구만 — 내부
 * 구현/스택 누출 방지.
 */
public record ApiErrorResponse(
    OffsetDateTime timestamp, int status, String error, String message, String path) {}
