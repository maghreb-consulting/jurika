-- Onboarding 2026-06-24 — Type de profil declare a l'inscription.
--
-- Jusqu'ici l'inscription ne gerait implicitement que le "cabinet". On ajoute
-- une colonne `professional_type` portant l'une des 8 categories metier
-- (ProfessionalType.java cote domaine).
--
-- NULLABLE par conception : les workspaces crees avant cette evolution n'ont
-- aucun type (legitime). On NE backfill PAS de force — un workspace pre-existant
-- reste valide avec professional_type = NULL. Le nouveau front envoie toujours
-- une valeur (defaut UI cote signup wizard).
--
-- RLS / multitenancy : aucune incidence. La table workspaces est la racine de
-- tenant (non soumise a la policy app.current_workspace_id qui s'applique aux
-- tables filles). Ajouter une colonne scalaire nullable ne touche ni les
-- policies RLS existantes ni l'isolation cross-workspace.
--
-- Ordre des migrations : derniere migration auth-service = V28
-- (login_email_contact_email). V29 est le numero suivant disponible.

ALTER TABLE workspaces
    ADD COLUMN IF NOT EXISTS professional_type VARCHAR(30);

-- Check : valeur dans l'enum des 8 types (NULL toleree pour workspaces legacy).
ALTER TABLE workspaces DROP CONSTRAINT IF EXISTS workspaces_professional_type_check;
ALTER TABLE workspaces ADD CONSTRAINT workspaces_professional_type_check
    CHECK (professional_type IS NULL OR professional_type IN (
        'COMPTABLE_AGREE',
        'CONSEILLER_JURIDIQUE',
        'CENTRE_AFFAIRES',
        'EXPERT_COMPTABLE',
        'ENTREPRISE',
        'AVOCAT',
        'NOTAIRE',
        'AUTRE'
    ));

COMMENT ON COLUMN workspaces.professional_type IS
    'Onboarding 2026-06-24 — type de profil declare au signup (8 valeurs ProfessionalType, NULL = workspace pre-evolution)';
