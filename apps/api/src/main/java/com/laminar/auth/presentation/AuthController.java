package com.laminar.auth.presentation;

import com.laminar.auth.application.AuthService;
import com.laminar.security.LaminarPrincipal;
import com.laminar.user.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * /api/auth/** — signup·login·refresh·logout·me.
 *
 * <p>HTTP 매핑·쿠키·응답 변환만 담당한다. 토큰 발급/rotation/사용자 검증/워크스페이스 생성 등 인증 로직은 {@link AuthService}가 처리한다. 인증
 * 실패는 {@link ResponseStatusException}(401)로 던져 {@code GlobalExceptionHandler}가 표준 envelope로 변환한다(응답
 * 일관성). 토큰: access(JWT, 단명, stateless) + refresh(opaque, 28일) — 둘 다
 * HttpOnly·Secure(prod)·SameSite=Lax 쿠키({@link AuthCookies}).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final AuthCookies authCookies;

  public AuthController(AuthService authService, AuthCookies authCookies) {
    this.authService = authService;
    this.authCookies = authCookies;
  }

  @PostMapping("/signup")
  public AuthDtos.AuthResponse signup(
      @Valid @RequestBody AuthDtos.SignupRequest request, HttpServletResponse response) {
    AuthService.Tokens tokens =
        authService.register(request.email(), request.password(), request.displayName());
    return issued(tokens, response);
  }

  @PostMapping("/login")
  public AuthDtos.AuthResponse login(
      @Valid @RequestBody AuthDtos.LoginRequest request, HttpServletResponse response) {
    AuthService.Tokens tokens =
        authService
            .authenticate(request.email(), request.password())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    return issued(tokens, response);
  }

  @PostMapping("/refresh")
  public AuthDtos.AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
    Optional<AuthService.Tokens> tokens =
        authService.refresh(authCookies.readRefresh(request).orElse(null));
    if (tokens.isEmpty()) {
      authCookies.clearAll(response);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "refresh token invalid");
    }
    return issued(tokens.get(), response);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpServletRequest request, HttpServletResponse response) {
    authService.logout(authCookies.readRefresh(request).orElse(null));
    authCookies.clearAll(response);
    // 잔존 HTTP 세션(JSESSIONID·OAuth 핸드셰이크)도 무효화.
    var httpSession = request.getSession(false);
    if (httpSession != null) {
      httpSession.invalidate();
    }
    SecurityContextHolder.clearContext();
  }

  @GetMapping("/me")
  public AuthDtos.AuthResponse me(@AuthenticationPrincipal LaminarPrincipal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    UserEntity user =
        authService
            .activeUser(principal.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    return toResponse(user);
  }

  /** 토큰쌍을 access/refresh 쿠키로 굽고 사용자 응답을 반환. */
  private AuthDtos.AuthResponse issued(AuthService.Tokens tokens, HttpServletResponse response) {
    authCookies.writeAccess(response, tokens.access());
    authCookies.writeRefresh(response, tokens.refresh());
    return toResponse(tokens.user());
  }

  private AuthDtos.AuthResponse toResponse(UserEntity user) {
    return new AuthDtos.AuthResponse(
        user.getId(), user.getEmail(), user.getDisplayName(), user.getEmailVerifiedAt() != null);
  }
}
