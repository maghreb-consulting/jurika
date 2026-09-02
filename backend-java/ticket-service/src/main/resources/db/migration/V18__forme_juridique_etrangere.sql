-- =====================================================================
-- JURIKA V18 — `forme_juridique = 'ETRANGERE'` autorisé (lot DIVERS §C, 2026-08-14)
-- =====================================================================
-- POURQUOI UNE MIGRATION SÉPARÉE ALORS QUE V16 CONTIENT DÉJÀ CE BLOC.
--
-- V16 a d'abord été appliquée dans une forme antérieure, où la mère étrangère
-- était encore assimilée à une SARL / SARL AU. La correction (« une société
-- étrangère n'est PAS une SARL » : forme_juridique = 'ETRANGERE', forme réelle
-- dans forme_juridique_origine) a été apportée EN ÉDITANT V16 — ce qui ne
-- produit aucun effet sur une base où V16 est déjà passée.
--
-- Et rien ne le signale : ticket-service tourne avec
--     spring.flyway.validate-on-migrate: false
-- donc AUCUNE erreur de checksum n'avertit que le fichier a changé après coup.
-- Le symptôme observé était très éloigné de la cause : la création du dossier
-- mère étrangère échouait sur le CHECK, l'exception d'intégrité était traduite
-- en « une société vivante porte déjà cette dénomination », et on cherchait un
-- doublon inexistant.
--
-- RÈGLE À RETENIR : sur ce projet, une migration déjà appliquée ne doit JAMAIS
-- être éditée — il faut en ajouter une nouvelle.
--
-- Ce script est IDEMPOTENT et sans effet si V16 a produit son effet (base
-- vierge, où le CHECK contient déjà 'ETRANGERE').
-- ---------------------------------------------------------------------

DO $$
DECLARE
    conname_forme TEXT;
    def_forme     TEXT;
BEGIN
    -- On retrouve le CHECK portant sur `forme_juridique` par introspection
    -- (nom auto-généré, potentiellement renommé par une migration antérieure).
    -- Le filtre de schéma est explicite : sans lui, une table homonyme dans un
    -- autre schéma pourrait être touchée à la place.
    SELECT c.conname, pg_get_constraintdef(c.oid)
      INTO conname_forme, def_forme
      FROM pg_constraint c
      JOIN pg_class     t ON t.oid = c.conrelid
      JOIN pg_namespace n ON n.oid = t.relnamespace
     WHERE n.nspname   = current_schema()
       AND t.relname   = 'entreprise_dossiers'
       AND c.contype   = 'c'
       AND pg_get_constraintdef(c.oid) ILIKE '%forme_juridique%'
     LIMIT 1;

    IF conname_forme IS NULL THEN
        -- Aucun CHECK sur la colonne : rien à élargir.
        RAISE NOTICE 'V18 : aucun CHECK sur forme_juridique, rien a faire.';
        RETURN;
    END IF;

    IF def_forme ILIKE '%ETRANGERE%' THEN
        -- Deja elargi (base vierge ou rejeu) : on ne touche a rien.
        RAISE NOTICE 'V18 : CHECK % contient deja ETRANGERE.', conname_forme;
        RETURN;
    END IF;

    EXECUTE format('ALTER TABLE entreprise_dossiers DROP CONSTRAINT %I', conname_forme);
    ALTER TABLE entreprise_dossiers
        ADD CONSTRAINT entreprise_dossiers_forme_juridique_check
        CHECK (forme_juridique IN ('SARL','SARL_AU','SA','SAS','SCS','GIE','ETRANGERE'));
    RAISE NOTICE 'V18 : CHECK % elargi a ETRANGERE.', conname_forme;
END $$;

COMMENT ON COLUMN entreprise_dossiers.forme_juridique IS
    'Forme juridique marocaine, ou ''ETRANGERE'' pour une societe mere etrangere (workflow SUCCURSALE_ETR). Dans ce cas la forme REELLE du pays d''origine vit dans forme_juridique_origine : c''est elle qui est publiee.';
