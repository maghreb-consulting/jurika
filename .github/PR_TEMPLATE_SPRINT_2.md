## Sprint 2 — Observabilité + Audit log

Plan source : [`docs/v2/PLAN_SPRINT_2_OBSERVABILITE.md`](../docs/v2/PLAN_SPRINT_2_OBSERVABILITE.md)
Roadmap : [`docs/v2/ROADMAP_PRODUCTION_SAAS.md § 4 Sprint 2`](../docs/v2/ROADMAP_PRODUCTION_SAAS.md)

### Périmètre livré (7/7 tasks du plan)

- [x] **TASK 1** Logs JSON structurés + Correlation ID (logback-spring.xml partagé, `CorrelationIdFilter` servlet + `CorrelationIdWebFilter` gateway, MDC enrichi via `JwtAuthFilter`)
- [x] **TASK 2** Micrometer Prometheus + Grafana (3 dashboards Overview/Business/JVM, `BusinessMetrics` facade, `OpsActuatorSecurityAutoConfiguration` HTTP Basic ops)
- [x] **TASK 3** OpenTelemetry agent + Tempo (auto-instrumentation Spring/JDBC/Rabbit/JJWT, sampling paramétrable, corrélation log↔trace bi-directionnelle, Service Graph)
- [x] **TASK 4** Loki + Promtail (logs Docker JSON, dashboard "JURIKA Logs" filtrable correlationId/workspaceId)
- [x] **TASK 5** Sentry Spring (6 services servlet) + React (avec `replayIntegration` masking + `Sentry.ErrorBoundary` + RGPD scrubbing)
- [x] **TASK 6** Audit log universel : `@Auditable` AOP + `@AfterThrowing`, table `audit_log` RLS multi-tenant, RabbitMQ async, endpoint admin `/api/v1/admin/audit` + page React
- [x] **TASK 7** Health checks K8s : groupes `liveness`/`readiness` distincts, `RabbitMQHealthIndicator` + `MinIOHealthIndicator`, graceful shutdown 30s, `docker-compose.services.yml`
- [x] **TASK 8** Tests : `BusinessMetricsTest` (8), `AdminAuditControllerTest` (5), tests existants conservés. Smoke tests `sprint-2-observability.http` (8 étapes)

### Statistiques

```
mvn test (jurika-common + auth-service)
  jurika-common      → 31/31 verts (CorrelationIdFilter, MinIOHealth, RabbitMQHealth,
                       JwtAuthFilterRsa, JwtKeyConfig, DnsMxValidator, BusinessMetrics)
  auth-service       → 106/106 verts (LoginUseCase, SessionLimitEnforcer, JwtTokenIssuerRsa,
                       AdminAuditController, ChangePassword, CheckWorkspace, …)
  Total              → 137/137 verts
```

`mvn compile` SUCCESS sur les 9 modules. `npx tsc --noEmit` clean côté frontend.

### Migrations DB

- `V8__audit_log_table.sql` (auth-service) — table + RLS + indexes + bypass policy
- `V9__audit_log_table.sql` (dataroom-service) — idempotent (ALTER IF NOT EXISTS)
- `V13__audit_log_table.sql` (ai-service) — idempotent

Migrations idempotentes : le DB partagé `jurika_db` héberge une seule table physique `audit_log`. La première migration à passer crée les colonnes Sprint 2, les suivantes sont no-ops.

### Nouveaux fichiers d'infrastructure

- `infrastructure/monitoring/docker-compose.monitoring.yml` — Prometheus + Grafana + Loki + Promtail + Tempo (tous loopback only)
- `infrastructure/monitoring/prometheus.yml` — 7 cibles avec basic_auth ops
- `infrastructure/monitoring/loki.yml`, `promtail.yml`, `tempo.yml`
- `infrastructure/monitoring/grafana/provisioning/{datasources,dashboards}/`
- `infrastructure/monitoring/grafana/dashboards/jurika-{overview,business,jvm,logs}.json`
- `infrastructure/monitoring/opentelemetry/{download-otel-agent.sh,Dockerfile.template,.gitignore}`
- `infrastructure/monitoring/README.md` — runbook + warnings sécurité

