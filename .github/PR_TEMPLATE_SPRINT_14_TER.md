# Sprint 14 ter — Tests IT massifs + e2e + CI/CD complet

**Branche** : `feat/sprint-14-ter-tests-cicd`
**Base** : `feat/sprint-14-bis-code-debt`
**Avancement projet** : 67 % → **71 %** (+4 pts)
**Réfs.** : `docs/v2/PLAN_SPRINT_14_TER.md` + `docs/v2/AUDIT_SPRINT_14_TER_PREALABLE.md` + CLAUDE.md V3.7

---

## 📦 Périmètre

Itération finale de "validation industrielle" du projet : volume massif de tests d'intégration backend + tests e2e Playwright multi-navigateurs + pipeline CI/CD complet (déploiement staging + SonarQube + k6). **10 tâches plan ter**, **10 livrées en 3 sessions** (2026-05-23 + 2026-05-24).

| TASK | Sujet | Dette résorbée | Commit |
|---|---|---|---|
| 0 | Audit pré-ter + découverte sprint-8/sprint-10 jamais mergés | — | `7963d3f` |
| P1 | Merge `feat/sprint-8-comptable-fiscal` (rapatrie 2039 lignes + 6 IT Fiscal hérités) | — | `ad2c047` |
| P2 | Merge `feat/sprint-10-dashboards` (rapatrie 3000 lignes + 6 IT Dashboard hérités) | — | `ff0c9a3` |
| P3 | Validation `mvn compile` SUCCESS post-merges | — | (inline) |
| E1 | Setup Playwright multi-browser + fixtures + smoke + 6 scripts npm | D-S14-01 | `876ef7f` |
| F4 | **9 Dockerfiles backend multi-stage** + compose staging + deploy-staging workflow | D-S2-07 | `6fc9240` |
| F5 | smoke-staging.spec.ts + rollback-staging.sh | D-S14-05 | `fb7f359` |
| F3 | SonarQube descriptor + workflow Quality Gate bloquant | D-S14-04 | `e80052d` |
| F6 | k6/baseline.js 3 scénarios + PERF_BASELINE template | D-S14-06 | `17ca138` |
| (bug) | 3 fixes Spring DI : `@ConditionalOnBean` Rabbit + Noop publisher + SimpleMeterRegistry fallback | — | `ec16fcd` |
| C1 (1/2) | 8 IT classes Fiscal (38 nouveaux cas) + application-it.yml | D-S8-01 | `5b3a245` |
| C1 (2/2) | **44 / 44 IT Fiscal verts** (fix RLS app_no_super + sous-classif) | D-S8-01 | `f84582a` |
| C2 | **10 IT Dashboard** (cache + invalidation + anti-stampede + KPI roles) + workaround Lettuce/Netty Windows | D-S10-01 | `46a10c1` |
| E2 (prealable) | Endpoint backend `POST /api/v1/test/seed/workspace` + cleanup + route gateway | D-S14-02 | `3b3d53a` |
| E2 (1/4) | 3 scénarios Playwright auth (login + 2FA + recovery code) | D-S14-02 | `dccbbaa` |
| E2 (2/4) | 3 scénarios Playwright tickets CRUD + workflow | D-S14-02 | `417a8ba` |
| E2 (3/4) | 6 scénarios Playwright dataroom juridique + fiscal | D-S14-02 | `1b0ef8c` |
| E2 (4/4) | 2 scénarios Playwright dashboard par rôle | D-S14-02 | `ea3c641` |
| G | Doc finale CLAUDE V3.7 + ROADMAP 71 % + ce PR template + rapport `output/2026-05-23_06_sprint14_ter_clos.md` | — | (ce commit) |

---

## ✅ DoD

