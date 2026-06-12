package com.laminar.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.laminar.error.GlobalExceptionHandler;
import com.laminar.security.LaminarPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

/**
 * 컨트롤러 단(standalone MockMvc) 테스트 공통기 (R2²).
 *
 * <p>전체 컨텍스트 없이 매핑·@Valid 검증·상태코드·오류 envelope 형상만 검증한다 — 보안 필터·격리 필터·DB는 통합 테스트(*IT)가 담당.
 * standalone에도 {@link GlobalExceptionHandler}를 결선해 프로덕션과 동일한 예외→상태코드 변환을 통과시킨다. *Test 네이밍이라 {@code
 * -PexcludeIT} 로컬 게이트에서도 돈다.
 */
public final class WebTestSupport {

  /** 컨트롤러 Authentication 파라미터에 주입되는 고정 테스트 사용자. */
  public static final LaminarPrincipal PRINCIPAL =
      new LaminarPrincipal(
          UUID.fromString("11111111-1111-1111-1111-111111111111"), "tester@laminar.dev", "테스터");

  private WebTestSupport() {}

  /** Boot 기본과 정렬된 ObjectMapper — java.time을 ISO 문자열로(standalone 기본은 타임스탬프 배열). */
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public static MockMvc mvc(Object controller, HandlerMethodArgumentResolver... resolvers) {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(resolvers)
        .setMessageConverters(new MappingJackson2HttpMessageConverter(OBJECT_MAPPER))
        .build();
  }

  /**
   * 컨트롤러의 {@code Authentication} 파라미터용 토큰 — MockMvc 요청 빌더의 {@code .principal(...)}로 주입하면 Spring
   * MVC의 Principal 리졸버가 전달한다(시큐리티 필터 없이).
   */
  public static Authentication auth() {
    return new UsernamePasswordAuthenticationToken(PRINCIPAL, "n/a", List.of());
  }
}
