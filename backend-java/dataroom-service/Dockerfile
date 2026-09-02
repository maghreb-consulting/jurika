# syntax=docker/dockerfile:1.6
# JURIKA dataroom-service ? multi-stage build (Sprint 14 ter F4, resorbe D-S2-07).
#
# Build :
#   cd backend-java
#   docker build -f dataroom-service/Dockerfile -t ghcr.io/ou55am1/jurika-dataroom:<tag> .
#
# Le contexte DOIT etre backend-java/ (parent multi-module) pour resoudre jurika-common.

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
COPY pom.xml ./
COPY jurika-common/pom.xml jurika-common/
COPY dataroom-service/pom.xml dataroom-service/
# Pre-resolve deps -> couche cachable
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl :dataroom-service -am -DskipTests dependency:go-offline || true
COPY jurika-common/src jurika-common/src
COPY dataroom-service/src dataroom-service/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl :dataroom-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget tini \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1000 jurika \
    && useradd --system --uid 1000 --gid jurika --home /app jurika \
    && chown -R jurika:jurika /app
COPY --from=build --chown=jurika:jurika /build/dataroom-service/target/*.jar /app/app.jar
USER jurika
EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1
ENTRYPOINT ["/usr/bin/tini", "--", "java", "-jar", "/app/app.jar"]
