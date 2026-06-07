package com.laminar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 발송 설정 (app.mail.*). resendApiKey 미설정 시 ResendEmailSender가 발송 생략 + 링크를 로그로 남긴다(오너 폴백).
 *
 * @param from 발신 주소 (Resend 검증 도메인 또는 onboarding@resend.dev)
 * @param resendApiKey Resend API 키 (RESEND_API_KEY) — 비면 폴백 로그 모드
 * @param resetBaseUrl 비밀번호 재설정 링크 베이스 URL
 */
@ConfigurationProperties("app.mail")
public record MailProperties(String from, String resendApiKey, String resetBaseUrl) {}
