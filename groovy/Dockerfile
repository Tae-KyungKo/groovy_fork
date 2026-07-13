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

# JRE만 포함하는 경량 런타임 이미지로 컨테이너 크기 최소화
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
