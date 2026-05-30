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
 *   - IllegalStateException    = scope/role/소유권 등 인가 위반 전용 → 403 (N-2 이후 도메인 충돌·
 *                                내부 오류는 ConflictException/RuntimeException으로 분리됨)
 *   - ConflictException        = 도메인 충돌(중복·상태전이) → 409 (안전 메시지 노출)
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
                .orElse("입력값이 유효하지 않습니다");
        return build(HttpStatus.BAD_REQUEST, msg, req);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleBadInput(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "요청 본문이 올바르지 않습니다", req);
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
        return build(HttpStatus.BAD_REQUEST, safe(ex.getMessage(), "잘못된 요청입니다"), req);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest req) {
        // N-2: 인가 위반 전용 → 403. 원본 메시지를 노출하지 않고 일반화한다(화이트리스트: 메시지를
        // 그대로 내보내는 것은 ConflictException뿐). 도메인 충돌/내부 오류가 IllegalStateException으로
        // 새어 들어와도(예: SHA-256 불가, slug 소진) 내부 메시지 누출을 차단. 실제 사유는 서버 로그.
        log.debug("authorization denied at {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "요청을 수행할 권한이 없습니다", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "접근이 거부되었습니다", req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainConflict(
            ConflictException ex, HttpServletRequest req) {
        // N-2: 도메인 충돌(중복·상태전이) → 409. 메시지는 큐레이트된 안전 도메인 사실만 노출.
        return build(HttpStatus.CONFLICT, safe(ex.getMessage(), "리소스 충돌이 발생했습니다"), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            DataIntegrityViolationException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "리소스 충돌이 발생했습니다", req);
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(
            org.springframework.dao.OptimisticLockingFailureException ex, HttpServletRequest req) {
        // M-8: 동시 편집 충돌 → 409. 클라이언트는 최신 상태 재조회 후 재시도.
        return build(HttpStatus.CONFLICT, "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도해 주세요", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("unhandled exception at {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다", req);
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
