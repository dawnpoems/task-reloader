# Build stage
FROM gradle:8.5-jdk17-jammy AS builder

WORKDIR /workspace

# Copy root build files
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle .

# Copy api module
COPY apps/api/build.gradle ./apps/api/
COPY apps/api/src ./apps/api/src

# Build application
RUN chmod +x ./gradlew && ./gradlew :apps:api:build -x test

# Runtime stage
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# curl은 compose healthcheck에서 사용
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copy built jar
COPY --from=builder /workspace/apps/api/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]