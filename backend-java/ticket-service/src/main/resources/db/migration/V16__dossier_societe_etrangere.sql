-- =====================================================================
-- JURIKA V16 — Dossier « société mère ÉTRANGÈRE » (lot DIVERS §C, 2026-08-13)
-- =====================================================================
-- CAUSE RACINE. Le workflow SUCCURSALE_ETR crée au Maroc la succursale d'une
-- société étrangère. Cette société mère n'existe NULLE PART en base : elle est
-- saisie à chaque workflow, ses actes (PV, annonce, pièces) n'ont aucune Data
-- Room de destination, et V15 le documentait déjà comme une limite assumée —
--
--     « Pour une succursale ETR sans dossier local rattache, la ligne n'est PAS
--       creee (contrainte NOT NULL) -> la succursale reste en saisie manuelle. »
--
-- Conséquences concrètes : (1) aucune Data Room pour la mère étrangère ;
-- (2) aucune ligne `succursales` (donc succursale ETR invisible, et impossible
-- à fermer via le workflow de fermeture) ; (3) re-saisie intégrale de la mère à
-- chaque nouvelle succursale du même groupe.
--
-- SOLUTION. On RÉUTILISE `entreprise_dossiers` — le modèle de dossier existant,
-- qui porte déjà la Data Room, les documents, les demandes et la traçabilité —
-- en le qualifiant par une ORIGINE. Aucune table nouvelle, aucun nouveau
-- microservice, aucun impact sur les lectures existantes (colonne à valeur par
-- défaut 'MAROCAINE').
--
-- `forme_juridique` — UNE SOCIÉTÉ ÉTRANGÈRE N'EST PAS UNE SARL.
-- La colonne existante est NOT NULL avec CHECK IN ('SARL','SARL_AU','SA','SAS',
-- 'SCS','GIE') : une forme étrangère (Ltd, GmbH, BV, Inc…) n'y entre pas.
--
-- On AJOUTE donc la valeur 'ETRANGERE' au CHECK, et la forme RÉELLE du pays
-- d'origine est stockée dans `forme_juridique_origine` (c'est elle qui est
-- publiée : $SOCIETE_MERE_FORME).
--
-- Pourquoi ce n'est pas risqué : le choix du modèle SARL vs SARL AU du workflow
-- SUCCURSALE_ETR ne vient PAS de cette colonne — il vient du caractère
-- uni/pluripersonnel de l'ORGANE qui décide, calculé à l'étape 1
-- (`step1.associeUnique`). Assimiler une Ltd à une « SARL » en base n'apportait
-- donc rien au moteur documentaire, et affichait une forme juridique fausse dans
-- la liste des dossiers, la Fiche client et la Data Room.
-- ---------------------------------------------------------------------

-- Élargissement du CHECK : on le retrouve par introspection plutôt que par son
-- nom auto-généré, pour rester robuste si une migration antérieure l'a renommé.
DO $$
DECLARE
    conname_forme TEXT;
BEGIN
    SELECT c.conname INTO conname_forme
      FROM pg_constraint c
      JOIN pg_class t ON t.oid = c.conrelid
     WHERE t.relname = 'entreprise_dossiers'
       AND c.contype = 'c'
       AND pg_get_constraintdef(c.oid) ILIKE '%forme_juridique%'
     LIMIT 1;

    IF conname_forme IS NOT NULL THEN
        EXECUTE format('ALTER TABLE entreprise_dossiers DROP CONSTRAINT %I', conname_forme);
    END IF;

    ALTER TABLE entreprise_dossiers
        ADD CONSTRAINT entreprise_dossiers_forme_juridique_check
        CHECK (forme_juridique IN ('SARL','SARL_AU','SA','SAS','SCS','GIE','ETRANGERE'));
END $$;

ALTER TABLE entreprise_dossiers
    -- Origine du dossier. 'MAROCAINE' = comportement historique (défaut : aucune
    -- ligne existante n'est modifiée sémantiquement).
    ADD COLUMN IF NOT EXISTS origine VARCHAR(20) NOT NULL DEFAULT 'MAROCAINE',
    -- Pays du siège de la société mère étrangère ($SOCIETE_MERE_PAYS). NULL en MAROCAINE.
    ADD COLUMN IF NOT EXISTS pays VARCHAR(100),
    -- Forme juridique RÉELLE du pays d'origine ($SOCIETE_MERE_FORME) : « Limited »,
    -- « GmbH », « SAS »… Voir la note ci-dessus sur `forme_juridique`.
    ADD COLUMN IF NOT EXISTS forme_juridique_origine VARCHAR(80),
    -- Registre du commerce étranger ($SOCIETE_MERE_REGISTRE / _NUMERO) : le RC
    -- marocain (`rc_numero` / `rc_tribunal`) reste NULL pour une mère étrangère.
    ADD COLUMN IF NOT EXISTS registre_etranger VARCHAR(120),
    ADD COLUMN IF NOT EXISTS registre_etranger_numero VARCHAR(80),
    -- Loi applicable à la société mère ($SOCIETE_MERE_LOI_APPLICABLE).
    ADD COLUMN IF NOT EXISTS loi_applicable VARCHAR(200),
    -- Capital de la mère TEL QUE PUBLIÉ, devise comprise ($SOCIETE_MERE_CAPITAL) :
    -- `capital_social_mad` est en dirhams et ne peut pas porter « 500 000 EUR ».
    ADD COLUMN IF NOT EXISTS capital_origine VARCHAR(60);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'entreprise_dossiers_origine_check'
    ) THEN
        ALTER TABLE entreprise_dossiers
            ADD CONSTRAINT entreprise_dossiers_origine_check
            CHECK (origine IN ('MAROCAINE', 'ETRANGERE'));
    END IF;
END $$;

-- Sélection d'une mère étrangère DÉJÀ enregistrée (spec §C : « si une mère
-- étrangère déjà enregistrée est réutilisée -> la sélectionner »). L'index
-- partiel garde la liste courte et le filtrage indexé.
CREATE INDEX IF NOT EXISTS idx_dossiers_origine_etrangere
    ON entreprise_dossiers (workspace_id, raison_sociale)
    WHERE origine = 'ETRANGERE';

COMMENT ON COLUMN entreprise_dossiers.origine IS
    'MAROCAINE (defaut) | ETRANGERE : societe mere etrangere creee par le workflow SUCCURSALE_ETR, porteuse de sa propre Data Room.';
COMMENT ON COLUMN entreprise_dossiers.forme_juridique_origine IS
    'Forme juridique REELLE du pays d''origine (Ltd, GmbH, BV, Inc...). C''est elle qui est publiee ($SOCIETE_MERE_FORME). forme_juridique vaut alors ''ETRANGERE'' : une societe etrangere n''est PAS une SARL.';