- [x] **44 / 44 IT Fiscal verts** : AccountantNotifier(3) + EcheancesGeneration(8) + ExerciceTransitions(6) + RetentionPurge(4) + AlertesEcheancesScheduler(4) + FiscalUpload(7) + FiscalListAndFilter(6) + FiscalSprint8IT hérité(6)
- [x] **10 / 10 nouveaux IT Dashboard verts** dans `DashboardExtendedIT` (cache miss/hit, invalidation par scope, anti-stampede 20 threads, RabbitMQ event listener, snapshot warm, RedisCacheManager, SUPER_ADMIN _global, Redis flush fallback) — validés par 3 batches (3+4+3)
- [x] **6 IT Dashboard hérités verts** : `DashboardSprint10IT`
- [x] **Cumul 60 IT TestContainers backend** post-ter (44 Fiscal + 16 Dashboard)
- [x] **14 scénarios e2e Playwright** dans 6 fichiers `.spec.ts` ciblant les composants RÉELS (LoginPage 3-steps, Setup2FAPage, RecoverWithCodePage, NewTicketDrawer, TicketDetailDrawer, WorkflowPage, DossierJuridiqueTab, PdfPreviewModal, DossierFiscalTab, FiscalUploadDrawer, EcheancesPanel, DashboardRouter)
- [x] **Endpoint backend `/api/v1/test/seed/workspace`** opérationnel avec double verrou sécurité (`@Profile("!prod")` + `@ConditionalOnProperty`)
- [x] **9 Dockerfiles backend multi-stage** propres + compose staging + workflow `deploy-staging.yml` + rollback automatique
- [x] **SonarQube** : descriptor + workflow Quality Gate bloquant (conditionné par secrets non vides)
- [x] **k6 baseline** : 3 scénarios constant-vus parallèles + template PERF_BASELINE
- [x] **Bug Spring DI structurel** corrigé : 3 fixes (`@ConditionalOnBean` Rabbit, fallback `SimpleMeterRegistry`, `NoopDataroomEventPublisher`)

---

## 📊 Tableau dettes — **26 / 26 résorbées**

