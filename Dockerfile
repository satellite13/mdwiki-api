FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY src src
COPY models models
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
RUN groupadd -g 1001 cnb && useradd -u 1002 -g cnb -m cnb
WORKDIR /home/cnb
COPY --from=build --chown=cnb:cnb /app/build/libs/*.jar app.jar
USER cnb
EXPOSE 8080
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]