-- Sprint 12.5/12.6 — Theme prefere par workspace (mode UI light/dark).
--
-- Objectif : permettre au theme switcher (front) de persister son etat cote
-- DB en plus du localStorage, pour que la preference suive l'utilisateur a
-- travers les devices.
--
-- Numerotation : V22 (V20/V21 reserves au billing-service / dashboard-service
-- en cas de migrations Sprint 12 quater -- accepte par Flyway, gaps autorises
-- par module).
--
-- Decisions :
--   - Default 'light' = Strategie A confirmee par user 2026-06-01 (clone
--     marketing-site editorial). Cf pivot dans
--     output/PLAN_SPRINT_12-5_THEME_ALIGNMENT.md.
--   - VARCHAR(10) suffit pour 'dark' / 'light' + marge pour valeurs futures
--     ('auto'-system, 'sepia'-editorial, etc.).
--   - CHECK contrainte sur les 2 valeurs courantes -- a etendre quand 'auto'
--     ou autres modes seront introduits (Sprint 13+).
--   - Pas d'INDEX : champ accede uniquement via /users/me/theme (lecture
--     point par workspace_id, jamais filtre/sort).

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS preferred_theme VARCHAR(10) NOT NULL DEFAULT 'light';

ALTER TABLE workspaces
    ADD CONSTRAINT chk_workspace_preferred_theme
    CHECK (preferred_theme IN ('dark', 'light'));

COMMENT ON COLUMN workspaces.preferred_theme IS
    'Sprint 12.5/12.6 -- Mode UI prefere : light (defaut, velin editorial marketing) '
    'ou dark (option, navy studio). Persistee par workspace (donc par cabinet). '
    'Sync localStorage front <-> POST /users/me/theme. Extendable a auto/sepia en '
    'Sprint 13+ (penser a relacher la contrainte CHECK).';
