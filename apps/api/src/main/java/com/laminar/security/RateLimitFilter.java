package com.laminar.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * IP 기반 레이트리밋 (H-1) — 무차별 대입·가입/발신/presign 남용 차단.
 *
 * <p>시큐리티 필터체인보다 먼저 실행(@Order)되어 로그인 BCrypt 비용 이전에 throttle. 분당 토큰 버킷(bucket4j). 인스턴스별 인메모리 — 다중
 * 머신에선 머신당 적용(기본 방어로 충분). 초과 시 429 + Retry-After. IP는 Fly 엣지의 X-Forwarded-For 우선.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

  private record Rule(String method, String path, int perMinute) {}

  private static final List<Rule> RULES =
      List.of(
          new Rule("POST", "/api/auth/login", 10),
          new Rule("POST", "/api/auth/signup", 5),
          // 비밀번호 재설정 요청 — 이메일 폭탄·다중 유효 토큰 남용 억제(전체리뷰 5차 Q1)
          new Rule("POST", "/api/auth/password-reset/request", 5),
          new Rule("POST", "/api/attachments/upload-url", 30),
          new Rule("POST", "/api/subjects/current/invitations", 20),
          // LAB 초대코드는 사람이 입력하는 8자리 — 무차별 대입 억제(LAB재설계 §2)
          new Rule("POST", "/api/labs/join", 10));

  private static final int MAX_TRACKED_KEYS = 50_000;

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Rule rule = match(request);
    if (rule != null) {
      if (buckets.size() > MAX_TRACKED_KEYS) {
        // 전체 clear(전원 버킷 리셋·강제초기화 우회) 대신 일부만 축출해 절반까지 축소.
        var it = buckets.keySet().iterator();
        int toRemove = buckets.size() - MAX_TRACKED_KEYS / 2;
        while (it.hasNext() && toRemove-- > 0) {
          it.next();
          it.remove();
        }
      }
      String key = rule.path() + "|" + clientIp(request);
      Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(rule.perMinute()));
      if (!bucket.tryConsume(1)) {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType("application/json;charset=UTF-8");
        response
            .getWriter()
            .write(
                "{\"status\":429,\"error\":\"Too Many Requests\","
                    + "\"message\":\"rate limit exceeded — try again shortly\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static Bucket newBucket(int perMinute) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(perMinute)
            .refillGreedy(perMinute, Duration.ofMinutes(1))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  private static Rule match(HttpServletRequest request) {
    String method = request.getMethod();
    String uri = request.getRequestURI();
    for (Rule rule : RULES) {
      if (rule.method().equals(method) && rule.path().equals(uri)) {
        return rule;
      }
    }
    return null;
  }

  private static String clientIp(HttpServletRequest request) {
    // Fly 엣지가 설정하는 실제 클라이언트 IP — 클라이언트가 위조 불가(엣지에서 덮어씀).
    String flyIp = request.getHeader("Fly-Client-IP");
    if (flyIp != null && !flyIp.isBlank()) {
      return flyIp.trim();
    }
    // 폴백: XFF의 *마지막* 홉(엣지가 append) — 클라이언트가 보낸 앞쪽 토큰은 신뢰 불가.
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      String[] parts = forwarded.split(",");
      return parts[parts.length - 1].trim();
    }
    return request.getRemoteAddr();
  }
}
