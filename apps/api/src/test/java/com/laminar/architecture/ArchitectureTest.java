package com.laminar.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.laminar.system.SystemRepository;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.util.Set;

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
   * 1.10.2 — @RestController는 system 패키지를 직접 import 금지 (3계층 컨텍스트 우회 방지).
   *
   * <p>컨트롤러는 서비스 경유만 — system 패키지(격리 우회 표면)에 직접 의존하면 SubjectContext 검증을 건너뛸 수 있다. 컨트롤러가 도메인별
   * presentation에 분산되므로 위치가 아닌 애너테이션 기반으로 강제한다.
   */
  @ArchTest
  static final ArchRule controllers_must_not_import_system_package =
      noClasses()
          .that()
          .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.laminar.system..")
          .allowEmptyShould(true);

  /**
   * 1.10.3 — raw EntityManager는 system 패키지 외부 사용 금지 (SubjectContext / HibernateFilterActivator만
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
          .haveSimpleNameNotContaining("SubjectContext")
          .and()
          .haveSimpleNameNotContaining("HibernateFilterActivator")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("jakarta.persistence.EntityManager");

  /**
   * 3.5.1 — @Entity는 com.laminar 1-depth 하위 도메인 패키지에만 위치.
   *
   * <p>예: com.laminar.card.domain.CardEntity O, com.laminar.web.SomeEntity X. 도메인 패키지 단위 격리 정책을
   * enforce.
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
   * N-3 — web 레이어(컨트롤러)는 Repository에 직접 의존 금지.
   *
   * <p>모든 데이터 접근은 @Transactional 서비스를 경유해야 한다. SubjectFilterAspect는 서비스 트랜잭션 경계 안에서만 격리 필터를
   * 활성화하므로(활성 트랜잭션 없으면 skip), 비-트랜잭션 컨텍스트(컨트롤러가 리포지토리를 직접 호출)의 list/derived 쿼리는 필터가 적용되지 않아 교차 테넌트
   * 누출 위험이 있다. 본 룰로 그 회귀 벡터를 차단한다. (리포지토리 호출자 전반의 트랜잭션 보장은 PostgreSQL RLS를 최종 방어선으로 — 별도 과제.)
   *
   * <p>system 패키지 리포지토리는 보안 필터가 트랜잭션 밖에서 호출(글로벌 자원, 워크스페이스 필터 불필요)하므로 본 룰 대상이 아니다(web은 어차피
   * SystemRepository 직접 접근도 위 룰로 금지).
   */
  @ArchTest
  static final ArchRule controllers_must_not_access_repositories_directly =
      noClasses()
          .that()
          .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Repository")
          .allowEmptyShould(true);

  /**
   * DX-16 — 인프라 패키지(context·common·error)는 도메인 패키지에 의존 금지.
   *
   * <p>context는 14개 패키지가 의존하는 최기반 인프라 — 특정 도메인으로의 역의존은 순환을 만든다 (과거:
   * SubjectContext→subject.SubjectRole, 필터→SubjectMemberRepository). SubjectRole은 격리 모델의 어휘로
   * context 소속으로 이동했고, 멤버십 해석은 MembershipResolver 포트(구현: subject 측 @Component)로 분리해 순환을 절단했다. 본 룰이
   * 재발을 기계 차단한다. security(LaminarPrincipal)는 인증 인프라라 허용.
   */
  @ArchTest
  static final ArchRule infra_must_not_depend_on_domain_packages =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.laminar.context..", "com.laminar.common..", "com.laminar.error..")
          .should()
          .dependOnClassesThat(
              resideInAPackage("com.laminar..")
                  .and(
                      not(
                          resideInAnyPackage(
                              "com.laminar.context..",
                              "com.laminar.common..",
                              "com.laminar.error..",
                              "com.laminar.security.."))));

  /**
   * DX-20 — 도메인 리포지토리 원정 접근 차단: {도메인}.repository는 자기 도메인 또는 집계·시스템 작업 패키지에서만 사용.
   *
   * <p>타 도메인 리포지토리 직접 사용은 도메인 쓰기 불변식(카드 이동 시 DAG 연쇄 등)을 우회하는 경로다. 정당한 원정은 전부 읽기·시스템 작업 — graph(화면
   * 집계 BFF)·cron(스케줄 워커)·rrule(반복 전개)·admin(운영 콘솔)만 예외 허용.
   * common.repository(PersonalOwnedRepository 믹스인)는 도메인 리포지토리가 상속하는 기반이라 대상에서 제외. system 리포지토리는
   * .repository 하위 패키지가 아니라 본 룰 비대상(자체 룰로 격리).
   */
  @ArchTest
  static final ArchRule domain_repositories_only_accessed_from_own_domain =
      classes()
          .that()
          .resideInAPackage("com.laminar.(*).repository..")
          .and()
          .resideOutsideOfPackage("com.laminar.common..")
          .should(onlyBeDependedOnByOwnDomainOr(Set.of("graph", "cron", "admin", "rrule")));

  /**
   * DX-1③ — 리포지토리에 쓰는 application 서비스는 쓰기 가드 정본을 호출해야 한다.
   *
   * <p>가드 정본 = {@code SubjectContextHolder.requirePersonalWritable / requireLabMember /
   * requireLabAdmin} (DX-1①·L3). 신규 도메인이 가드 호출을 통째로 빠뜨리는 회귀(실사례: CardCategoryService가 scope·역할 검사
   * 없이 쓰기 — 본 룰 도입 시 검출·교정)를 기계 차단한다. 클래스 단위 검사라 "어느 메서드에 거는가"는 여전히 리뷰 몫 — 가드 0개인 쓰기 서비스만 잡는다.
   *
   * <p>면제 목록(각 사유): SYSTEM 스코프 표면(자기 계정·토큰·아웃박스 워커·반복 전개 — 컨텍스트 자체가 없음) = UserService ·
   * SessionService · PasswordResetService · JobsOutboxService · RruleExpansionService. 시스템성
   * append(SUBJECT_SHARED 감사) = AuditLogService. 주제·멤버십 관리 표면(§1.3 isOwner/isAdmin 자체 검사 + 컨텍스트 진입
   * 전 생성 표면) = SubjectService · SubjectMemberService · InvitationService. 진입점 가드 뒤 내부 협력자(정본은
   * CardService) = CardDagService. 사설 가드 잔존(gcal 데드표면 — cron의 SUBJECT_SHARED 컨텍스트를 허용해야 해 정본 부적합,
   * 구현/제거 결정 시 재논의) = CardEventLinkService.
   */
  private static final Set<String> WRITE_GUARD_EXEMPT =
      Set.of(
          "UserService",
          "SessionService",
          "PasswordResetService",
          "JobsOutboxService",
          "RruleExpansionService",
          "AuditLogService",
          "SubjectService",
          "SubjectMemberService",
          "InvitationService",
          "CardDagService",
          "CardEventLinkService");

  private static final Set<String> CANONICAL_WRITE_GUARDS =
      Set.of("requirePersonalWritable", "requireLabMember", "requireLabAdmin");

  @ArchTest
  static final ArchRule write_services_must_call_canonical_write_guard =
      classes()
          .that()
          .resideInAPackage("..application..")
          .and(
              com.tngtech.archunit.base.DescribedPredicate.describe(
                  "리포지토리 쓰기(save*/delete*)를 호출한다", ArchitectureTest::callsRepositoryWrite))
          .and(
              com.tngtech.archunit.base.DescribedPredicate.describe(
                  "쓰기 가드 면제 목록이 아니다", c -> !WRITE_GUARD_EXEMPT.contains(c.getSimpleName())))
          .should(callCanonicalWriteGuard());

  private static boolean callsRepositoryWrite(JavaClass clazz) {
    return clazz.getMethodCallsFromSelf().stream()
        .anyMatch(
            call ->
                call.getTargetOwner()
                        .isAssignableTo(org.springframework.data.repository.Repository.class)
                    && (call.getName().startsWith("save") || call.getName().startsWith("delete")));
  }

  private static ArchCondition<JavaClass> callCanonicalWriteGuard() {
    return new ArchCondition<>(
        "SubjectContextHolder 쓰기 가드 정본(requirePersonalWritable/requireLabMember/requireLabAdmin)을 호출") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        boolean guarded =
            clazz.getMethodCallsFromSelf().stream()
                .anyMatch(
                    call ->
                        "com.laminar.context.SubjectContextHolder"
                                .equals(call.getTargetOwner().getFullName())
                            && CANONICAL_WRITE_GUARDS.contains(call.getName()));
        if (!guarded) {
          events.add(
              SimpleConditionEvent.violated(
                  clazz, clazz.getFullName() + "가 리포지토리에 쓰지만 쓰기 가드 정본을 호출하지 않는다"));
        }
      }
    };
  }

  private static ArchCondition<JavaClass> onlyBeDependedOnByOwnDomainOr(Set<String> exempt) {
    return new ArchCondition<>("only be depended on by own domain or " + exempt) {
      @Override
      public void check(JavaClass repoClass, ConditionEvents events) {
        String targetDomain = laminarDomainSegment(repoClass.getPackageName());
        for (Dependency dep : repoClass.getDirectDependenciesToSelf()) {
          String originDomain = laminarDomainSegment(dep.getOriginClass().getPackageName());
          if (!targetDomain.equals(originDomain) && !exempt.contains(originDomain)) {
            events.add(SimpleConditionEvent.violated(dep, dep.getDescription()));
          }
        }
      }
    };
  }

  /** {@code com.laminar.{segment}...} → segment (com.laminar 외부 클래스는 빈 문자열). */
  private static String laminarDomainSegment(String packageName) {
    String prefix = "com.laminar.";
    if (!packageName.startsWith(prefix)) return "";
    String rest = packageName.substring(prefix.length());
    int dot = rest.indexOf('.');
    return dot < 0 ? rest : rest.substring(0, dot);
  }
}
