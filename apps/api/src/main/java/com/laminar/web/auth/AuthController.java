package com.laminar.web.auth;

import com.laminar.security.LaminarPrincipal;
import com.laminar.security.SessionAuthenticationFilter;
import com.laminar.user.SessionService;
import com.laminar.user.UserEntity;
import com.laminar.user.UserService;
import com.laminar.workspace.WorkspaceService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import com.laminar.config.CookieProperties;
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

import java.util.Optional;

/**
 * /api/auth/** 엔드포인트 — signup·login·logout·me.
 *
 * Cookie 정책:
 *   - HttpOnly + SameSite=Lax + Path=/
 *   - Secure: app.cookie.secure 프로파일 (prod=true, local=false)
 *   - Max-Age: 28일 (SessionService.DEFAULT_TTL_DAYS와 일치)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final SessionService sessionService;
    private final WorkspaceService workspaceService;
    private final CookieProperties cookieProperties;

    public AuthController(
            UserService userService,
            SessionService sessionService,
            WorkspaceService workspaceService,
            CookieProperties cookieProperties) {
        this.userService = userService;
        this.sessionService = sessionService;
        this.workspaceService = workspaceService;
        this.cookieProperties = cookieProperties;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthDtos.AuthResponse> signup(
            @Valid @RequestBody AuthDtos.SignupRequest request,
            HttpServletResponse response) {
        UserEntity user = userService.signup(request.email(), request.password(), request.displayName());
        workspaceService.createPersonalWorkspace(user.getId(), user.getDisplayName());
        String token = sessionService.issue(user.getId());
        writeSessionCookie(response, token);
        return ResponseEntity.ok(toResponse(user));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request,
            HttpServletResponse response) {
        UserEntity user = userService.verifyCredentials(request.email(), request.password())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
        String token = sessionService.issue(user.getId());
        writeSessionCookie(response, token);
        return ResponseEntity.ok(toResponse(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        extractSessionCookie(request).ifPresent(sessionService::revoke);
        clearSessionCookie(response);
        // 잔존 HTTP 세션(JSESSIONID)도 무효화 — 세션에 영속된 인증이 로그아웃 후 남지 않도록.
        var httpSession = request.getSession(false);
        if (httpSession != null) {
            httpSession.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDtos.AuthResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LaminarPrincipal principal)) {
            return ResponseEntity.status(401).build();
        }
        return userService.findActive(principal.userId())
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    private AuthDtos.AuthResponse toResponse(UserEntity user) {
        return new AuthDtos.AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getEmailVerifiedAt() != null);
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

    private void clearSessionCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(SessionAuthenticationFilter.COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieProperties.secure());
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    private Optional<String> extractSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie c : cookies) {
            if (SessionAuthenticationFilter.COOKIE_NAME.equals(c.getName())) {
                return Optional.ofNullable(c.getValue());
            }
        }
        return Optional.empty();
    }
}
