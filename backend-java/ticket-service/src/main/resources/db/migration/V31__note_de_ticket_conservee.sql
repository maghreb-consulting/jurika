-- =====================================================================
-- JURIKA V31 (ticket-service) -- Lot L1 : la note de ticket est conservee
--
-- RG-TKT-07 (cahier des charges mis a jour le 2026-10-10) : la note suit le ticket
-- lors d'un transfert ou d'une reaffectation, reste consultable en lecture seule
-- apres la cloture et est CONSERVEE avec le dossier. V29 (deja appliquee au Z440,
-- donc intouchable) l'attachait au ticket en ON DELETE CASCADE : supprimer la ligne
-- du ticket l'aurait effacee sans bruit. La cle passe en RESTRICT.
-- =====================================================================
ALTER TABLE ticket_notes DROP CONSTRAINT IF EXISTS ticket_notes_ticket_id_fkey;
ALTER TABLE ticket_notes ADD CONSTRAINT ticket_notes_ticket_id_fkey
    FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE RESTRICT;
