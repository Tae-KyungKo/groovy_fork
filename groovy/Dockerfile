# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 의존성 레이어를 소스 코드 레이어보다 먼저 캐싱하여 빌드 시간 단축
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# --- Run stage ---
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

# 컨테이너 내부 프로세스를 루트가 아닌 전용 유저로 실행(운영 환경 보안 원칙)
RUN groupadd --system groovy && useradd --system --gid groovy --no-create-home groovy

# JRE만 포함하는 경량 런타임 이미지로 컨테이너 크기 최소화
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
RUN chown groovy:groovy app.jar

USER groovy
EXPOSE 8080

# docker-compose의 depends_on(condition: service_healthy)이 참조할 수 있도록
# 컨테이너 자체 헬스체크도 동일한 기준(/api/health)으로 선언한다.
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=10 \
	CMD wget -q --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
