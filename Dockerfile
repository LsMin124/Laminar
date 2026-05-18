# Laminar 프로덕션 멀티스테이지 빌드 (Task 1.7.1~1.7.3)
# 사용: fly deploy (Fly 원격 빌더) 또는 로컬 docker build

# ── Stage 1: React 빌드 ───────────────────────────────────────
FROM node:22-alpine AS web-build
WORKDIR /web

# pnpm 활성화 (corepack + 9.15 핀)
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate

# 의존성 캐시 활용 — lockfile만 먼저 복사
COPY apps/web/package.json apps/web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# 소스 복사 + 빌드
COPY apps/web/ ./
RUN pnpm build

# ── Stage 2: Spring Boot JAR 빌드 ─────────────────────────────
# gradle.properties는 로컬 JDK 경로 핀이라 Docker에선 제외 (eclipse-temurin 이미지가 JAVA_HOME 제공)
FROM eclipse-temurin:21-jdk-alpine AS api-build
WORKDIR /api

# Gradle wrapper + 빌드 스크립트 먼저 (의존성 캐시 레이어)
COPY apps/api/gradle ./gradle
COPY apps/api/gradlew apps/api/build.gradle.kts apps/api/settings.gradle.kts ./
RUN chmod +x ./gradlew

# 소스 + Stage 1 web 산출물 복사
COPY apps/api/src ./src
COPY --from=web-build /web/dist ./src/main/resources/static

# JAR 빌드
#  - test 스킵 (Docker 빌드 시 DB 없음)
#  - buildWebApp/copyWebDist 스킵 (Stage 1에서 처리됨, dist도 위에서 복사 완료)
#  - spotlessCheck 스킵 (소스 형식은 CI에서 검증)
RUN ./gradlew --no-daemon bootJar \
    -x test -x buildWebApp -x copyWebDist -x spotlessCheck

# ── Stage 3: 런타임 ───────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# pg_dump (Task 6.5 백업용) — Alpine은 postgresql16-client
RUN apk add --no-cache postgresql16-client tzdata && \
    ln -snf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

# JAR만 복사 (빌드 산출물 외 다른 파일 미포함)
COPY --from=api-build /api/build/libs/api-*.jar /app/app.jar

# Spring Boot HTTP 포트
EXPOSE 8080

# JVM 옵션: 컨테이너 메모리 인식 + UTC 강제 (애플리케이션 내부는 UTC로 통일)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
