package com.laminar.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cron 전용 DataSource (Task 1.3.4).
 *
 * <p>메인 DataSource는 환경에 따라 Neon pooler 또는 direct를 사용하지만, cron 잡은 풀러를 거치지 않고 direct 엔드포인트로 직접 연결한다
 * (장시간 트랜잭션·예측 가능한 부하).
 *
 * <p>주입 시: {@code @Qualifier("cronDataSource")} 사용.
 *
 * <p>설정 키 (application-{profile}.yml): {@code app.datasource.cron.*}
 */
@Configuration
public class CronDataSourceConfig {

  @Bean("cronDataSourceProperties")
  @ConfigurationProperties("app.datasource.cron")
  public DataSourceProperties cronDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean("cronDataSource")
  @ConfigurationProperties("app.datasource.cron.hikari")
  public DataSource cronDataSource(
      @Qualifier("cronDataSourceProperties") DataSourceProperties props) {
    return props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }
}
