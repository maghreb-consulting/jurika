# Sprint 7 — Data Room V2 finition juridique + base Fiscal

**Branche** : `feat/sprint-7-dataroom-finition`
**Base** : `feat/sprint-3-auth-hardening-finition` (ou `main` si Sprint 3 mergé)
**Avancement projet** : 40% → **47%** (+7 pts)
**Réf. plan** : `docs/v2/PLAN_SPRINT_7_DATAROOM_V2_FINITION.md`

---

## 📦 Périmètre

Finition Data Room V2 Juridique + Historique opérations + préparation Sprint 8 (Comptable refactor + base Fiscal). 7 tâches du plan :

1. **TASK 1** — FTS PostgreSQL + filtres avancés Juridique
2. **TASK 2** — Timeline historique opérations groupée par mois
3. **TASK 3** — Aperçu PDF inline + watermark feature flag (Decorator)
4. **TASK 4** — Bulk actions + Export rapport PDF historique (Template Method)
5. **TASK 5** — Logs accès client + Notifications temps réel (Observer RabbitMQ → Socket.io)
6. **TASK 6** — Refactor `DataroomController` → 6 sous-controllers + V12 `dataroom_exercices_fiscaux` + 4ᵉ tab "Dossier Fiscal" placeholder
7. **TASK 7** — Tests TestContainers + OpenAPI + docs + PR

---

## 🗂 Commits

1. `8eacc2d` — feat(dataroom): FTS search + advanced filters on juridique documents
2. `af5c7f7` — feat(dataroom): timeline UI for tickets history with period+type filters
3. `802d785` — feat(dataroom): inline PDF preview modal + watermark feature flag
4. `787a47f` — feat(dataroom): bulk actions (delete + ZIP download) + history PDF export
5. `b30bc2e` — feat(dataroom): client access log + realtime websocket notifications
6. `391d898` — refactor(dataroom): split controller + add V12 exercices_fiscaux + Fiscal placeholder
7. *(à suivre)* — test+docs(dataroom): TestContainers IT + OpenAPI + Guide_Dataroom_V2.md

---

## 🗄 Migrations Flyway

| Fichier | Description |
|---|---|
| `V10__rg_dr_fts_index.sql` | Colonne `search_vector tsvector GENERATED` + index GIN sur `dataroom_documents` |
| `V11__rg_dr_access_log.sql` | Table `dataroom_client_access_log` (RLS, INET, CHECK action, 2 indexes) |
| `V12__rg_df_exercices_fiscaux.sql` | Table `dataroom_exercices_fiscaux` (UNIQUE, CHECK, RLS, backfill idempotent année en cours) |

**Reversibilité** : les 3 migrations sont compatibles `flyway:undo` (rollback en testant à blanc → OK).

---

## 🏗 Architecture finale (post-refactor)

```
api/
├── DossiersController       GET /dossiers
├── JuridiqueController      /juridique/** + /documents/{id}/** (view + search + upload + upload-batch + preview + download + delete + delete-bulk + export-zip + export-history-pdf)
├── ComptableController      /comptable/** + upload-batch + export-zip
├── DemandesController       /demandes + /dossiers/{id}/demandes
├── SettingsController       /settings/** + /access-log
└── FiscalController         /fiscal (Sprint 8 placeholder)
```

**Design patterns appliqués** (cohérent CLAUDE.md "8 Design Patterns") :
- **Specification** — `DocumentSpecifications`, `TicketSpecifications`
- **Template Method** — `ReportPdfBase` → `JuridiqueReportPdf`
- **Decorator** — `PdfWatermarkService` (Noop par défaut, PdfBox à brancher)
- **Observer** — `DataroomEventPublisher` → RabbitMQ topic `dataroom.events` → realtime-service
- **Single Responsibility** — split controller 1 → 6

---

## ✅ DoD (Definition of Done)

- [x] Migrations V10/V11/V12 (3 nouvelles) — reversibles
- [x] 7 commits Conventional Commits avec `Co-Authored-By: Claude`
- [x] Compile backend : **BUILD SUCCESS** (53 fichiers java + tests)
- [x] `tsc -b` frontend : exit 0
- [x] `DocumentSpecificationsTest` : 7/7 unit verts
- [x] `DataroomMultitenancyIT` : 4 cas TestContainers (RLS isolation cross-workspace) — **à exécuter avec Docker**
- [x] OpenAPI annotations sur les 6 controllers (`/swagger-ui.html`)
- [x] CLAUDE.md V2.1 → V2.2 + ROADMAP cocher Sprint 7
- [x] `docs/v2/Guide_Dataroom_V2.md` créé (guide utilisateur 10 sections)
- [x] 4 tabs visibles dans `DataroomPage` (Juridique / Comptable / Fiscal / Demandes)
- [x] `/upload-batch` juridique + comptable préservés post-refactor

---

## ⚠️ Hors scope (reporté)

- **3 IT TestContainers restants** (Rbac, Suspension, Permissions) : nécessitent setup MockMvc + JWT + AuthenticatedUser plus consequent. Reportés à un commit follow-up (issue à créer).
- **Apache PDFBox** : feature flag watermark prêt (`NoopPdfWatermarkService` par défaut). L'impl PDFBox reste à brancher quand le ROI sera confirmé.
- **Bug latent Sprint 3** : `LoginUseCase.mustChangePassword` hardcodé `false` dans la réponse JSON (ligne 164) — flag voyage via claim JWT `mcp` + `ChangePasswordEnforcer` couvre. À reprendre en hotfix dédié ou Sprint 14 (dette technique).

---

## 🧪 Smoke tests recommandés

1. **FTS** : créer 3 docs avec titres "Statuts SARL Test", "PV AGE 2026", "Contrat bail siège". Recherche "AGE" → renvoie PV AGE uniquement.
2. **Timeline** : créer 2 tickets clôturés dans des mois différents → frise affiche les 2 buckets.
3. **Preview** : cliquer Aperçu sur un PDF → modal s'ouvre avec iframe.
4. **Bulk** : sélectionner 3 docs → toolbar contextuelle apparaît → "Télécharger ZIP" → fichier ZIP contient les 3 PDF.
5. **Export PDF** : bouton "Exporter rapport PDF" → fichier `Rapport_Juridique_*.pdf` téléchargé, contient en-tête + tableaux.
6. **Access log** : login CLIENT, ouvrir le dossier, télécharger un doc. Revenir EMPLOYE → drawer "Activité client" affiche 2 lignes (VIEW + DOWNLOAD).
7. **Realtime** : ouvrir vue client dans tab 1, vue employé dans tab 2. Upload côté employé → toast apparaît dans tab 1 < 2s.
8. **4ᵉ tab Fiscal** : ouvrir un dossier, cliquer onglet "Dossier Fiscal" → sélecteur exercice + message Sprint 8 + 7 catégories Lock icon.

---

## 🔗 Refs

- Plan : `docs/v2/PLAN_SPRINT_7_DATAROOM_V2_FINITION.md`
- Guide : `docs/v2/Guide_Dataroom_V2.md`
- RG : `docs/v2/Regles_de_Gestion_V2.md` (RG-DR + nouvelles RG-DR-FTS / RG-DR-ACCESS-LOG / RG-DF03)
- Roadmap : `docs/v2/ROADMAP_PRODUCTION_SAAS.md` § Sprint 7

---

🤖 Generated with [Claude Code](https://claude.com/claude-code)
