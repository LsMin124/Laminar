package com.laminar.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laminar.config.JwtProperties;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** JwtService 순수 단위 테스트 — DB·컨테이너 불필요(CI에서도 실행). */
class JwtServiceTest {

  private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long-xxxxx";

  private JwtService service(Duration accessTtl) {
    return new JwtService(new JwtProperties(SECRET, accessTtl, Duration.ofDays(28)));
  }

  @Test
  void issuesAndVerifiesAccessToken() {
    JwtService jwt = service(Duration.ofMinutes(15));
    UUID userId = UUID.randomUUID();

    String token = jwt.issueAccessToken(userId, "a@b.com", "Alice");
    Optional<LaminarPrincipal> principal = jwt.verifyAccessToken(token);

    assertThat(principal).isPresent();
    assertThat(principal.get().userId()).isEqualTo(userId);
    assertThat(principal.get().email()).isEqualTo("a@b.com");
    assertThat(principal.get().displayName()).isEqualTo("Alice");
  }

  @Test
  void rejectsExpiredToken() throws InterruptedException {
    JwtService jwt = service(Duration.ofMillis(1));
    String token = jwt.issueAccessToken(UUID.randomUUID(), "a@b.com", "Alice");
    Thread.sleep(50);

    assertThat(jwt.verifyAccessToken(token)).isEmpty();
  }

  @Test
  void rejectsTokenSignedWithDifferentSecret() {
    JwtService issuer = service(Duration.ofMinutes(15));
    JwtService verifier =
        new JwtService(
            new JwtProperties(
                "another-secret-key-at-least-32-bytes-long-yyyyyyyy",
                Duration.ofMinutes(15),
                Duration.ofDays(28)));
    String token = issuer.issueAccessToken(UUID.randomUUID(), "a@b.com", "Alice");

    assertThat(verifier.verifyAccessToken(token)).isEmpty();
  }

  @Test
  void rejectsTamperedToken() {
    JwtService jwt = service(Duration.ofMinutes(15));
    String token = jwt.issueAccessToken(UUID.randomUUID(), "a@b.com", "Alice");
    String tampered = token.substring(0, token.length() - 2) + "xx";

    assertThat(jwt.verifyAccessToken(tampered)).isEmpty();
  }

  @Test
  void rejectsShortSecret() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new JwtService(
                new JwtProperties("too-short", Duration.ofMinutes(15), Duration.ofDays(28))));
  }

  @Test
  void returnsEmptyForBlankOrNullToken() {
    JwtService jwt = service(Duration.ofMinutes(15));
    assertThat(jwt.verifyAccessToken("")).isEmpty();
    assertThat(jwt.verifyAccessToken(null)).isEmpty();
  }
}
