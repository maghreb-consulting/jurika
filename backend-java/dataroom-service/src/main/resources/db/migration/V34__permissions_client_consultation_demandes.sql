-- =====================================================================
-- JURIKA V34 (dataroom-service) -- Lot L1, etape E10 : permissions du client
--
-- RG-CLI-01 : les permissions du client sont reglees par dossier, par l'employe
-- responsable ou par le superviseur : consultation des documents, telechargement,
-- depot de documents, envoi de demandes. Chaque modification est tracee.
--
-- Existaient : perm_download, perm_print (V6), perm_depot (V19). Manquaient la
-- consultation (toujours permise jusqu'ici) et l'envoi de demandes (jamais
-- verifie). Valeur par defaut TRUE : le comportement actuel est conserve tant que
-- le cabinet ne restreint pas.
-- =====================================================================
ALTER TABLE dataroom_settings ADD COLUMN IF NOT EXISTS perm_consultation BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE dataroom_settings ADD COLUMN IF NOT EXISTS perm_demandes BOOLEAN NOT NULL DEFAULT TRUE;
