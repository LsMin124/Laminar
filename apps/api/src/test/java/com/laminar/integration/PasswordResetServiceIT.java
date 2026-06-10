package com.laminar.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.laminar.auth.application.PasswordResetService;
import com.laminar.system.PasswordResetTokenSystemRepository;
import com.laminar.system.SessionSystemRepository;
import com.laminar.system.UserSystemRepository;
import com.laminar.user.application.SessionService;
import com.laminar.user.application.UserService;
import com.laminar.user.domain.PasswordResetTokenEntity;
import com.laminar.user.domain.UserEntity;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 회귀 — G3: confirm()이 비밀번호 교체와 함께 해당 사용자의 기존 refresh 세션을 전부 폐기하는지.
 *
 * <p>재설정의 전형적 동기가 "탈취 의심"이므로, 공격자가 이미 쥔 refresh(28d)가 재설정 후에도 살아남으면 복구 수단이 복구하지 못한다(리뷰 4차 §7.3 G3).
 */
class PasswordResetServiceIT extends IsolationIntegrationBase {

  @Autowired PasswordResetService passwordResetService;
  @Autowired SessionService sessionService;
  @Autowired UserService userService;
  @Autowired UserSystemRepository userRepo;
  @Autowired SessionSystemRepository sessionRepo;
  @Autowired PasswordResetTokenSystemRepository tokenRepo;

  @Test
  @Transactional
  void confirm_revokes_all_sessions_of_target_user_only() {
    UUID victim = seedUser("reset-victim");
    UUID bystander = seedUser("reset-bystander");
    sessionService.issue(victim); // 정상 기기 세션
    sessionService.issue(victim); // 공격자가 쥔 세션 가정
    sessionService.issue(bystander);

    passwordResetService.confirm(seedToken(victim), "new-password-123");

    assertThat(sessionRepo.findByUserId(victim)).isEmpty();
    assertThat(sessionRepo.findByUserId(bystander)).hasSize(1);
  }

  @Test
  @Transactional
  void confirm_sets_new_password_and_burns_token() {
    UUID userId = seedUser("reset-pw");
    String raw = seedToken(userId);

    passwordResetService.confirm(raw, "new-password-123");

    String email = userRepo.findById(userId).orElseThrow().getEmail();
    assertThat(userService.verifyCredentials(email, "new-password-123")).isPresent();
    // usedAt 단회성 — 같은 토큰 재사용은 거부
    assertThatThrownBy(() -> passwordResetService.confirm(raw, "another-password-456"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private UUID seedUser(String prefix) {
    UserEntity user = new UserEntity();
    user.setEmail(prefix + "-" + UUID.randomUUID() + "@test.local");
    return userRepo.save(user).getId();
  }

  /** request()는 raw 토큰을 메일로만 내보내므로, 서비스와 동일 정책(SHA-256 해시 저장)으로 직접 심는다. */
  private String seedToken(UUID userId) {
    String raw = "test-reset-token-" + UUID.randomUUID();
    PasswordResetTokenEntity token = new PasswordResetTokenEntity();
    token.setUserId(userId);
    token.setTokenHash(SessionService.hashToken(raw));
    token.setExpiresAt(OffsetDateTime.now().plus(60, ChronoUnit.MINUTES));
    tokenRepo.save(token);
    return raw;
  }
}
