-- =====================================================================
-- JURIKA V32 (ticket-service) -- Lot L1 : verification des rattrapages par le superviseur
--
-- V28 a designe d'office un responsable aux dossiers qui n'en avaient pas (nature
-- RATTRAPAGE, decision D1 du rapport de L1). Le superviseur verifie chacun de ces
-- choix depuis son ecran ; la verification est tracee (qui, quand). Un rattrapage
-- errone se corrige par une reaffectation d'office (nature FORCEE), elle aussi tracee.
-- =====================================================================
ALTER TABLE dossier_reaffectations ADD COLUMN IF NOT EXISTS verifie_par UUID REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE dossier_reaffectations ADD COLUMN IF NOT EXISTS verifie_le TIMESTAMPTZ;
ALTER TABLE dossier_reaffectations ADD CONSTRAINT ck_reaffectation_verification
    CHECK ((verifie_par IS NULL) = (verifie_le IS NULL) AND (verifie_par IS NULL OR nature = 'RATTRAPAGE'));
