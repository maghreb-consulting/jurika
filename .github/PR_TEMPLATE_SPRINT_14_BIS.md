# Sprint 14 bis — Code dette + tests d'integration

**Branche** : `feat/sprint-14-bis-code-debt`
**Base** : `feat/sprint-14-consolidation-ci-cd`
**Avancement projet** : 64 % → **67 %** (+3 pts)
**Réfs.** : `docs/v2/AUDIT_SPRINT_14_BIS_PREALABLE.md` + CLAUDE.md V3.4

---

## 📦 Périmètre

Itération de "rattrapage" qui absorbe la dette identifiée dans `AUDIT_SPRINT_14_DETTE.md` (commit 1) — code source de production manquant + tests d'intégration absents + 0 test frontend sur Sprint 7/8/10. **11 tâches** au plan, **11 livrées en 2 sessions** (sessions du 2026-05-23) :

| TASK | Sujet | Dette résorbée | Commit |
|---|---|---|---|
| 0 | Audit pré-bis + découverte écarts plan/réalité | — | `ee3633b` + `4a64869` |
| — | Fix orphan `NotificationAutoConfiguration` (bug structurel découvert) | — | `83b9802` |
| B2 | `AuthAuditableIT` 10 cas TestContainers + fix `REQUIRES_NEW` `AuditLogAdapter` | D-S2-01 | `3b58b8d` |
| B5 | Cardinality Prometheus `workspace_bucket` hashing | D-S2-08 | `a7b760d` |
| B3 | `BusinessMetrics` injecté CreateTicket + Transition + uploadVersion + nouveau `ticketStateTransition` | D-S2-04/05/06 | `62d9cd3` |
| C4 | `BusinessMetricsTest` 16 tests (cible ≥ 15) | D-S2-03 (faux positif déjà OK) | livré en B3+B5 |
| B4 | Endpoint `POST /auth/verify-recovery-code` + UI `RecoverWithCodePage` + 5 IT + RG-AU41/42 | D-S3-03 | `02b47d1` |
| C3 | `AuditAspectIT` TestContainers 8 cas (AOP, RLS, RabbitMQ, fallback, MDC) | D-S2-02 | `038cb23` |
| D2+D3+D4 | 30 tests Vitest frontend (auth + dataroom + dashboard) | D-S3-03 + D-S8-02 + D-S10-02 | `103d769` |
| G | Doc finale RG-AU41/42 + Guide_Tests Vitest + CLAUDE.md + PR template + rapport `output/2026-05-23_03_sprint14_bis_clos.md` | — | (ce commit) |

---

## ✅ DoD

- [x] **5 IT B4 verts** : `RecoveryCodeVerificationIT` (succès, code inconnu, code déjà utilisé, rate limit 429, email inconnu) — 93s
- [x] **8 IT C3 verts** : `AuditAspectIT` (standard, resourceType+id, exception, RLS, audit_bypass, Rabbit async, fallback JDBC, correlation ID) — 24s
- [x] **10 IT B2 verts** : `AuthAuditableIT` (10 actions auth) — 51s
- [x] **Cumul 23 nouveaux IT TestContainers** + 16 unit `BusinessMetricsTest`
- [x] **32 tests Vitest verts** (2 smoke + 12 auth + 10 dataroom + 8 dashboard) — 5s
- [x] `mvn verify` SUCCESS sur jurika-common + auth-service
- [x] `npm run test:run` SUCCESS + `npm run typecheck` SUCCESS
- [x] **9 dettes résorbées** : D-S2-01/02/03/04/05/06/08 + D-S3-03 + D-S8-02 + D-S10-02
- [x] RG-AU41 (single-use) + RG-AU42 (rate limit) ajoutées à `Regles_de_Gestion_V2.md`
- [x] Section "Patterns Vitest stabilisés" ajoutée à `Guide_Tests_V1.md`
- [x] CLAUDE.md V3.4 finalisée (sprint bis complet)
- [x] Stash `WIP-rag-notifications-hors-sprint-14-bis` **préservé intact** (contient Sprint 9 RAG hors scope)

---

## 🆕 Nouveaux endpoints + UI

### `POST /api/v1/auth/verify-recovery-code` (B4 — RG-AU41/42)
- Public (permitAll) — court-circuit du TOTP/SMS via un code single-use BCrypt
- Rate limit applicatif **5/15min** par `(workspaceCode + email + ip)` ⇒ `429 RECOVERY_CODE_RATE_LIMITED`
- Audit : `RECOVERY_CODE_USED` (succès, metadata.remaining) ou `RECOVERY_CODE_FAILED` (échec, metadata.reason ∈ {USER_UNKNOWN, NO_ACTIVE_CODES, CODE_MISMATCH, RATE_LIMITED})
- Email d'alerte `recovery-code-used.html` envoyé au user après succès (RG-SAAS-08)

### `GET /auth/recover-with-code` (UI 2 étapes)
- Étape 1 : workspace code + email
- Étape 2 : `RecoveryCodeForm` (auto-format XXXX-XXXX-XXXX-XXXX, normalisation uppercase + strip non-alphanum)
- Lien ajouté depuis l'étape 2FA du `LoginPage`

---

## 🗄 Schéma BD / migrations

**Aucune nouvelle migration** — l'infrastructure recovery codes (Entity + Repo + Generator + V4) existait depuis Sprint 3, seul le UseCase + Controller manquaient.

---

## 🏗 Architecture cohérence (vérifiée pendant Sprint 14 bis)

