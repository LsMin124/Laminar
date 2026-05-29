package com.laminar.security;

import com.laminar.system.SessionSystemRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.SessionEntity;
import com.laminar.user.SessionService;
import com.laminar.user.UserEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Cookie의 session_token으로 sessions 테이블 조회 → SecurityContext 설정.
 *
 * Auth.js DB session adapter 호환 — 멤버 제거 시 즉시 revoke 가능 (DB row 삭제).
 * 만료 세션은 무시 (cleanup cron이 hard delete). 토큰 미설정·무효 시 anonymous (다음 필터에서 401).
 *
 * SessionService (Phase 4.2)가 cookie 발급·revoke 책임 — 이 필터는 read-only.
 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "laminar-session";

    private final SessionSystemRepository sessionRepo;
    private final UserSystemRepository userRepo;

    public SessionAuthenticationFilter(
            SessionSystemRepository sessionRepo,
            UserSystemRepository userRepo) {
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            extractToken(request)
                    .map(SessionService::hashToken)
                    .flatMap(sessionRepo::findBySessionToken)
                    .filter(s -> s.getExpiresAt().isAfter(OffsetDateTime.now()))
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private void authenticate(SessionEntity session) {
        Optional<UserEntity> maybeUser = userRepo.findById(session.getUserId());
        if (maybeUser.isEmpty() || maybeUser.get().getDeletedAt() != null) {
            return;
        }
        UserEntity user = maybeUser.get();
        LaminarPrincipal principal = new LaminarPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