### Breaking changes

⚠️ `LoginUseCase` et `RefreshTokenUseCase` ont un paramètre constructeur supplémentaire (`BusinessMetrics`). Spring DI résout automatiquement, mais les tests injectent maintenant un `SimpleMeterRegistry` via `new BusinessMetrics(...)`.

⚠️ Les 7 `application.yml` services exposent maintenant `/actuator/prometheus` et `loggers`, `env`. Si `PROMETHEUS_OPS_PASSWORD` est défini, ces endpoints exigent HTTP Basic `ops`/`<password>`. **En dev sans monitoring, laisser vide = endpoints publics.**

⚠️ Frontend `package.json` : `@sentry/react@^8.55.0` ajouté. `npm install` requis après merge.

### Sécurité

- `OpsActuatorSecurityAutoConfiguration` (HIGHEST_PRECEDENCE) sécurise `/actuator/**` sans toucher les chaînes existantes
- Sentry `send-default-pii=false` + `beforeSend` strip email/IP/cookies (RGPD)
- Grafana bindé sur `127.0.0.1` uniquement (tunnel SSH/WireGuard en prod)
- Audit log RLS multi-tenant avec bypass SUPER_ADMIN (pas de fuite cross-workspace par défaut)

### Démarrage local après merge

```powershell
# 1. Reinstaller deps frontend (nouveau @sentry/react)
cd frontend-react ; npm install ; cd ..

# 2. Telecharger l'agent OTel (une fois)
bash infrastructure/monitoring/opentelemetry/download-otel-agent.sh

# 3. Demarrer la stack
.\scripts\start-all.ps1

# 4. (Optionnel) Demarrer la stack monitoring
$env:PROMETHEUS_OPS_PASSWORD = "..."
$env:GRAFANA_ADMIN_PASSWORD = "..."
docker compose -f infrastructure/docker-compose.yml `
               -f infrastructure/monitoring/docker-compose.monitoring.yml up -d

# 5. Acceder a Grafana
start http://localhost:3001/  # admin / $env:GRAFANA_ADMIN_PASSWORD
```

### Validation post-déploiement (prod)

- [ ] Login depuis prod → `X-Correlation-Id` propagé en réponse
- [ ] Token Bearer → `/api/v1/admin/audit` SUPER_ADMIN retourne LOGIN_SUCCESS
- [ ] `curl -u ops:$PROM_PASS https://api.jurika.ma/actuator/prometheus` retourne format Prometheus
- [ ] Grafana via tunnel SSH affiche les 4 dashboards
- [ ] LogQL `{service="auth-service"} |= "LOGIN_SUCCESS"` retourne logs dans Loki
- [ ] Tempo Service Graph affiche `gateway → auth-service → postgresql`
- [ ] Forcer NullPointerException → événement visible dans Sentry avec tag `workspace`

### Suivi roadmap

- ROADMAP § 4 Sprint 2 cochée ✅
- § 5 avancement : 28% → **35%**
- § 6 decisions log : 8 entrées 2026-05-22 ajoutées

### Dette technique reportée Sprint 14

- 7 use cases auth (Login/Logout/ChangePassword/...) appellent déjà `auditLogger.log()` explicitement → pas re-annotés `@Auditable` pour éviter double-écriture. Consolidation à faire (choisir UNE source de vérité).
- Sentry gateway WebFlux (incompatible jakarta starter actuel) — à brancher avec `sentry-reactor`.
- `BusinessMetrics` injection dans `CreateTicketUseCase`, `DataroomJuridiqueService.uploadVersion`, workflow Timer.
- Tests `AuditAspectIT` TestContainers (DB + RabbitMQ) — TestContainers absent du repo pour l'instant.

### Liens

- [Plan Sprint 2](../docs/v2/PLAN_SPRINT_2_OBSERVABILITE.md)
- [Roadmap master](../docs/v2/ROADMAP_PRODUCTION_SAAS.md)
- [Smoke tests](../tests/integration/sprint-2-observability.http)
- [Monitoring runbook](../infrastructure/monitoring/README.md)
