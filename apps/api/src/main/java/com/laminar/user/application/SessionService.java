package com.laminar.user.application;

import com.laminar.system.SessionSystemRepository;
import com.laminar.user.domain.SessionEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 발급·revoke·정리.
 *
 * <p>토큰: 256-bit SecureRandom + base64url. sessions 테이블이 SOR (멤버 제거 즉시 revoke). Cookie 발급은
 * AuthController 책임 — 본 서비스는 토큰만 반환.
 */
@Service
public class SessionService {

  static final int DEFAULT_TTL_DAYS = 28;
  private static final int TOKEN_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SessionSystemRepository sessionRepo;

  public SessionService(SessionSystemRepository sessionRepo) {
    this.sessionRepo = sessionRepo;
  }

  /**
   * 세션 발급 — 쿠키에 담을 raw 토큰을 반환하고, DB에는 SHA-256(token)만 저장한다 (M-2). DB 유출 시에도 활성 세션 토큰을 역산할 수 없음 (초대
   * 토큰과 동일 정책).
   */
  @Transactional
  public String issue(UUID userId) {
    return issue(userId, DEFAULT_TTL_DAYS);
  }

  @Transactional
  public String issue(UUID userId, int ttlDays) {
    String rawToken = generateToken();
    SessionEntity session = new SessionEntity();
    session.setUserId(userId);
    session.setSessionToken(hashToken(rawToken));
    session.setExpiresAt(OffsetDateTime.now().plus(ttlDays, ChronoUnit.DAYS));
    sessionRepo.save(session);
    return rawToken;
  }

  @Transactional
  public void revoke(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) return;
    sessionRepo.findBySessionToken(hashToken(rawToken)).ifPresent(sessionRepo::delete);
  }

  /**
   * refresh 토큰(raw) → 활성 세션의 userId. 만료·미존재 시 빈 Optional. JWT access는 stateless라, refresh 검증만 DB를
   * 거친다(이 테이블이 refresh rotation·revoke의 SOR). AuthController.refresh가 호출.
   */
  @Transactional(readOnly = true)
  public Optional<UUID> resolveUserId(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    return sessionRepo
        .findBySessionToken(hashToken(rawToken))
        .filter(s -> s.getExpiresAt().isAfter(OffsetDateTime.now()))
        .map(SessionEntity::getUserId);
  }

  /**
   * 사용자의 모든 refresh 세션 폐기 — "전 기기 로그아웃". 비밀번호 재설정의 전형적 동기가 "계정 탈취 의심"이므로, 공격자가 이미 쥔 refresh(28d)를
   * 재설정 시점에 끊는다(G3). 향후 refresh 재사용 탐지(G2 token family)의 도난 대응으로도 재사용 예정.
   */
  @Transactional
  public long revokeAllForUser(UUID userId) {
    return sessionRepo.deleteByUserId(userId);
  }

  /** 쿠키의 raw 토큰을 DB 조회용 해시로 변환 (resolveUserId·revoke에서 사용). */
  public static String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable in JVM — environment broken", e);
    }
  }

  /** 만료된 세션 정리 — cleanup cron 매시 호출. */
  @Transactional
  public long purgeExpired() {
    return sessionRepo.deleteByExpiresAtBefore(OffsetDateTime.now());
  }

  private static String generateToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
