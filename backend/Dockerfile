FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace/backend

COPY backend/gradlew backend/gradlew.bat backend/build.gradle backend/settings.gradle ./
COPY backend/gradle ./gradle

RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon

COPY backend/src ./src

RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring \
    && adduser -S spring -G spring

USER spring:spring
WORKDIR /app

COPY --from=build /workspace/backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
