package com.laminar.context;

import com.laminar.error.ForbiddenException;

/**
 * ThreadLocal 기반 SubjectContext 보관소.
 *
 * <p>요청 진입 시 set(), 응답 후 clear() 강제. WebFilter/Interceptor가 책임. 미설정 상태에서 require() 호출하면 명시적 예외 —
 * silent default 절대 금지.
 */
public final class SubjectContextHolder {

  private static final ThreadLocal<SubjectContext> HOLDER = new ThreadLocal<>();

  private SubjectContextHolder() {}

  public static void set(SubjectContext context) {
    if (context == null) {
      throw new IllegalArgumentException("context must not be null — use clear() to remove");
    }
    HOLDER.set(context);
  }

  public static SubjectContext get() {
    return HOLDER.get();
  }

  public static SubjectContext require() {
    SubjectContext context = HOLDER.get();
    if (context == null) {
      throw new IllegalStateException(
          "SubjectContext not set — request filter must populate before service call");
    }
    return context;
  }

  /**
   * Personal-First 리소스 접근 시 PERSONAL scope를 강제한다 (subject 헤더 누락 = SYSTEM 차단). SYSTEM/SUBJECT_SHARED
   * scope로 Personal-First 서비스 진입을 막아 fail-closed 보장.
   */
  public static SubjectContext requirePersonal() {
    SubjectContext context = require();
    if (context.scope() != SubjectContext.Scope.PERSONAL) {
      throw new IllegalStateException("PERSONAL subject scope required for this resource");
    }
    return context;
  }

  /**
   * 쓰기 경로 공통 가드 — PERSONAL scope + 쓰기 권한(VIEWER 차단)을 한 번에 강제한다. 도메인 서비스의 쓰기 메서드 첫 줄에서 호출한다.
   *
   * <p>DX-1①: 과거 10개 서비스가 동일 가드를 private 메서드로 중복 정의하던 것을 통합 — 신규 도메인이 베껴 쓸 단일 정본이며, 정책 변경(예: 권한 단계
   * 추가)도 이 한 곳에서 끝난다.
   *
   * @param resourceNoun VIEWER 거부 메시지에 들어갈 리소스 명사(예: "cards", "groups")
   */
  public static SubjectContext requirePersonalWritable(String resourceNoun) {
    SubjectContext context = require();
    // DX-5: 의도적 인가 거부는 ForbiddenException(403 명시 타입) — 메시지는 기존 가드 문구 보존.
    if (context.scope() != SubjectContext.Scope.PERSONAL) {
      throw new ForbiddenException("PERSONAL scope required");
    }
    if (!context.canWrite()) {
      throw new ForbiddenException("VIEWER cannot mutate " + resourceNoun);
    }
    return context;
  }

  public static void clear() {
    HOLDER.remove();
  }
}
