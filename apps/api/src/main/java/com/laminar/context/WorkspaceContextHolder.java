package com.laminar.context;

/**
 * ThreadLocal 기반 WorkspaceContext 보관소.
 *
 * 요청 진입 시 set(), 응답 후 clear() 강제. WebFilter/Interceptor가 책임.
 * 미설정 상태에서 require() 호출하면 명시적 예외 — silent default 절대 금지.
 */
public final class WorkspaceContextHolder {

    private static final ThreadLocal<WorkspaceContext> HOLDER = new ThreadLocal<>();

    private WorkspaceContextHolder() {
    }

    public static void set(WorkspaceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null — use clear() to remove");
        }
        HOLDER.set(context);
    }

    public static WorkspaceContext get() {
        return HOLDER.get();
    }

    public static WorkspaceContext require() {
        WorkspaceContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException(
                    "WorkspaceContext not set — request filter must populate before service call");
        }
        return context;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
