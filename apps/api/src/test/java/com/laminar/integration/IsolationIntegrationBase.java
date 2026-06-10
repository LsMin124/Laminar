package com.laminar.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Testcontainers 기반 통합 테스트 베이스.
 *
 * <p>- PostgreSQL 17 컨테이너 1개를 모든 sub-class가 공유하는 <b>싱글톤 패턴</b>(수동 start, JVM 종료 시 Ryuk 정리). ⚠
 * {@code @Testcontainers}/{@code @Container} 라이프사이클을 일부러 쓰지 않는다 — 그 extension은 공유 베이스의 static 컨테이너를
 * "첫 서브클래스 종료 시" stop하는데, 캐시된 Spring 컨텍스트는 후속 IT 클래스에 재사용되므로 죽은 DB를 가리키게 된다(전 테스트 30s 커넥션 타임아웃,
 * CI에서 실측). - @ServiceConnection이 datasource·flyway·jpa url 자동 주입(컨테이너 수동 관리와 무관하게 동작) - Flyway가 전체
 * 마이그레이션 적용 → 진짜 스키마로 격리 검증 - R2(S3) 빈은 mock — placeholder 엔드포인트로의 실연결(ConnectException) 차단. 필요한
 * 테스트가 스텁한다.
 *
 * <p>사용: extends IsolationIntegrationBase + @Autowired Repository/Service.
 */
@SpringBootTest
@ActiveProfiles("integration")
public abstract class IsolationIntegrationBase {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("laminar_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  static {
    POSTGRES.start();
  }

  /** R2(S3) 호출 차단 — finalize의 HEAD 검증 등은 각 테스트가 스텁(AttachmentServiceIT 참조). */
  @MockitoBean protected S3Client s3Client;

  @MockitoBean protected S3Presigner s3Presigner;

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
