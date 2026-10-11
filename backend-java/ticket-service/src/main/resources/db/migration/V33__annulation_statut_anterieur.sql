-- Lot L4a (E4, RG-TKT-03 a 05) : annulation et reprise d'un ticket.
--
-- statut_anterieur : statut atteint au moment de l'annulation ; la reprise ramene le
-- ticket a ce statut, et a lui seul (RG-TKT-05). Il est efface a la reprise.
-- annule_par_id : auteur de l'annulation (RG-TKT-03 : motif, auteur et date conserves ;
-- le motif et la date l'etaient deja : annulation_motif, annule_at).
--
-- Les tickets annules avant cette migration n'ont pas de statut anterieur en colonne :
-- il se relit dans la trace de l'annulation (ticket_comments, type ANNULATION,
-- metadata.from), jamais suppose.
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS statut_anterieur VARCHAR(30);
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS annule_par_id UUID REFERENCES users(id);

ALTER TABLE tickets DROP CONSTRAINT IF EXISTS ck_tickets_statut_anterieur;
ALTER TABLE tickets ADD CONSTRAINT ck_tickets_statut_anterieur
    CHECK (statut_anterieur IS NULL OR statut_anterieur IN
           ('CREATION_TICKET', 'GENERATION_DOCUMENTS', 'DEROULEMENT_DEMARCHE', 'CLOTURE_DOSSIER'));
