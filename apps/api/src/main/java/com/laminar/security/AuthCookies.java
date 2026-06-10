package com.laminar.security;

import com.laminar.config.CookieProperties;
import com.laminar.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 인증 쿠키(access/refresh) 발급·삭제·추출 공통 유틸.
 *
 * <p>AuthController·OAuth2LoginSuccessHandler·JwtAuthenticationFilter가 공유 — 쿠키 정책(HttpOnly ·
 * Secure(prod) · SameSite=Lax · Path=/)의 단일 출처. max-age는 JWT TTL(access/refresh)에서 파생. 실소비자 3/4이
 * security 필터(JWT·CSRF·OAuth 핸들러)라 전송 계층 어휘로서 security 소속이다(DX-22).
 */
@Component
public class AuthCookies {

  public static final String ACCESS_COOKIE = "laminar-access";
  public static final String REFRESH_COOKIE = "laminar-refresh";

  private final boolean secure;
  private final int accessMaxAge;
  private final int refreshMaxAge;

  public AuthCookies(CookieProperties cookieProperties, JwtProperties jwtProperties) {
    this.secure = cookieProperties.secure();
    this.accessMaxAge = (int) jwtProperties.accessTtl().toSeconds();
    this.refreshMaxAge = (int) jwtProperties.refreshTtl().toSeconds();
  }

  public void writeAccess(HttpServletResponse response, String token) {
    response.addCookie(build(ACCESS_COOKIE, token, accessMaxAge));
  }

  public void writeRefresh(HttpServletResponse response, String token) {
    response.addCookie(build(REFRESH_COOKIE, token, refreshMaxAge));
  }

  /** access·refresh 쿠키를 모두 즉시 만료 (로그아웃·refresh 실패 시). */
  public void clearAll(HttpServletResponse response) {
    response.addCookie(build(ACCESS_COOKIE, "", 0));
    response.addCookie(build(REFRESH_COOKIE, "", 0));
  }

  public Optional<String> readAccess(HttpServletRequest request) {
    return read(request, ACCESS_COOKIE);
  }

  public Optional<String> readRefresh(HttpServletRequest request) {
    return read(request, REFRESH_COOKIE);
  }

  private Cookie build(String name, String value, int maxAge) {
    Cookie cookie = new Cookie(name, value);
    cookie.setHttpOnly(true);
    cookie.setSecure(secure);
    cookie.setPath("/");
    cookie.setMaxAge(maxAge);
    cookie.setAttribute("SameSite", "Lax");
    return cookie;
  }

  private Optional<String> read(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        String value = cookie.getValue();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
      }
    }
    return Optional.empty();
  }
}
