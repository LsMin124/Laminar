package com.laminar.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

/**
 * 전역 예외 핸들러 (H-2) — 도메인/검증 예외를 정확한 HTTP 상태코드 + 표준 envelope로 매핑.
 *
 * 본 코드베이스 관례상:
 *   - IllegalArgumentException = 잘못된 입력/도메인 invariant 위반 → 400
 *   - IllegalStateException    = scope/role/권한·상태 위반 (대부분 인가) → 403
 *   - ResponseStatusException  = 컨트롤러가 명시한 상태 (예: 로그인 실패 401) → passthrough
 *   - 검증(@Valid)             → 400
 *   - DataIntegrityViolation   → 409 (DB unique 등)
 *   - 그 외                    → 500 (메시지 일반화 + 서버 로그)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("validation failed");
        return build(HttpStatus.BAD_REQUEST, msg, req);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleBadInput(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "invalid request body", req);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();
        return build(status, message, req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, safe(ex.getMessage(), "bad request"), req);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, safe(ex.getMessage(), "forbidden"), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "access denied", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            DataIntegrityViolationException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "resource conflict", req);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(
            org.springframework.dao.OptimisticLockingFailureException ex, HttpServletRequest req) {
        // M-8: 동시 편집 충돌 → 409. 클라이언트는 최신 상태 재조회 후 재시도.
        return build(HttpStatus.CONFLICT, "resource was modified concurrently — reload and retry", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("unhandled exception at {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal server error", req);
    }

    private static String safe(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }

    private static ResponseEntity<ApiErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
    }
}
