# ==============================================================================
# DineFlow Backend Production Dockerfile (Multi-Stage, Non-Root, Zero Secrets)
# ==============================================================================
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Create non-root system user and group
RUN addgroup -S dineflow && adduser -S dineflow -G dineflow \
    && mkdir -p /app/uploads && chown -R dineflow:dineflow /app

COPY --from=builder --chown=dineflow:dineflow /build/target/*.jar /app/dineflow-backend.jar

USER dineflow
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseZGC", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/dineflow-backend.jar"]
