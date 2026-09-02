# JURIKA — Stack monitoring (Sprint 2 / Lot C)

> Stack séparée du compose applicatif pour pouvoir la couper en dev léger.

| Composant | Port (host) | Rôle |
|---|---|---|
| Prometheus | 9090 | TSDB métriques |
| Grafana | 3001 (loopback only) | Dashboards |
| Loki | 3100 (loopback only) | Logs centralisés |
| Promtail | — | Scraper Docker logs |
| Tempo | 3200 / 4318 / 4317 (loopback only) | Traces OpenTelemetry |

## Démarrage

```powershell
# 1. La stack applicative DOIT etre demarree d'abord (reseau jurika-network).
docker compose -f infrastructure/docker-compose.yml up -d

# 2. Stack monitoring
$env:PROMETHEUS_OPS_PASSWORD = "mot-de-passe-fort-genere-via-doppler"
$env:GRAFANA_ADMIN_PASSWORD = "autre-mot-de-passe-fort"
docker compose -f infrastructure/docker-compose.yml `
               -f infrastructure/monitoring/docker-compose.monitoring.yml up -d
```

Vérification :

```powershell
curl http://localhost:9090/-/ready      # Prometheus pret
curl http://localhost:9090/targets      # 7 cibles UP
start http://localhost:3001/            # Grafana (admin / $env:GRAFANA_ADMIN_PASSWORD)
```

## Configuration des services

Chaque service Java déclare :

```yaml
jurika:
  observability:
    prometheus:
      password: ${PROMETHEUS_OPS_PASSWORD:}
```

Si la variable est définie, `OpsActuatorSecurityAutoConfiguration` (jurika-common) active une chaîne de sécurité dédiée à `/actuator/**` qui exige HTTP Basic `ops` / `${PROMETHEUS_OPS_PASSWORD}` (rôle `ROLE_OPS`).

Si la variable est vide (dev local sans monitoring), les endpoints actuator restent publics — c'est le comportement par défaut Spring Boot et c'est sans risque tant que le port n'est pas exposé sur Internet.

## Dashboards provisionnés

| Dashboard | UID | Description |
|---|---|---|
| JURIKA Overview | `jurika-overview` | Latence p95/p99, throughput, error rate par service |
| JURIKA Business | `jurika-business` | Logins, refresh tokens, tickets, documents (compteurs business) |
| JURIKA JVM | `jurika-jvm` | Heap, threads, GC pause, CPU |
| JURIKA Logs | `jurika-logs` | Logs Loki filtrables par service/level/correlationId/workspaceId |

## Logs (Loki)

Promtail scrape les logs Docker de tous les conteneurs sur `jurika-network`. Le pipeline tente un parse JSON (profil `prod`/`staging`, logback-spring.xml émet du JSON) et extrait `level`, `service`, `correlationId`, `workspaceId`, `trace_id`, `span_id` :

- `level` et `service` sont indexés en labels Loki (low-cardinality, query rapide).
- `correlationId`, `workspaceId`, `trace_id`, `span_id` sont en *structured metadata* (cardinality élevée acceptable, requêtables via filtre JSON).

Exemples de requêtes LogQL :

```logql
# Tous les ERROR auth-service de la dernière heure
{service="auth-service", level="ERROR"}

# Trace d'une requête bout-en-bout via correlationId
{service=~".*"} | json | correlationId="abc-123-def"

# Logs d'un workspace particulier
{service=~".*"} | json | workspaceId="00000000-0000-0000-0000-000000000001"
```

Le dashboard "JURIKA Logs" expose ces filtres dans la UI via variables Grafana.

## Traces (OpenTelemetry + Tempo)

Chaque service Java embarque l'agent OpenTelemetry **auto-instrumentation** (jar `opentelemetry-javaagent.jar`, ~25 MB, gitignoré). Il instrumente automatiquement Spring MVC, Spring WebFlux, JDBC, RabbitMQ, JJWT, etc. — aucun code applicatif à modifier.

### Setup poste de dev (UNE FOIS)

```bash
bash infrastructure/monitoring/opentelemetry/download-otel-agent.sh
```

Le jar atterrit dans `infrastructure/monitoring/opentelemetry/opentelemetry-javaagent.jar`. Le Dockerfile de chaque service (Sprint 15) copiera ce jar à `/app/otel.jar` et activera `-javaagent:/app/otel.jar`.

### Configuration ENV (déjà câblée dans docker-compose.services.yml)

| Variable | Dev | Staging | Prod |
|---|---|---|---|
| `OTEL_TRACES_SAMPLER` | `parentbased_traceidratio` | idem | idem |
| `OTEL_TRACES_SAMPLER_ARG` | `1.0` (100%) | `0.5` | `0.1` (10%) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://tempo:4318` | idem | idem |
| `OTEL_PROPAGATORS` | `tracecontext,baggage,b3` | idem | idem |

Surcharger en prod via Doppler/AWS SM :
```bash
export OTEL_TRACES_SAMPLER_ARG=0.1
```

### Corrélation log ↔ trace

L'agent OTel injecte automatiquement `trace_id` + `span_id` dans le MDC SLF4J. Le `logback-spring.xml` (Lot A TASK 1) les inclut dans le JSON émis. Promtail les extrait en *structured metadata* et la datasource Loki utilise `derivedFields` pour afficher un bouton **"View Trace"** sur chaque ligne de log. Inversement, depuis Tempo, **"Logs for this span"** ouvre Loki filtré par `trace_id`.

### Service graph

Tempo `metrics_generator` calcule un service-graph en RED (Rate-Errors-Duration) qu'on visualise dans Grafana → Explore → Tempo → Service Graph. Utile pour repérer les hot-paths et les dépendances inter-services.

## ⚠️ Sécurité production

- **Grafana NE DOIT JAMAIS être exposé publiquement.** Le port 3001 est bind sur `127.0.0.1` uniquement (loopback). Accès distant : tunnel SSH `ssh -L 3001:localhost:3001 prod` ou WireGuard.
- **Prometheus retient 30j par défaut** (`PROMETHEUS_RETENTION`). Pour la prod long-terme : configurer Thanos ou Cortex (post-V1).
- **Cardinalité** : le tag `workspace` sur les compteurs business est sûr tant que < ~1000 workspaces actifs. Au-delà, basculer vers un bucket `other` ou aggrégat avant publication.

## Risques connus

| Risque | Mitigation |
|---|---|
| `PROMETHEUS_OPS_PASSWORD` fuite | Toujours via Doppler/AWS SM en prod, jamais en clair dans .env committé. |
| Grafana exposé sans VPN | Vérifier `ports: ["127.0.0.1:3001:3000"]` à chaque modification du compose. |
| Disque saturé par TSDB | `--storage.tsdb.retention.time` + alerte espace disque Prometheus self-monitoring. |
| Cardinalité explosion (workspace tag) | Documentation BusinessMetrics.java + alerte sur `prometheus_tsdb_symbol_table_size_bytes`. |

## Diagnostic

```powershell
# Logs Prometheus
docker logs jurika-prometheus -f

# Recharger config sans redemarrer
curl -X POST http://localhost:9090/-/reload

# Tester manuellement /actuator/prometheus depuis le reseau Docker
docker exec -it jurika-prometheus wget -qO- --header="Authorization: Basic $(echo -n ops:$PROMETHEUS_OPS_PASSWORD | base64)" http://auth-service:8081/actuator/prometheus
```
