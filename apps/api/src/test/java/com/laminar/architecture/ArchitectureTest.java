package com.laminar.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.laminar.system.SystemRepository;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

/**
 * 패키지 의존성 룰 (Task 1.10).
 *
 * <p>Phase 1은 클래스가 적어 룰이 vacuous하게 통과하지만, Phase 2+에서 {@code
 * com.laminar.{service,repository,system,web.controller}} 패키지가 생기면 즉시 강제된다.
 *
 * <p>실행: {@code ./gradlew test} 또는 {@code ./gradlew test --tests
 * com.laminar.architecture.ArchitectureTest}. CI는 매 push/PR마다 검증.
 */
@AnalyzeClasses(packages = "com.laminar", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  /**
   * 1.10.2 — system 패키지는 web.controller에서 import 금지 (3계층 컨텍스트 우회 방지).
   *
   * <p>{@code allowEmptyShould(true)} — Phase 1엔 web.controller 패키지가 비어 vacuous하게 통과 (Phase 2+ 컨트롤러
   * 적재 후 강제).
   */
  @ArchTest
  static final ArchRule system_package_not_imported_by_web_controller =
      noClasses()
          .that()
          .resideInAPackage("com.laminar.web.controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.laminar.system..")
          .allowEmptyShould(true);

  /**
   * 1.10.3 — raw EntityManager는 system 패키지 외부 사용 금지 (WorkspaceContext / HibernateFilterActivator만
   * 허용).
   *
   * <p>HibernateFilterActivator는 Session unwrap → enableFilter 호출이 책무라 EntityManager 직접 접근 필요.
   */
  @ArchTest
  static final ArchRule entitymanager_only_in_system =
      noClasses()
          .that()
          .resideOutsideOfPackage("com.laminar.system..")
          .and()
          .haveSimpleNameNotContaining("WorkspaceContext")
          .and()
          .haveSimpleNameNotContaining("HibernateFilterActivator")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("jakarta.persistence.EntityManager");

  /**
   * 3.5.1 — @Entity는 com.laminar 1-depth 하위 도메인 패키지에만 위치.
   *
   * <p>예: com.laminar.card.CardEntity O, com.laminar.web.SomeEntity X. 도메인 패키지 단위 격리 정책을 enforce.
   */
  @ArchTest
  static final ArchRule entities_in_domain_packages =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .should()
          .resideInAPackage("com.laminar.(*)..")
          .andShould()
          .resideOutsideOfPackages(
              "com.laminar.web..",
              "com.laminar.service..",
              "com.laminar.repository..",
              "com.laminar.context..",
              "com.laminar.system..",
              "com.laminar.config..");

  /**
   * 3.5.2 — SystemRepository 파생 (interface extends 또는 class implements)은 com.laminar.system 패키지에만
   * 위치.
   *
   * <p>시스템 컨텍스트 (격리 우회)는 명시적으로 system 패키지에 격리. 일반 도메인 Repository가 우연히 SystemRepository를 상속해 격리 우회하는
   * 사고를 차단.
   *
   * <p>{@code areAssignableTo}는 interface→interface 상속도 매칭 (vs {@code implement}는
   * class→interface만). SystemRepository 자체는 제외 (자기 자신 매칭 제외).
   */
  @ArchTest
  static final ArchRule system_repository_only_in_system_package =
      classes()
          .that()
          .areAssignableTo(SystemRepository.class)
          .and()
          .areNotAssignableFrom(SystemRepository.class)
          .should()
          .resideInAPackage("com.laminar.system..");

  /**
   * 3.5.3 — SystemRepository는 web.controller에서 직접 import 금지 (3계층 우회 방지).
   *
   * <p>web 레이어는 서비스 경유만 — SystemRepository는 서비스 안에서 격리 정책 검증 후 호출.
   */
  @ArchTest
  static final ArchRule system_repository_not_in_web_controller =
      noClasses()
          .that()
          .resideInAPackage("com.laminar.web.controller..")
          .should()
          .dependOnClassesThat()
          .areAssignableTo(SystemRepository.class)
          .allowEmptyShould(true);

  /**
   * N-3 — web 레이어(컨트롤러)는 Repository에 직접 의존 금지.
   *
   * <p>모든 데이터 접근은 @Transactional 서비스를 경유해야 한다. WorkspaceFilterAspect는 서비스 트랜잭션 경계 안에서만 격리 필터를
   * 활성화하므로(활성 트랜잭션 없으면 skip), 비-트랜잭션 컨텍스트(컨트롤러가 리포지토리를 직접 호출)의 list/derived 쿼리는 필터가 적용되지 않아 교차 테넌트
   * 누출 위험이 있다. 본 룰로 그 회귀 벡터를 차단한다. (리포지토리 호출자 전반의 트랜잭션 보장은 PostgreSQL RLS를 최종 방어선으로 — 별도 과제.)
   *
   * <p>system 패키지 리포지토리는 보안 필터가 트랜잭션 밖에서 호출(글로벌 자원, 워크스페이스 필터 불필요)하므로 본 룰 대상이 아니다(web은 어차피
   * SystemRepository 직접 접근도 위 룰로 금지).
   */
  @ArchTest
  static final ArchRule web_must_not_access_repositories_directly =
      noClasses()
          .that()
          .resideInAPackage("com.laminar.web..")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Repository")
          .allowEmptyShould(true);
}
