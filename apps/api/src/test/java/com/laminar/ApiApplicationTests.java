package com.laminar;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring context load smoke test (Initializr 기본).
 *
 * <p>현재 비활성: {@code @SpringBootTest}가 전체 컨텍스트(JPA + Flyway + DataSource)를 로드하는데 CI에는 DB가 없어 실패. 본격
 * 통합 테스트는 Phase 2+에서 testcontainers postgres로 격리 가능.
 *
 * <p>현재 부팅 검증은 로컬 {@code bootRun} (Phase 1.2.6 smoke)이 더 광범위.
 */
@SpringBootTest
@Disabled("Phase 2+ testcontainers 도입 시 활성 — 현재 CI는 DB 없음")
class ApiApplicationTests {

  @Test
  void contextLoads() {}
}
