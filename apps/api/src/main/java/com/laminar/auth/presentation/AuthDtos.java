package com.laminar.auth.presentation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * /api/auth/** 요청·응답 DTO 모음.
 *
 * <p>email — 정규화 책임은 UserService (trim + lowercase). 본 record는 검증만. password — 8자 이상 128자 이하 (해시
 * BCrypt 72-byte 한계 고려).
 *
 * <p>accessTtlSeconds — FE 선제 silent refresh 타이머의 기준(G1). 서버 설정(app.jwt.access-ttl)이 정본이라 FE에 거울
 * 상수를 두지 않는다.
 */
public final class AuthDtos {

  private AuthDtos() {}

  public record SignupRequest(
      @Email @NotBlank String email,
      @NotBlank @Size(min = 8, max = 128) String password,
      @NotBlank @Size(max = 100) String displayName) {}

  public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

  public record ResetRequestDto(@Email @NotBlank String email) {}

  public record ResetConfirmDto(
      @NotBlank String token, @NotBlank @Size(min = 8, max = 128) String password) {}

  public record AuthResponse(
      UUID userId,
      String email,
      String displayName,
      boolean emailVerified,
      long accessTtlSeconds) {}
}
