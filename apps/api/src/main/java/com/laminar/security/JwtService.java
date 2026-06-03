package com.laminar.security;

import com.laminar.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Access 토큰(JWT, HS256) 발급·검증.
 *
 * <p>Stateless — 서명·만료만 검증(DB 조회 0). claims: sub=userId, email, name. refresh 토큰은 opaque({@link
 * com.laminar.user.application.SessionService})이며 본 서비스는 access 전용이다.
 *
 * <p>키는 {@code JWT_SECRET}(≥32바이트)에서 파생. 미설정·약한 키는 부팅 시 즉시 실패(fail fast)해 무서명/약서명 운영을 차단한다. 검증은
 * {@code verifyWith(SecretKey)}로 HMAC 계열만 허용 — "alg:none" 위조와 알고리즘 혼동 공격을 차단한다.
 */
@Service
public class JwtService {

  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_NAME = "name";
  private static final int MIN_SECRET_BYTES = 32;

  private final SecretKey key;
  private final long accessTtlSeconds;

  public JwtService(JwtProperties props) {
    byte[] secretBytes =
        props.secret() == null ? new byte[0] : props.secret().getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "JWT_SECRET이 미설정이거나 32바이트 미만입니다 (HS256은 ≥256bit 필요). "
              + "환경변수 JWT_SECRET을 설정하세요 (예: openssl rand -base64 48).");
    }
    this.key = Keys.hmacShaKeyFor(secretBytes);
    this.accessTtlSeconds = props.accessTtl().toSeconds();
  }

  /** access 토큰 발급. displayName은 null 허용(클레임 생략). */
  public String issueAccessToken(UUID userId, String email, String displayName) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userId.toString())
        .claim(CLAIM_EMAIL, email)
        .claim(CLAIM_NAME, displayName)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  /**
   * access 토큰 검증 → {@link LaminarPrincipal}. 서명 불일치·만료·형식 오류·sub UUID 파싱 실패 시 빈 Optional (인증 안 됨).
   */
  public Optional<LaminarPrincipal> verifyAccessToken(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      UUID userId = UUID.fromString(claims.getSubject());
      String email = claims.get(CLAIM_EMAIL, String.class);
      String name = claims.get(CLAIM_NAME, String.class);
      return Optional.of(new LaminarPrincipal(userId, email, name));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
