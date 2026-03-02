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

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Copy built jar from builder stage
COPY --from=builder /workspace/apps/api/build/libs/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
