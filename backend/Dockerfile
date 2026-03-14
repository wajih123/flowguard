# FlowGuard Backend Dockerfile
# Quarkus 3.8.1, Java 21 — multi-stage, supports linux/amd64 + linux/arm64

# ── Stage 1 : Build ─────────────────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Cache Maven dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Build
COPY src ./src
RUN mvn package -DskipTests -B -q

# ── Stage 2 : Run ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl

COPY --from=builder /build/target/quarkus-app/ ./

EXPOSE 8080

CMD ["java", "-Xmx512m", "-XX:+UseContainerSupport", "-jar", "quarkus-run.jar"]
