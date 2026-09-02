-- =====================================================================
-- JURIKA V11 -- Correction de la contrainte UNIQUE multi-tenant
-- Reference : dette technique 2026-05-19 (workflow 500 sur /start)
--
-- Probleme : `UNIQUE (ticket_id)` ne tient pas compte du workspace_id.
-- Si un test cree une row puis le workspace change, l'INSERT plante.
-- Fix : UNIQUE (workspace_id, ticket_id) -- coherent avec multi-tenancy.
-- =====================================================================

ALTER TABLE workflow_progress DROP CONSTRAINT IF EXISTS uq_workflow_ticket;
ALTER TABLE workflow_progress
    ADD CONSTRAINT uq_workflow_workspace_ticket UNIQUE (workspace_id, ticket_id);
