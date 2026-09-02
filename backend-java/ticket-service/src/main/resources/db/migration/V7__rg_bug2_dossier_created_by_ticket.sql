-- =====================================================================
-- JURIKA V7 (ticket-service) — Fix 2026-06-07 (BUG 2)
--
-- Trace l'origine d'un dossier auto-cree par CreateTicketUseCase :
-- on retient l'ID du ticket qui a declenche la creation. Permet a
-- TransitionTicketUseCase de decider, en cas d'annulation du ticket,
-- si le dataroom associe doit etre detruit (oui : dossier autocreate
-- par CE ticket, pas reutilise depuis une demande existante) ou
-- conserve (cas IMPORT qui reutilise un dossier ACTIVE preexistant
-- via DossierIdempotenceLookup).
--
-- NULL = dossier pre-existant, ou cree hors-flow auto-create. NULL
-- protege le dataroom contre toute suppression automatique.
--
-- FK ON DELETE SET NULL : si le ticket est physiquement supprime
-- (rare), on ne perd que le pointeur d'origine, pas le dossier.
-- =====================================================================

ALTER TABLE entreprise_dossiers
    ADD COLUMN IF NOT EXISTS created_by_ticket_id UUID
        REFERENCES tickets(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_dossiers_created_by_ticket
    ON entreprise_dossiers (created_by_ticket_id)
    WHERE created_by_ticket_id IS NOT NULL;
