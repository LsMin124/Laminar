package com.laminar.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers 기반 통합 테스트 베이스.
 *
 * <p>- PostgreSQL 17 컨테이너 1개를 모든 sub-class에서 공유 (static + reusable) - @ServiceConnection이
 * datasource·flyway·jpa url 자동 주입 - Flyway가 V1~V12 마이그레이션 적용 → 진짜 스키마로 격리 검증
 *
 * <p>사용: extends IsolationIntegrationBase + @Autowired Repository/Service.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
public abstract class IsolationIntegrationBase {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("laminar_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  /**
   * cron 전용 보조 DataSource(app.datasource.cron.*)는 property 기반이라 @ServiceConnection이 채워주지 않는다 — 같은
   * 컨테이너를 가리키도록 동적 주입(미설정 시 컨텍스트 부팅이 driver 미결정으로 실패).
   */
  @DynamicPropertySource
  static void cronDataSourceProperties(DynamicPropertyRegistry registry) {
    registry.add("app.datasource.cron.url", POSTGRES::getJdbcUrl);
    registry.add("app.datasource.cron.username", POSTGRES::getUsername);
    registry.add("app.datasource.cron.password", POSTGRES::getPassword);
  }
}
