-- =====================================================================
-- JURIKA V35 (auth-service) -- Lot L1, etape E8 : droit de suppression delegue
--
-- CDC 3.2 : le superviseur accorde ou retire aux employes le droit de suppression
-- en Data Room. RG-DR-06 : l'employe ajoute des documents ; il ne peut en supprimer
-- que si le superviseur lui en a donne le droit ; toute suppression est tracee.
-- Par defaut : aucun droit (FALSE) ; le controle est fait par dataroom-service.
-- =====================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS droit_suppression_dataroom BOOLEAN NOT NULL DEFAULT FALSE;
