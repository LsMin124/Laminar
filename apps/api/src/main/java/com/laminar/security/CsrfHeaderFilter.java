package com.laminar.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * M-1 CSRF 방어 — custom-header 패턴 강제.
 *
 * <p>쿠키({@link AuthCookies#ACCESS_COOKIE}) 기반 ambient 자격으로 들어오는 상태변경 요청(POST/PUT/PATCH/DELETE)에
 * {@code X-Laminar-CSRF} 헤더를 강제한다. 교차출처 공격 페이지가 커스텀 헤더를 붙이려면 CORS preflight가 필요한데, 본 앱은 CORS 미허용(동일
 * 출처 전용)이라 preflight가 실패 → 위조 요청이 헤더를 달 수 없어 차단된다.
 *
 * <p>{@code SameSite=Lax}(쿠키 속성)와 합쳐 다층 방어를 이룬다. 쿠키가 없는 요청 (로그인·가입, 미래의 서버간 HMAC 호출)은 ambient 자격이
 * 없어 CSRF 대상이 아니므로 면제한다.
 *
 * <p>필터 예외는 @RestControllerAdvice가 잡지 못하므로(DispatcherServlet 이전 실행) 403 JSON을 직접 기록한다 — {@code
 * GlobalExceptionHandler}의 envelope 형태와 정합.
 */
public class CsrfHeaderFilter extends OncePerRequestFilter {

  static final String CSRF_HEADER = "X-Laminar-CSRF";
  private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

  // 본문 자격(이메일·비번·리셋 토큰) 기반이라 ambient 쿠키 인증을 사용하지 않는 엔드포인트 — CSRF 면제.
  // (stale 쿠키가 남아 있으면 CSRF가 강제돼 로그인·가입이 403으로 막히던 footgun 제거.)
  // /refresh·/logout은 refresh 쿠키를 사용하므로 면제하지 않는다.
  private static final Set<String> CSRF_EXEMPT =
      Set.of(
          "/api/auth/login",
          "/api/auth/signup",
          "/api/auth/password-reset/request",
          "/api/auth/password-reset/confirm");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (requiresCsrfHeader(request) && isBlank(request.getHeader(CSRF_HEADER))) {
      reject(request, response);
      return;
    }
    chain.doFilter(request, response);
  }

  /** mutating + /api/** + 세션 쿠키 보유 시에만 CSRF 헤더 요구. */
  private boolean requiresCsrfHeader(HttpServletRequest request) {
    if (!MUTATING.contains(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI();
    if (path == null || !path.startsWith("/api/")) {
      return false;
    }
    if (CSRF_EXEMPT.contains(path)) {
      return false;
    }
    return hasAuthCookie(request);
  }

  private boolean hasAuthCookie(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return false;
    }
    for (Cookie cookie : cookies) {
      String name = cookie.getName();
      if ((AuthCookies.ACCESS_COOKIE.equals(name) || AuthCookies.REFRESH_COOKIE.equals(name))
          && !isBlank(cookie.getValue())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json;charset=UTF-8");
    response
        .getWriter()
        .write(
            "{\"status\":403,\"error\":\"Forbidden\","
                + "\"message\":\"CSRF header required\",\"path\":\""
                + jsonEscape(request.getRequestURI())
                + "\"}");
  }

  private static String jsonEscape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
