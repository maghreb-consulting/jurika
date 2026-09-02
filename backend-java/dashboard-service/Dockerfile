# syntax=docker/dockerfile:1.6
# JURIKA dashboard-service ? multi-stage build (Sprint 14 ter F4, resorbe D-S2-07).
# Build :
#   cd backend-java && docker build -f dashboard-service/Dockerfile -t ghcr.io/ou55am1/jurika-dashboard:<tag> .

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
COPY pom.xml ./
COPY jurika-common/pom.xml jurika-common/
COPY dashboard-service/pom.xml dashboard-service/
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl :dashboard-service -am -DskipTests dependency:go-offline || true
COPY jurika-common/src jurika-common/src
COPY dashboard-service/src dashboard-service/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl :dashboard-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget tini \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1000 jurika \
    && useradd --system --uid 1000 --gid jurika --home /app jurika \
    && chown -R jurika:jurika /app
COPY --from=build --chown=jurika:jurika /build/dashboard-service/target/*.jar /app/app.jar
USER jurika
EXPOSE 8087
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8087/actuator/health || exit 1
ENTRYPOINT ["/usr/bin/tini", "--", "java", "-jar", "/app/app.jar"]
