package com.laminar.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.jwt.*} — JWT 토큰 정책.
 *
 * <p>secret: access 토큰 HS256 서명 키(≥32바이트=256bit). 환경변수 {@code JWT_SECRET} 주입, 평문 커밋 금지. access-ttl:
 * access 토큰 수명(단명, 기본 15분). refresh-ttl: refresh 토큰 수명(기본 28일) — opaque 토큰의 sessions 만료에 사용.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
    String secret,
    @DefaultValue("PT15M") Duration accessTtl,
    @DefaultValue("P28D") Duration refreshTtl) {}