| # | Dette | Sprint origine | Sévérité | Status pré-Sprint-14 | Status post-Sprint-14-ter |
|---|---|---|---|---|---|
| D-S1-01 | Smoke tests Sprint 1 manuels uniquement | 1 | M | 🔴 | ✅ couverts par F1 (workflow GitHub Actions build-test.yml) |
| D-S2-01 | `audit_log` Sprint 2 non testé en intégration | 2 | H | 🔴 | ✅ `AuthAuditableIT` 10 cas (bis) |
| D-S2-02 | `AuditAspect` AOP central jamais testé | 2 | H | 🔴 | ✅ `AuditAspectIT` 8 cas (bis) |
| D-S2-03 | `BusinessMetrics` non couvert par tests | 2 | M | ⚠️ | ✅ `BusinessMetricsTest` 16 tests (bis) |
| D-S2-04 | `BusinessMetrics.loginAttempted` non injecté dans use cases | 2 | M | 🔴 | ✅ injection dans `CreateTicketUseCase` (bis B3) |
| D-S2-05 | `BusinessMetrics.ticketStateTransition` absent | 2 | M | 🔴 | ✅ ajouté + injection `TransitionTicketUseCase` (bis B3) |
| D-S2-06 | `BusinessMetrics.documentUploaded` non injecté | 2 | M | 🔴 | ✅ injection `DataroomJuridiqueService.uploadVersion` (bis B3) |
| D-S2-07 | Dockerfiles backend factices / monolithique | 2 | H | 🔴 | ✅ 9 Dockerfiles multi-stage propres (ter F4) |
| D-S2-08 | Cardinality Prometheus non bornée (workspace_id) | 2 | H | 🔴 | ✅ hashing `workspace_bucket` 100 séries (bis B5) |
| D-S3-01 | `LoginUseCase.mustChangePassword` hardcoded false | 3 | M | 🔴 | ✅ aligné claim JWT mcp (Sprint 14 B1) |
| D-S3-03 | Endpoint `/auth/verify-recovery-code` absent | 3 | H | 🔴 | ✅ endpoint + UI + 5 IT + RG-AU41/42 (bis B4) |
| D-S7-01 | `DataroomMultitenancyIT` Sprint 7 incomplet (4 cas seulement) | 7 | M | ⚠️ | ✅ patterns FK seed corrigés (ter C1 prep) |
| D-S8-01 | Code Fiscal Sprint 8 livré sans 43 IT prévus | 8 | C | 🔴 | ✅ **44 / 44 IT Fiscal verts** (ter C1, sessions #2 + #3) |
| D-S8-02 | 0 test Vitest sur composants Sprint 8 (DossierFiscalTab, ComptableTab) | 8 | H | 🔴 | ✅ 10 tests Vitest (bis D3) |
| D-S10-01 | 0 IT cache Redis / invalidation Sprint 10 (placeholder seul) | 10 | C | 🔴 | ✅ **10 IT Dashboard verts** (ter C2) |
| D-S10-02 | 0 test Vitest sur composants Sprint 10 (Dashboards 4 rôles) | 10 | H | 🔴 | ✅ 8 tests Vitest (bis D4) |
| D-S14-01 | Pas de e2e multi-browser (Playwright absent) | 14 | C | 🔴 | ✅ Playwright 3 projects + fixtures (ter E1) |
| D-S14-02 | 0 scénario e2e métier | 14 | C | 🔴 | ✅ **14 scénarios e2e** dans 6 fichiers + endpoint seed (ter E2) |
| D-S14-03 | Pas de coverage report (JaCoCo + lcov) | 14 | M | ⚠️ | ✅ JaCoCo plugin + lcov frontend (Sprint 14 C5+D1) |
| D-S14-04 | Aucun Quality Gate SonarQube | 14 | M | 🔴 | ✅ workflow `sonar.yml` + 10 modules (ter F3) |
| D-S14-05 | Aucun déploiement staging automatique | 14 | C | 🔴 | ✅ `deploy-staging.yml` matrix + rollback (ter F4+F5) |
| D-S14-06 | Aucun baseline performance / load test | 14 | M | 🔴 | ✅ k6 baseline 3 scénarios + template PERF (ter F6) |
| D-S14-07 | Pas de GitHub Actions CI | 14 | C | 🔴 | ✅ `build-test.yml` 2 jobs (Sprint 14 F1) |
| D-S14-08 | Pas de security scan (gitleaks/Trivy) | 14 | M | 🔴 | ✅ `security.yml` gitleaks cron (Sprint 14 F2) |
| D-S14-09 | Pas de Dependabot | 14 | M | 🔴 | ✅ `.github/dependabot.yml` (Sprint 14 F7) |
| D-S14-10 | Pas de Guide_Tests centralisé | 14 | L | 🔴 | ✅ `Guide_Tests_V1.md` 10 sections (Sprint 14 G1) |

**Total : 26 dettes résorbées / 26 identifiées.** ✅

---

## 🧪 Test plan post-merge

- [ ] CI `build-test.yml` vert (1ère exécution post-merge)
- [ ] `mvn verify` local SUCCESS (jurika-common + auth + dataroom + dashboard + ticket)
- [ ] `npm run test:run` + `npm run typecheck` SUCCESS
- [ ] (manuel) `docker compose up` + `mvn -pl dataroom-service spring-boot:run -Djurika.test.seed.enabled=true` + `curl -X POST localhost:8080/api/v1/test/seed/workspace` retourne 200 + IDs
- [ ] (manuel) `npx playwright install --with-deps chromium firefox webkit` + `npm run e2e:chromium` après backend + frontend up

---

## 📋 Actions utilisateur post-merge

1. **VM staging Hetzner CX11** : provisionner + DNS `staging.jurika.ma` + user `deploy` + clé SSH GitHub `STAGING_SSH_KEY` + autres secrets (STAGING_HOST, STAGING_USER, SMOKE_PASSWORD, GHCR_TOKEN)
2. **SonarQube self-hosted** : `docker run -d sonarqube:community` (RAM 4GB) + créer projet `jurika-platform` + token + secrets repo `SONAR_TOKEN` + `SONAR_HOST_URL`
3. **Browsers Playwright** : `npx playwright install --with-deps chromium firefox webkit` (~300 MB)
4. **1er run staging** : push sur `main` → déclenche `deploy-staging` → smoke + récupère métriques k6 baseline → remplir `docs/v2/PERF_BASELINE_SPRINT_14_TER.md`
5. **Décision périmètre Sprint 15** : Mobile (16-17) ou Onboarding/Billing (11-13) ou Beta go-live (18) ?

---

## 🔗 Hors-scope explicite

- ❌ Sprint 9 RAG/notifications/ticket-service rework (stash `WIP-rag-notifications-hors-sprint-14-bis` préservé)
- ❌ Migration `workspace` → `workspace_bucket` Grafana dashboards (breaking, à planifier en Sprint 15)
- ❌ `InMemoryRecoveryCodeRateLimiter` → Redis (signature port inchangée, à planifier quand auth-service > 1 replica)
- ❌ Mobile React Native (sprints 16-17, optionnel V1)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
