-- =====================================================================
-- JURIKA V22 — `demarches_referentiel.obligatoire` : CHAR(1) -> VARCHAR(1)
-- =====================================================================
-- POURQUOI UNE MIGRATION SEPAREE PLUTOT QUE CORRIGER V20.
-- V20 est appliquee depuis le 2026-09-06. Sur ce projet, une migration deja
-- passee ne doit JAMAIS etre editee : `validate-on-migrate: false` fait que la
-- modification serait ignoree en silence sur toute base existante. Voir V18,
-- qui documente exactement le meme piege.
--
-- LE DEFAUT. V20 declarait la colonne en CHAR(1), alors que l'entite JPA
-- `DemarcheReferentielEntity.obligatoire` est un `String`. Hibernate attend donc
-- un VARCHAR, trouve un `bpchar`, et REFUSE de demarrer :
--
--     Schema-validation: wrong column type encountered in column [obligatoire]
--     in table [demarches_referentiel]; found [bpchar (Types#CHAR)],
--     but expecting [varchar(255) (Types#VARCHAR)]
--
-- ticket-service repartait en boucle. Le defaut n'a pas pu etre vu plus tot :
-- les migrations n'avaient ete eprouvees que dans une transaction annulee, sans
-- jamais demarrer le service contre le schema ainsi cree.
--
-- POURQUOI CORRIGER LA COLONNE ET NON L'ENTITE. Toutes les autres colonnes
-- courtes de cette table sont des VARCHAR (`phase_code` VARCHAR(8),
-- `delai_unite` VARCHAR(5), `workflow_type` VARCHAR(30)) : CHAR(1) etait
-- l'exception. De plus CHAR complete a blanc, ce qui rend les comparaisons
-- ('O' vs 'O ') dependantes du contexte. VARCHAR(1) est strictement preferable.
--
-- Le CHECK est recree explicitement : un ALTER TYPE le conserve, mais on ne
-- laisse pas la garantie metier dependre d'un effet de bord.
-- ---------------------------------------------------------------------

ALTER TABLE demarches_referentiel
    ALTER COLUMN obligatoire TYPE VARCHAR(1) USING TRIM(TRAILING FROM obligatoire);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
         WHERE conrelid = 'demarches_referentiel'::regclass
           AND contype  = 'c'
           AND pg_get_constraintdef(oid) ILIKE '%obligatoire%'
    ) THEN
        ALTER TABLE demarches_referentiel
            ADD CONSTRAINT demarches_referentiel_obligatoire_check
            CHECK (obligatoire IN ('O','C'));
    END IF;
END $$;

COMMENT ON COLUMN demarches_referentiel.obligatoire IS
    'O = demarche obligatoire ; C = demarche conditionnelle (voir condition_application).';
