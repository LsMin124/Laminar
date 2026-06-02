package com.laminar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.r2.*} — Cloudflare R2(S3 호환) 접속 설정.
 *
 * <p>R2Config(S3Client/S3Presigner 빈)와 R2StorageService(bucket)가 공유. 두 클래스로 흩어져 있던
 * {@code @Value("${app.r2.*}")}를 단일 타입으로 응집.
 */
@ConfigurationProperties(prefix = "app.r2")
public record R2Properties(
    @DefaultValue("") String endpoint,
    @DefaultValue("") String accessKey,
    @DefaultValue("") String secretKey,
    @DefaultValue("laminar-attachments") String bucket) {}
