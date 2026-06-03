package com.laminar.auth.application;

import com.laminar.security.JwtService;
import com.laminar.subject.application.SubjectService;
import com.laminar.user.application.SessionService;
import com.laminar.user.application.UserService;
import com.laminar.user.domain.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 오케스트레이션 — 가입·로그인·refresh rotation·OAuth 로그인의 토큰 발급 흐름을 캡슐화.
 *
 * <p>AuthController(HTTP)와 OAuth2LoginSuccessHandler가 공유한다. 컨트롤러/핸들러는 HTTP 매핑·쿠키만 맡고, 토큰 발급
 * 조합(access JWT + refresh opaque)·refresh rotation·사용자 검증·개인 워크스페이스 생성은 본 서비스가 단일 출처로 처리한다.
 */
@Service
public class AuthService {

  private final UserService userService;
  private final SessionService sessionService;
  private final SubjectService subjectService;
  private final JwtService jwtService;

  public AuthService(
      UserService userService,
      SessionService sessionService,
      SubjectService subjectService,
      JwtService jwtService) {
    this.userService = userService;
    this.sessionService = sessionService;
    this.subjectService = subjectService;
    this.jwtService = jwtService;
  }

  /** 발급된 토큰쌍 + 대상 사용자. 컨트롤러가 쿠키·응답으로 변환. */
  public record Tokens(String access, String refresh, UserEntity user) {}

  /** 이메일 가입 → 개인 워크스페이스 생성 → 토큰 발급. */
  @Transactional
  public Tokens register(String email, String password, String displayName) {
    UserEntity user = userService.signup(email, password, displayName);
    subjectService.createPersonalSubject(user.getId(), user.getDisplayName());
    return issueFor(user);
  }

  /** 이메일·비밀번호 검증 후 토큰 발급. 자격 불일치 시 빈 Optional(컨트롤러가 401). */
  @Transactional
  public Optional<Tokens> authenticate(String email, String password) {
    return userService.verifyCredentials(email, password).map(this::issueFor);
  }

  /** refresh 토큰 검증 → rotation(기존 폐기 + 재발급). 무효·만료·비활성 사용자면 빈 Optional. */
  @Transactional
  public Optional<Tokens> refresh(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      return Optional.empty();
    }
    Optional<UserEntity> user =
        sessionService.resolveUserId(rawRefreshToken).flatMap(userService::findActive);
    if (user.isEmpty()) {
      return Optional.empty();
    }
    sessionService.revoke(rawRefreshToken);
    return Optional.of(issueFor(user.get()));
  }

  /** refresh 토큰 폐기(로그아웃). null/blank는 무시. */
  @Transactional
  public void logout(String rawRefreshToken) {
    if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
      sessionService.revoke(rawRefreshToken);
    }
  }

  /** OAuth(Google) 로그인 — 검증된 email/name으로 find-or-create + 워크스페이스 보장 + 토큰 발급. */
  @Transactional
  public Tokens oauthLogin(String email, String displayName) {
    UserEntity user = userService.findOrCreateOAuthUser(email, displayName);
    if (subjectService.listForUser(user.getId()).isEmpty()) {
      subjectService.createPersonalSubject(user.getId(), user.getDisplayName());
    }
    return issueFor(user);
  }

  /** 현재 인증 사용자(me) — 활성 사용자만. */
  @Transactional(readOnly = true)
  public Optional<UserEntity> activeUser(UUID userId) {
    return userService.findActive(userId);
  }

  /** access(JWT) + refresh(opaque) 발급 — 모든 인증 진입점의 공통 토큰 발급. */
  private Tokens issueFor(UserEntity user) {
    String access =
        jwtService.issueAccessToken(user.getId(), user.getEmail(), user.getDisplayName());
    String refresh = sessionService.issue(user.getId());
    return new Tokens(access, refresh, user);
  }
}
