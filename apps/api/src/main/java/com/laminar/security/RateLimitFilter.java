package com.laminar.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 기반 레이트리밋 (H-1) — 무차별 대입·가입/발신/presign 남용 차단.
 *
 * 시큐리티 필터체인보다 먼저 실행(@Order)되어 로그인 BCrypt 비용 이전에 throttle.
 * 분당 토큰 버킷(bucket4j). 인스턴스별 인메모리 — 다중 머신에선 머신당 적용(기본 방어로 충분).
 * 초과 시 429 + Retry-After. IP는 Fly 엣지의 X-Forwarded-For 우선.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, String path, int perMinute) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/auth/login", 10),
            new Rule("POST", "/api/auth/signup", 5),
            new Rule("POST", "/api/attachments/upload-url", 30),
            new Rule("POST", "/api/workspaces/current/invitations", 20));

    private static final int MAX_TRACKED_KEYS = 50_000;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Rule rule = match(request);
        if (rule != null) {
            if (buckets.size() > MAX_TRACKED_KEYS) {
                buckets.clear();
            }
            String key = rule.path() + "|" + clientIp(request);
            Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(rule.perMinute()));
            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"status\":429,\"error\":\"Too Many Requests\","
                                + "\"message\":\"rate limit exceeded — try again shortly\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static Bucket newBucket(int perMinute) {
        Bandwidth limit = Bandwidth.builder()
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
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
