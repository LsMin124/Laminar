package com.laminar.context;

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

  public static void clear() {
    HOLDER.remove();
  }
}
