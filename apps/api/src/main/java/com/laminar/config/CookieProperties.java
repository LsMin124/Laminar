package com.laminar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.cookie.*} — 세션 쿠키 정책.
 *
 * <p>secure: prod=true(HTTPS 전용), local=false. 기존 {@code @Value("${app.cookie.secure:true}")}를 대체해
 * AuthController·OAuth2LoginSuccessHandler가 단일 출처를 공유한다.
 */
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(@DefaultValue("true") boolean secure) {}
