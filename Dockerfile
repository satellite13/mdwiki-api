# Сборка приложения поверх mdwiki-api-build-base (Gradle + deps уже в образе).
# Базовый образ: Dockerfile.build-base / ./scripts/build-base-image.sh
ARG BUILD_BASE_IMAGE=mdwiki-api-build-base:latest
FROM ${BUILD_BASE_IMAGE} AS build
WORKDIR /app

ENV GRADLE_USER_HOME=/gradle-cache

# При изменении зависимостей пересоберите build-base (fingerprint в deploy-скрипте).
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src src
COPY models models

RUN ./gradlew bootJar --no-daemon -x test --offline \
  || ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
RUN groupadd -g 1001 cnb && useradd -u 1002 -g cnb -m cnb
WORKDIR /home/cnb
COPY --from=build --chown=cnb:cnb /app/build/libs/*.jar app.jar
USER cnb
EXPOSE 8080
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
