plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.5.1"
}

group = "com.laminar"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// ── 외부 라이브러리 버전 (Spring Boot BOM 외 명시 필요) ──
// 2026-05-15 Maven Central ground truth 기준
val awsSdkVersion = "2.44.7"
val googleApiClientVersion = "2.9.0"
val googleCalendarVersion = "v3-rev20260225-2.0.0"
val commonmarkVersion = "0.28.0"
val owaspSanitizerVersion = "20260313.1"
val tikaVersion = "3.3.0"
val bucket4jVersion = "8.10.1" // groupId 주의: com.bucket4j (구 io.github.bucket4j는 7.x까지)
val shedlockVersion = "7.7.0" // 5.x→7.x major bump (코드 없으니 마이그레이션 부담 없음)
val sentryVersion = "8.41.0"
val archunitVersion = "1.4.2"

dependencies {
    // ── Spring Boot starters (Initializr) ──
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // ── AWS S3 SDK (Cloudflare R2 호환, BOM으로 transitive 일관성) ──
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:s3")

    // ── Google Calendar API ──
    implementation("com.google.api-client:google-api-client:$googleApiClientVersion")
    implementation("com.google.apis:google-api-services-calendar:$googleCalendarVersion")

    // ── Markdown + HTML sanitize (카드 본문 렌더링) ──
    implementation("org.commonmark:commonmark:$commonmarkVersion")
    implementation("org.commonmark:commonmark-ext-gfm-tables:$commonmarkVersion")
    implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:$owaspSanitizerVersion")

    // ── Content-type 감지 (첨부 업로드 검증) ──
    implementation("org.apache.tika:tika-core:$tikaVersion")

    // ── Rate limiting (인증·핵심 API; v8부터 groupId가 com.bucket4j) ──
    implementation("com.bucket4j:bucket4j-core:$bucket4jVersion")

    // ── Distributed lock (cron coordination) ──
    implementation("net.javacrumbs.shedlock:shedlock-spring:$shedlockVersion")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:$shedlockVersion")

    // ── 모니터링 ──
    implementation("io.sentry:sentry-spring-boot-starter-jakarta:$sentryVersion")

    // ── Lombok (선택, boilerplate 감소) ──
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // ── Postgres driver ──
    runtimeOnly("org.postgresql:postgresql")

    // ── 테스트 ──
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ── 포맷팅 (Task 1.2.7) — formatter 버전은 spotless 플러그인 기본 사용 ──
spotless {
    java {
        googleJavaFormat()
        target("src/**/*.java")
    }
    kotlinGradle {
        ktlint()
        target("*.gradle.kts")
    }
}

// ── Task 1.6: React 빌드 산출물을 Spring 정적 리소스로 통합 ──
// 요구사항: pnpm이 PATH에 있어야 함 (corepack: ~/.local/bin/pnpm).
// Docker 빌드는 Phase 1.7에서 node 단계로 분리해 처리한다.
tasks.register<Exec>("buildWebApp") {
    description = "React 앱 빌드 (apps/web → dist/)"
    group = "build"
    workingDir = file("../web")
    commandLine("pnpm", "build")
    inputs.dir("../web/src")
    inputs.files("../web/package.json", "../web/vite.config.ts", "../web/tsconfig.json", "../web/tsconfig.app.json")
    outputs.dir("../web/dist")
}

tasks.register<Copy>("copyWebDist") {
    description = "React dist를 Spring static으로 복사"
    group = "build"
    dependsOn("buildWebApp")
    from("../web/dist")
    into("src/main/resources/static")
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("copyWebDist")
}

// Spotless가 src/를 스캔하는데 copyWebDist가 src/main/resources/static에 쓰므로
// Gradle implicit dependency 경고를 mustRunAfter로 회피
tasks.matching { it.name.startsWith("spotless") }.configureEach {
    mustRunAfter("copyWebDist")
}

// 정적 리소스가 build/ 산출물에 포함되므로 clean 시도 동반
tasks.named<Delete>("clean") {
    delete("src/main/resources/static")
}
