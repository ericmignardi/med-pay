# =============================================================================
# MedPay Ledger — backend image (Spring Boot 4.1 / Java 21)
#
# Multi-stage. Stage 1 builds with the Maven wrapper pinned in the repo so the
# image build uses the same Maven version as a local `./mvnw`. Stage 2 is a JRE
# only — no Maven, no JDK, no build cache — running as a non-root user.
#
#   docker build -t medpay-backend ./backend
#
# Built by docker-compose.yml locally and pushed to ACR by the deploy workflow.
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1 — build
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# pom.xml alone first: dependency resolution is the slow half of the build, and
# this layer only invalidates when the pom changes, not on every source edit.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src

# Tests are skipped here on purpose. The suite is Testcontainers-based (it wants
# a Docker daemon) and it already gates the pipeline in CI before an image is
# ever built — running it again inside the build would need docker-in-docker.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

# Explode the fat jar into Spring Boot's layers so the runtime stage can copy
# them as separate, differently-churning Docker layers. Boot 4 does this with
# the `tools` jarmode; the old `layertools` jarmode was removed in Boot 3.3+.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# -----------------------------------------------------------------------------
# Stage 2 — runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Alpine's busybox supplies wget, which is what the compose healthcheck shells
# out to. A Debian-based JRE tag would need an extra apt-get for that.
WORKDIR /app

# Non-root. UID is fixed rather than left to adduser so it stays stable across
# rebuilds and matches any volume ownership set outside the image.
RUN addgroup -g 10001 -S medpay \
    && adduser -u 10001 -S medpay -G medpay \
    && chown medpay:medpay /app
USER 10001:10001

# Ordered least- to most-frequently-changed: dependencies almost never move,
# application classes change on every commit. Reversing these costs a full
# ~60 MB layer push per build.
COPY --from=build --chown=medpay:medpay /build/extracted/dependencies/ ./
COPY --from=build --chown=medpay:medpay /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=medpay:medpay /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=medpay:medpay /build/extracted/application/ ./

# Documentation only — compose does not publish this port. The backend is
# reachable solely through the frontend's /api/v1 proxy (ADR-006).
EXPOSE 8080

ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# exec form, no shell: PID 1 is the JVM, so `docker stop` delivers SIGTERM
# straight to it and Spring's graceful shutdown actually runs. `--launcher`
# extraction leaves no jar to point at, so the loader class is the entry point.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
