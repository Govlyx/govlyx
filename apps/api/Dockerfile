
# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy dependency manifest first — this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build fat JAR (tests skipped for Docker builds)
COPY src ./src
RUN mvn package -DskipTests -q


# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS runtime

# Security: never run as root
RUN groupadd --gid 1001 jansahayak \
 && useradd  --uid 1001 --gid jansahayak --no-create-home --shell /bin/false jansahayak

WORKDIR /app

# Copy the fat JAR (badwords/strict.txt is embedded inside it via ClassPathResource)
COPY --from=builder /build/target/*.jar app.jar

# StaticResourceConfig maps these paths — harmless even though Cloudinary
# handles all real storage; avoids a FileNotFoundException at startup
RUN mkdir -p /app/uploads/posts /app/uploads/social-posts \
 && chown -R jansahayak:jansahayak /app

USER jansahayak

# application.properties: server.port=${PORT:8081}
# Render injects $PORT automatically; we expose the same value here
EXPOSE 8081

# JVM flags:
#   UseContainerSupport   — respect cgroup CPU/memory limits (essential on Render)
#   MaxRAMPercentage=75   — cap heap at 75% of container RAM, leave headroom for Metaspace
#   urandom               — fast SecureRandom; prevents slow JWT/AES startup on Linux
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]