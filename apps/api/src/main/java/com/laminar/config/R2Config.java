package com.laminar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Cloudflare R2 (S3 호환) 설정.
 *
 * R2는 region "auto" + pathStyleAccess=true 가 권장.
 * presigned URL은 short-lived (5분 TTL) — 클라이언트가 직접 R2에 PUT/GET.
 */
@Configuration
public class R2Config {

    private final R2Properties props;

    public R2Config(R2Properties props) {
        this.props = props;
    }

    @Bean
    public S3Client r2S3Client() {
        return S3Client.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create(endpointOrDefault()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public S3Presigner r2S3Presigner() {
        return S3Presigner.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create(endpointOrDefault()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /** local dev에서 R2 미설정 시 dummy URL — 실제 호출은 미지원이라 prod에서 ENV 강제. */
    private String endpointOrDefault() {
        String endpoint = props.endpoint();
        return endpoint == null || endpoint.isBlank()
                ? "https://localhost:0/r2-dev-placeholder"
                : endpoint;
    }
}
