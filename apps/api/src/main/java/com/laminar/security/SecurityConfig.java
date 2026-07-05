package com.laminar.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Spring Security 기본 체인.
 *
 * <p>정책: - 세션 IF_REQUIRED — 평시 JWT access 쿠키(laminar-access) 인증이 SOR이라 API는 무세션. OAuth2 login의
 * 인가요청(state) 저장에만 HTTP 세션을 쓰고, 성공 핸들러가 즉시 invalidate. - Spring CSRF 토큰 비활성화 — 대신 CsrfHeaderFilter가
 * custom-header(X-Laminar-CSRF)를 쿠키 기반 상태변경 요청에 강제 (M-1). SameSite=Lax(쿠키)와 합쳐 다층 CSRF 방어. - OAuth2
 * login (Google) 활성 — AUTH_GOOGLE_ID/SECRET 설정 시. 성공 시 OAuth2LoginSuccessHandler가 이메일로 사용자
 * find-or-create + JWT 쿠키(access/refresh) 발급(비밀번호 경로와 통일). - 보안 헤더: HSTS, X-Content-Type-Options,
 * X-Frame-Options, Referrer-Policy, Permissions-Policy, CSP
 *
 * <p>인증 면제: /api/auth/** (signup·login·logout·invitation accept) /api/health/** (actuator) /,
 * /index.html, /assets/**, /static/** (React SPA)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtAuthFilter,
      OAuth2LoginSuccessHandler oauth2SuccessHandler,
      ObjectProvider<ClientRegistrationRepository> clientRegistrations)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        // OAuth2 login은 인가요청(state) 저장에 세션이 필요 → IF_REQUIRED. SB3 explicit-save라
        // 쿠키 인증 API 요청은 세션을 만들지 않고(필터가 매 요청 컨텍스트 설정·미저장),
        // OAuth 핸드셰이크에만 세션이 생기며 성공 핸들러가 즉시 invalidate한다.
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        // SecurityContext를 HTTP 세션에 영속하지 않고 요청 범위만 사용 — JWT access 쿠키(laminar-access)
        // 인증을 매 요청 재파생(stateless). 세션 영속 시 JSESSIONID가 로그아웃(쿠키 삭제·토큰
        // revoke) 후에도 인증을 유지해 로그아웃이 무력화되던 버그를 차단. OAuth 인가요청(state)은
        // 별도 저장소(세션)라 영향 없음.
        .securityContext(
            sc -> sc.securityContextRepository(new RequestAttributeSecurityContextRepository()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/**", "/api/health/**", "/actuator/health")
                    .permitAll()
                    .requestMatchers("/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                    .requestMatchers("/", "/index.html", "/assets/**", "/static/**", "/favicon.ico")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        // API 미인증은 로그인 페이지 redirect가 아니라 401 — SPA가 자체 로그인 화면을 띄운다.
        // (oauth2Login 활성 시 기본 entry point가 redirect라 명시적으로 401로 고정.)
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        // CsrfHeaderFilter·jwtAuthFilter 모두 빌트인 UsernamePasswordAuthenticationFilter
        // 앞에 배치 — addFilterBefore의 참조는 order가 등록된 빌트인 필터여야 함(커스텀 필터
        // 참조 시 "does not have a registered order"로 부팅 실패). 추가 순서상 CSRF가 먼저 실행.
        .addFilterBefore(new CsrfHeaderFilter(), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .headers(
            headers ->
                headers
                    .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                    .contentTypeOptions(c -> {})
                    .referrerPolicy(
                        r ->
                            r.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .httpStrictTransportSecurity(
                        hsts ->
                            hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000).preload(true))
                    .permissionsPolicyHeader(
                        p -> p.policy("camera=(), microphone=(), geolocation=(), payment=()"))
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; "
                                    + "script-src 'self'; "
                                    + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                                    + "img-src 'self' data: https:; "
                                    + "font-src 'self' https://fonts.gstatic.com; "
                                    + "connect-src 'self' https://accounts.google.com https://www.googleapis.com https://*.r2.cloudflarestorage.com; "
                                    + "frame-src 'none'; "
                                    + "object-src 'none'; "
                                    + "base-uri 'self'")));
    // Google OAuth2 자격(AUTH_GOOGLE_ID/SECRET)이 설정된 환경에서만 oauth2Login 활성.
    // 자격 없는 local/test/build에선 ClientRegistrationRepository 빈이 없어 미적용 → 부팅 안전.
    if (clientRegistrations.getIfAvailable() != null) {
      http.oauth2Login(oauth -> oauth.successHandler(oauth2SuccessHandler));
    }
    return http.build();
  }
}
