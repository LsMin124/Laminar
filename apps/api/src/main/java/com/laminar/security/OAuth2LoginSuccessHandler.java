package com.laminar.security;

import jakarta.servlet.ServletException;
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
 * <p>OAuth 응답에서 검증된 이메일을 꺼내 {@link AuthService#oauthLogin}으로 위임(find-or-create + 워크스페이스 보장 + 토큰
 * 발급)하고, 비밀번호 경로와 동일한 JWT 쿠키({@link AuthCookies})를 구운 뒤 SPA 루트로 리다이렉트한다.
 *
 * <p>OAuth 핸드셰이크용 HTTP 세션(JSESSIONID)은 쿠키 발급 직후 invalidate해 이후 요청이 JWT 쿠키로만 인증되도록 한다(OAuth2
 * SecurityContext 잔존 차단).
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final AuthService authService;
  private final AuthCookies authCookies;

  public OAuth2LoginSuccessHandler(AuthService authService, AuthCookies authCookies) {
    this.authService = authService;
    this.authCookies = authCookies;
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

    AuthService.Tokens tokens = authService.oauthLogin(email, name);
    authCookies.writeAccess(response, tokens.access());
    authCookies.writeRefresh(response, tokens.refresh());

    // OAuth 핸드셰이크용 HTTP 세션 제거 — 이후엔 JWT 쿠키만 사용.
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    response.sendRedirect("/");
  }
}