### Audit log universel — pattern réel découvert
- Port `AuditLogger` (auth-service) ↔ adapter `AuditLogAdapter` ↔ table `audit_log` constitue **un mécanisme d'audit complet** parallèle à `@Auditable` (jurika-common). 14/16 use cases auth émettent **déjà** 22 events dans `audit_log` via cette voie manuelle.
- **Décision Option C validée** : ne PAS migrer le code manuel vers `@Auditable` (créerait doublons + perte de métadonnées richesse IP/UA/attempts/otpId). Résorption D-S2-01 par PREUVE via `AuthAuditableIT`.
- Fix prod découvert pendant les tests : `AuditLogAdapter.@Transactional(REQUIRES_NEW)` (sans REQUIRES_NEW, l'audit `LOGIN_FAILED` était rollback avec l'exception métier). Aligné sur `JdbcAuditEventEmitter`.

### BusinessMetrics — cardinality Prometheus
- Helper `workspaceBucket(UUID)` : `Math.abs(uuid.hashCode()) % 100` ⇒ tag `workspace_bucket` (au lieu de `workspace`)
- Borne la cardinalité à **100 séries** indépendamment du nombre de workspaces (au lieu de N par tenant)
- ⚠️ Breaking pour dashboards Grafana Sprint 2 "Business" — à documenter Sprint 14 ter (les requêtes par UUID exacte ne marcheront plus)

### Rate limiter recovery
- `RecoveryCodeRateLimiter` port + `InMemoryRecoveryCodeRateLimiter` (sliding window, ConcurrentHashMap → Deque<Instant>)
- ⚠️ Single-instance only — migration Redis prévue Sprint 14 ter (la signature du port ne change pas)

---

## 🧪 Suite tests (rappel cumul Sprint 14 bis)

| Module | Type | Cas | Durée |
|---|---|:---:|:---:|
| `BusinessMetricsTest` (jurika-common) | unit | 16 | < 1s |
| `AuditAspectIT` (jurika-common) | IT Postgres | 8 | 24s |
| `AuthAuditableIT` (auth-service) | IT Postgres | 10 | 51s |
| `RecoveryCodeVerificationIT` (auth-service) | IT Postgres | 5 | 93s |
| Vitest D2 — auth | composants | 12 | < 1s |
| Vitest D3 — dataroom | composants | 10 | < 1s |
| Vitest D4 — dashboard | composants | 8 | < 1s |
| **Total Sprint 14 bis** | | **69** | **~3min** |

---

## ⚠️ Hors scope (reporté Sprint 14 ter)

- **C1** : ~40 IT Fiscal (placeholder Sprint 8 toujours valide)
- **C2** : +10 IT Dashboard supervision-service
- **E1/E2** : Playwright multi-browser + 12 e2e
- **F3** : SonarQube
- **F4/F5** : deploy-staging + smoke
- **F6** : k6 baseline
- **Composants Sprint 8/10 manquants** mentionnés au plan B4-D2 mais inexistants en code : `FiscalUploadDrawer`, `EcheancesPanel`, `EvolutionChart`, `DistributionChart`, `DashboardEmpty`, `useDashboard` hook → à implémenter Sprint 14 ter (ou Sprint 8 finition / Sprint 10 finition)
- **Migration `workspace_bucket`** Grafana dashboards Sprint 2 → doc note Sprint 14 ter
- **`InMemoryRecoveryCodeRateLimiter` → Redis** quand auth-service > 1 replica
- **Stash `WIP-rag-notifications-hors-sprint-14-bis`** : Sprint 9 (RAG + notifications + ticket-service rework) à reprendre proprement Sprint 9 dédié

---

## 🧪 Smoke tests recommandés (UI + API)

1. **Recovery code happy path** : activer 2FA → noter les 10 codes → naviguer vers `/auth/recover-with-code` → saisir `JUR-DEMO1` + email + un des codes → redirige vers `/dashboard` + email `recovery-code-used` reçu.
2. **Rate limit** : 6 essais consécutifs avec code bidon depuis le même couple email+IP → le 6e renvoie `429 RECOVERY_CODE_RATE_LIMITED`.
3. **Single-use** : utiliser un code 2x → la 2e tentative renvoie `401`.
4. **Audit visible** : ouvrir `/admin/audit` (SUPER_ADMIN) → filter `action=RECOVERY_CODE_*` → lignes USED et FAILED présentes.
5. **AuditAspect AOP** : créer un ticket via `/tickets/POST` → ligne `TICKET_CREATED` dans `audit_log` avec `correlation_id` (= MDC), `source_service=ticket-service`, `metadata.method`.

---

## 🔗 Refs

- Audit pré-bis : `docs/v2/AUDIT_SPRINT_14_BIS_PREALABLE.md`
- Audit dette: `docs/v2/AUDIT_SPRINT_14_DETTE.md`
- Guide tests : `docs/v2/Guide_Tests_V1.md` (section "Patterns Vitest stabilisés Sprint 14 bis")
- RG mises à jour : `docs/v2/Regles_de_Gestion_V2.md` (RG-AU41 + RG-AU42)
- Rapport clos : `output/2026-05-23_03_sprint14_bis_clos.md`
- Roadmap : `docs/v2/ROADMAP_PRODUCTION_SAAS.md` § Sprint 14 bis ✅ + § Sprint 14 ter (à venir)

---

🤖 Generated with [Claude Code](https://claude.com/claude-code)
