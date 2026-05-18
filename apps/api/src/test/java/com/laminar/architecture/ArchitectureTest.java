package com.laminar.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

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
   * 1.10.1 — 레이어드 아키텍처 강제: Web → Service → Repository 한 방향.
   *
   * <p>{@code withOptionalLayers(true)} — Phase 1엔 service/repository 패키지가 비어 있어도 통과 (Phase 2+ 적재되면
   * 위반 시 즉시 실패).
   */
  @ArchTest
  static final ArchRule layered_architecture =
      Architectures.layeredArchitecture()
          .consideringAllDependencies()
          .withOptionalLayers(true)
          .layer("Web")
          .definedBy("com.laminar.web..")
          .layer("Service")
          .definedBy("com.laminar.service..")
          .layer("Repository")
          .definedBy("com.laminar.repository..")
          .whereLayer("Web")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Service")
          .mayOnlyBeAccessedByLayers("Web")
          .whereLayer("Repository")
          .mayOnlyBeAccessedByLayers("Service");

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

  /** 1.10.3 — raw EntityManager는 system 패키지 외부 사용 금지 (WorkspaceContext만 허용). */
  @ArchTest
  static final ArchRule entitymanager_only_in_system =
      noClasses()
          .that()
          .resideOutsideOfPackage("com.laminar.system..")
          .and()
          .haveSimpleNameNotContaining("WorkspaceContext")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("jakarta.persistence.EntityManager");
}
