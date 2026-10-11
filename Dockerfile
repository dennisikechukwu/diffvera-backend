# ==============================================================================
# Stage 1: Build Application with Java 25 JDK
# ==============================================================================
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

# Copy Maven wrapper and POM first for layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (fail-safe cache layer)
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build production artifact (skipping tests)
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# Stage 2: Minimal Production JRE Runtime
# ==============================================================================
FROM eclipse-temurin:25-jre AS runner

WORKDIR /app

# Install curl for container healthcheck and create unprivileged user
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd -r diffvera && useradd -r -g diffvera -s /bin/false diffvera

# Copy compiled executable JAR from builder stage
COPY --from=builder /build/target/*.jar app.jar

# Set ownership
RUN chown -R diffvera:diffvera /app

USER diffvera

# Spring Boot default port
EXPOSE 8080

# Configure production JVM flags and container resource awareness
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
