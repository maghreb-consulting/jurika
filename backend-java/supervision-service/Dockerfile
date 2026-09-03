# syntax=docker/dockerfile:1.6
# JURIKA supervision-service ? multi-stage build (Sprint 14 ter F4, resorbe D-S2-07).
# Build :
#   cd backend-java && docker build -f supervision-service/Dockerfile -t ghcr.io/ou55am1/jurika-supervision:<tag> .

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
COPY pom.xml ./
# Le POM parent declare 11 modules : Maven charge le reacteur complet avant
# d'appliquer -pl, et echoue si un module declare est absent. Tous les POM
# enfants sont donc requis, meme pour construire un seul service.
COPY jurika-common/pom.xml        jurika-common/
COPY discovery-service/pom.xml    discovery-service/
COPY gateway-service/pom.xml      gateway-service/
COPY auth-service/pom.xml         auth-service/
COPY ticket-service/pom.xml       ticket-service/
COPY workflow-service/pom.xml     workflow-service/
COPY dataroom-service/pom.xml     dataroom-service/
COPY supervision-service/pom.xml  supervision-service/
COPY ai-service/pom.xml           ai-service/
COPY dashboard-service/pom.xml    dashboard-service/
COPY billing-service/pom.xml      billing-service/
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl :supervision-service -am -DskipTests dependency:go-offline || true
COPY jurika-common/src jurika-common/src
COPY supervision-service/src supervision-service/src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl :supervision-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget tini \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1000 jurika \
    && useradd --system --uid 1000 --gid jurika --home /app jurika \
    && chown -R jurika:jurika /app
COPY --from=build --chown=jurika:jurika /build/supervision-service/target/*.jar /app/app.jar
USER jurika
EXPOSE 8086
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8086/actuator/health || exit 1
ENTRYPOINT ["/usr/bin/tini", "--", "java", "-jar", "/app/app.jar"]
