package com.laminar.auth.application;

import com.laminar.config.MailProperties;
import com.laminar.notify.ResendEmailSender;
import com.laminar.system.PasswordResetTokenSystemRepository;
import com.laminar.user.application.SessionService;
import com.laminar.user.application.UserService;
import com.laminar.user.domain.PasswordResetTokenEntity;
import com.laminar.user.domain.UserEntity;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 — 요청(토큰 발급 + 메일)·확인(토큰 검증 + 비번 설정).
 *
 * <p>토큰: 256-bit SecureRandom, DB엔 SHA-256 해시만(세션 정책 재사용). 1시간 만료·1회용. 요청은 계정 존재 여부와 무관하게 동일 응답
 * (enumeration 차단) — 미존재면 조용히 no-op. 확인 실패(무효·만료·사용됨)는 IllegalArgumentException→400.
 */
@Service
public class PasswordResetService {

  private static final int TOKEN_BYTES = 32;
  private static final long TTL_MINUTES = 60;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserService userService;
  private final PasswordResetTokenSystemRepository tokenRepo;
  private final ResendEmailSender mailSender;
  private final MailProperties mailProps;

  public PasswordResetService(
      UserService userService,
      PasswordResetTokenSystemRepository tokenRepo,
      ResendEmailSender mailSender,
      MailProperties mailProps) {
    this.userService = userService;
    this.tokenRepo = tokenRepo;
    this.mailSender = mailSender;
    this.mailProps = mailProps;
  }

  @Transactional
  public void request(String email) {
    Optional<UserEntity> user = userService.findActiveByEmail(email);
    if (user.isEmpty()) {
      return; // anti-enumeration: 항상 동일 응답
    }
    String raw = generateToken();
    PasswordResetTokenEntity token = new PasswordResetTokenEntity();
    token.setUserId(user.get().getId());
    token.setTokenHash(SessionService.hashToken(raw));
    token.setExpiresAt(OffsetDateTime.now().plus(TTL_MINUTES, ChronoUnit.MINUTES));
    tokenRepo.save(token);

    String url = trimTrailingSlash(mailProps.resetBaseUrl()) + "/reset?token=" + raw;
    mailSender.sendPasswordReset(user.get().getEmail(), url);
  }

  @Transactional
  public void confirm(String rawToken, String newPassword) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("유효하지 않은 링크입니다.");
    }
    PasswordResetTokenEntity token =
        tokenRepo
            .findByTokenHash(SessionService.hashToken(rawToken))
            .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(OffsetDateTime.now()))
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 링크입니다."));

    userService.setPassword(token.getUserId(), newPassword);
    token.setUsedAt(OffsetDateTime.now());
    tokenRepo.save(token);
  }

  private static String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String trimTrailingSlash(String url) {
    if (url == null || url.isBlank()) {
      return "";
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
