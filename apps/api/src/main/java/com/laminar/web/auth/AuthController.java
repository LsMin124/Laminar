package com.laminar.web.auth;

import com.laminar.security.AuthCookies;
import com.laminar.security.JwtService;
import com.laminar.security.LaminarPrincipal;
import com.laminar.user.SessionService;
import com.laminar.user.UserEntity;
import com.laminar.user.UserService;
import com.laminar.workspace.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * /api/auth/** — signup·login·refresh·logout·me.
 *
 * <p>토큰: access(JWT, 단명, stateless) + refresh(opaque, sessions 테이블 해시, 28일). 둘 다 HttpOnly ·
 * Secure(prod) · SameSite=Lax 쿠키({@link AuthCookies}). access 만료 시 프론트가 /refresh로 재발급(refresh
 * rotation).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserService userService;
  private final SessionService sessionService;
  private final WorkspaceService workspaceService;
  private final JwtService jwtService;
  private final AuthCookies authCookies;

  public AuthController(
      UserService userService,
      SessionService sessionService,
      WorkspaceService workspaceService,
      JwtService jwtService,
      AuthCookies authCookies) {
    this.userService = userService;
    this.sessionService = sessionService;
    this.workspaceService = workspaceService;
    this.jwtService = jwtService;
    this.authCookies = authCookies;
  }

  @PostMapping("/signup")
  public ResponseEntity<AuthDtos.AuthResponse> signup(
      @Valid @RequestBody AuthDtos.SignupRequest request, HttpServletResponse response) {
    UserEntity user =
        userService.signup(request.email(), request.password(), request.displayName());
    workspaceService.createPersonalWorkspace(user.getId(), user.getDisplayName());
    issueTokens(user, response);
    return ResponseEntity.ok(toResponse(user));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthDtos.AuthResponse> login(
      @Valid @RequestBody AuthDtos.LoginRequest request, HttpServletResponse response) {
    UserEntity user =
        userService
            .verifyCredentials(request.email(), request.password())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
    issueTokens(user, response);
    return ResponseEntity.ok(toResponse(user));
  }

  /**
   * refresh 쿠키 → 새 access(+refresh rotation). 무효·만료·비활성 사용자면 401 + 쿠키 삭제. 토큰 탈취 대응으로 사용 시마다 기존
   * refresh를 revoke하고 새로 발급(rotation).
   */
  @PostMapping("/refresh")
  public ResponseEntity<AuthDtos.AuthResponse> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    Optional<String> rawRefresh = authCookies.readRefresh(request);
    Optional<UserEntity> user =
        rawRefresh.flatMap(sessionService::resolveUserId).flatMap(userService::findActive);
    if (user.isEmpty()) {
      authCookies.clearAll(response);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    // rotation: 기존 refresh 폐기 후 새 토큰쌍 발급.
    rawRefresh.ifPresent(sessionService::revoke);
    issueTokens(user.get(), response);
    return ResponseEntity.ok(toResponse(user.get()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    authCookies.readRefresh(request).ifPresent(sessionService::revoke);
    authCookies.clearAll(response);
    // 잔존 HTTP 세션(JSESSIONID·OAuth 핸드셰이크)도 무효화.
    var httpSession = request.getSession(false);
    if (httpSession != null) {
      httpSession.invalidate();
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public ResponseEntity<AuthDtos.AuthResponse> me(Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof LaminarPrincipal principal)) {
      return ResponseEntity.status(401).build();
    }
    return userService
        .findActive(principal.userId())
        .map(this::toResponse)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(401).build());
  }

  private void issueTokens(UserEntity user, HttpServletResponse response) {
    String access =
        jwtService.issueAccessToken(user.getId(), user.getEmail(), user.getDisplayName());
    String refresh = sessionService.issue(user.getId());
    authCookies.writeAccess(response, access);
    authCookies.writeRefresh(response, refresh);
  }

  private AuthDtos.AuthResponse toResponse(UserEntity user) {
    return new AuthDtos.AuthResponse(
        user.getId(), user.getEmail(), user.getDisplayName(), user.getEmailVerifiedAt() != null);
  }
}
