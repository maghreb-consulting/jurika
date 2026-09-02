-- =====================================================================
-- JURIKA V10 (ticket-service) — Marqueur de transfert sur les tickets
--
-- Contexte : lors d'un transfert de dossier (V9, VOIE A demande/acceptation),
-- DossierTransferService.applyTransfer reassigne les tickets OUVERTS
-- (NOUVEAU/EN_COURS) du dossier au nouvel employe. On veut desormais MARQUER
-- explicitement ces tickets comme "transferes" pour :
--   1) afficher un badge "Transfere" cote front (TicketCard) ;
--   2) tracer la date effective de la reprise.
--
-- Colonne nullable : NULL = jamais transfere (cas nominal). Renseignee par
-- applyTransfer via markTransferred(). Aucun backfill (les transferts passes
-- restent non marques, comportement volontaire).
-- =====================================================================

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS transferred_at TIMESTAMPTZ NULL;
