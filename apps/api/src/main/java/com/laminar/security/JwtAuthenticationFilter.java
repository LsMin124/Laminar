package com.laminar.security;

import com.laminar.auth.presentation.AuthCookies;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * access 쿠키(JWT) → 서명·만료 검증 → SecurityContext. Stateless — DB 조회 0.
 *
 * <p>DB 세션 조회 방식의 구(舊) SessionAuthenticationFilter를 대체. 무효·부재 토큰은 anonymous로 통과시켜 다음
 * 단계(authorizeHttpRequests)가 401을 결정한다. access 만료 시 프론트가 /api/auth/refresh로 재발급(refresh 쿠키)한다.
 * soft-delete된 사용자는 access TTL(단명) 동안 토큰이 유효 — refresh·me 시점에 DB로 차단(stateless 트레이드오프).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final AuthCookies authCookies;

  public JwtAuthenticationFilter(JwtService jwtService, AuthCookies authCookies) {
    this.jwtService = jwtService;
    this.authCookies = authCookies;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() == null) {
      authCookies
          .readAccess(request)
          .flatMap(jwtService::verifyAccessToken)
          .ifPresent(this::authenticate);
    }
    chain.doFilter(request, response);
  }

  private void authenticate(LaminarPrincipal principal) {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
