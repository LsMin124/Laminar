package com.laminar.auth.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.laminar.auth.application.AuthService;
import com.laminar.auth.application.PasswordResetService;
import com.laminar.config.CookieProperties;
import com.laminar.config.JwtProperties;
import com.laminar.security.AuthCookies;
import com.laminar.security.LaminarPrincipal;
import com.laminar.testsupport.WebTestSupport;
import com.laminar.user.domain.UserEntity;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /api/auth/** 매핑·검증·상태코드 (R2²).
 *
 * <p>AuthCookies는 실물을 결선해 쿠키 발급(httpOnly·max-age)까지 단언한다 — 토큰 발급/회전 로직은 AuthService 모의. me()의
 * {@code @AuthenticationPrincipal}은 시큐리티 리졸버를 standalone에 직접 등록해 해석한다.
 */
class AuthControllerTest {

  private static final long ACCESS_TTL_SECONDS = Duration.ofMinutes(15).toSeconds();

  private AuthService authService;
  private PasswordResetService passwordResetService;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    authService = mock(AuthService.class);
    passwordResetService = mock(PasswordResetService.class);
    JwtProperties jwtProperties =
        new JwtProperties(
            "0123456789abcdef0123456789abcdef", Duration.ofMinutes(15), Duration.ofDays(28));
    AuthCookies authCookies = new AuthCookies(new CookieProperties(false), jwtProperties);
    mvc =
        WebTestSupport.mvc(
            new AuthController(authService, authCookies, passwordResetService, jwtProperties),
            new AuthenticationPrincipalArgumentResolver());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private static UserEntity user() {
    UserEntity u = new UserEntity();
    u.setId(WebTestSupport.PRINCIPAL.userId());
    u.setEmail(WebTestSupport.PRINCIPAL.email());
    u.setDisplayName(WebTestSupport.PRINCIPAL.displayName());
    return u;
  }

  @Test
  void signup_정상이면_200_쿠키_2종과_TTL_정본을_반환한다() throws Exception {
    given(authService.register("a@b.co", "password123", "홍길동"))
        .willReturn(new AuthService.Tokens("access-token", "refresh-token", user()));

    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"a@b.co\",\"password\":\"password123\",\"displayName\":\"홍길동\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("tester@laminar.dev"))
        .andExpect(jsonPath("$.emailVerified").value(false))
        .andExpect(jsonPath("$.accessTtlSeconds").value(ACCESS_TTL_SECONDS))
        .andExpect(cookie().value(AuthCookies.ACCESS_COOKIE, "access-token"))
        .andExpect(cookie().httpOnly(AuthCookies.ACCESS_COOKIE, true))
        .andExpect(cookie().value(AuthCookies.REFRESH_COOKIE, "refresh-token"))
        .andExpect(cookie().httpOnly(AuthCookies.REFRESH_COOKIE, true));
  }

  @Test
  void signup_이메일_형식_오류는_400() throws Exception {
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"not-an-email\",\"password\":\"password123\",\"displayName\":\"홍길동\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("email")));
  }

  @Test
  void signup_8자_미만_비밀번호는_400() throws Exception {
    mvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.co\",\"password\":\"short\",\"displayName\":\"홍길동\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("password")));
  }

  @Test
  void login_자격_불일치는_401_envelope() throws Exception {
    given(authService.authenticate(any(), any())).willReturn(Optional.empty());

    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.co\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("invalid credentials"))
        .andExpect(jsonPath("$.path").value("/api/auth/login"));
  }

  @Test
  void refresh_쿠키_없으면_401이고_쿠키를_즉시_만료시킨다() throws Exception {
    given(authService.refresh(null)).willReturn(Optional.empty());

    mvc.perform(post("/api/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("refresh token invalid"))
        .andExpect(cookie().maxAge(AuthCookies.ACCESS_COOKIE, 0))
        .andExpect(cookie().maxAge(AuthCookies.REFRESH_COOKIE, 0));
  }

  @Test
  void logout_은_204() throws Exception {
    mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
  }

  @Test
  void me_미인증이면_401() throws Exception {
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void me_인증이면_사용자와_TTL을_반환한다() throws Exception {
    LaminarPrincipal principal = WebTestSupport.PRINCIPAL;
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    given(authService.activeUser(principal.userId())).willReturn(Optional.of(user()));

    mvc.perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(principal.userId().toString()))
        .andExpect(jsonPath("$.accessTtlSeconds").value(ACCESS_TTL_SECONDS));
  }

  @Test
  void 비밀번호_재설정_요청은_204_위임() throws Exception {
    mvc.perform(
            post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@b.co\"}"))
        .andExpect(status().isNoContent());

    verify(passwordResetService).request("a@b.co");
  }

  @Test
  void 비밀번호_재설정_확인_짧은_비밀번호는_400() throws Exception {
    mvc.perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("password")));
  }
}
