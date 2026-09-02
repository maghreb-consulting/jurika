# syntax=docker/dockerfile:1.6
# JURIKA gateway-service ? multi-stage build (Sprint 14 ter F4, resorbe D-S2-07).
# Build :
#   cd backend-java && docker build -f gateway-service/Dockerfile -t ghcr.io/ou55am1/jurika-gateway:<tag> .

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
COPY pom.xml ./
COPY jurika-common/pom.xml jurika-common/
COPY gateway-service/pom.xml gateway-service/
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl :gateway-service -am -DskipTests dependency:go-offline || true
COPY jurika-common/src jurika-common/src
COPY gateway-service/src gateway-service/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl :gateway-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget tini \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1000 jurika \
    && useradd --system --uid 1000 --gid jurika --home /app jurika \
    && chown -R jurika:jurika /app
COPY --from=build --chown=jurika:jurika /build/gateway-service/target/*.jar /app/app.jar
USER jurika
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["/usr/bin/tini", "--", "java", "-jar", "/app/app.jar"]
