package com.laminar.security;

import com.laminar.config.CookieProperties;
import com.laminar.user.SessionService;
import com.laminar.user.UserEntity;
import com.laminar.user.UserService;
import com.laminar.workspace.WorkspaceService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Google OAuth2 로그인 성공 핸들러.
 *
 * <p>OAuth 인증 성공 후: 이메일로 사용자 find-or-create(신규는 개인 워크스페이스 생성) → 기존 세션 메커니즘({@link
 * SessionService})으로 토큰 발급 → {@code laminar-session} 쿠키 설정 → SPA 루트로 리다이렉트. 비밀번호 로그인/가입과 **동일한
 * 세션·쿠키**를 써서 인증 경로를 통일한다.
 *
 * <p>OAuth 핸드셰이크용 HTTP 세션(JSESSIONID)은 쿠키 발급 직후 invalidate해, 이후 요청이 {@code laminar-session}
 * 쿠키(SOR)로만 인증되도록 한다(OAuth2 SecurityContext 잔존 차단).
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final UserService userService;
  private final WorkspaceService workspaceService;
  private final SessionService sessionService;
  private final CookieProperties cookieProperties;

  public OAuth2LoginSuccessHandler(
      UserService userService,
      WorkspaceService workspaceService,
      SessionService sessionService,
      CookieProperties cookieProperties) {
    this.userService = userService;
    this.workspaceService = workspaceService;
    this.sessionService = sessionService;
    this.cookieProperties = cookieProperties;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    OAuth2User principal = (OAuth2User) authentication.getPrincipal();
    String email = principal.getAttribute("email");
    if (email == null || email.isBlank()) {
      response.sendRedirect("/?error=oauth_no_email");
      return;
    }
    // email_verified가 명시적으로 true일 때만 신뢰 — 미검증 이메일이 기존
    // 비밀번호 계정에 자동 연결되어 탈취되는 것을 차단(N-6).
    Object verified = principal.getAttribute("email_verified");
    boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
    if (!emailVerified) {
      response.sendRedirect("/?error=oauth_email_unverified");
      return;
    }
    String name = principal.getAttribute("name");

    UserEntity user = userService.findOrCreateOAuthUser(email, name);
    if (workspaceService.listForUser(user.getId()).isEmpty()) {
      workspaceService.createPersonalWorkspace(user.getId(), user.getDisplayName());
    }
    String token = sessionService.issue(user.getId());
    writeSessionCookie(response, token);

    // OAuth 핸드셰이크용 HTTP 세션 제거 — 이후엔 laminar-session 쿠키만 사용.
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    response.sendRedirect("/");
  }

  private void writeSessionCookie(HttpServletResponse response, String token) {
    Cookie cookie = new Cookie(SessionAuthenticationFilter.COOKIE_NAME, token);
    cookie.setHttpOnly(true);
    cookie.setSecure(cookieProperties.secure());
    cookie.setPath("/");
    cookie.setMaxAge(28 * 24 * 60 * 60);
    cookie.setAttribute("SameSite", "Lax");
    response.addCookie(cookie);
  }
}
