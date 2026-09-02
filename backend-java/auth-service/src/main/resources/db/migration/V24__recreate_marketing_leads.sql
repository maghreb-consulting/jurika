-- ════════════════════════════════════════════════════════════════════════════
-- Récupération post-divergence : recréation de marketing_leads
--
-- Contexte : une migration V23 obsolète "drop trial and demo leads" (jamais
-- présente sur main) avait été appliquée à une DB de dev, droppant la table
-- marketing_leads créée par V19. La V23 actuelle sur main est le rename
-- professionnel → business, qui ne touche pas marketing_leads. On recrée
-- donc la table ici (IF NOT EXISTS pour rester idempotente sur les DB où
-- V19 est encore intacte).
--
-- Identique à V19__sprint11_marketing_leads.sql.
-- ════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS marketing_leads (
    id              UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(150)             NOT NULL,
    raison_sociale  VARCHAR(150)             NOT NULL,
    telephone       VARCHAR(30),
    source          VARCHAR(30)              NOT NULL DEFAULT 'marketing-site',
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    status          VARCHAR(20)              NOT NULL DEFAULT 'NEW',
    notes           TEXT,
    created_at      TIMESTAMPTZ              NOT NULL DEFAULT now(),
    contacted_at    TIMESTAMPTZ,
    converted_workspace_id UUID
);

CREATE INDEX IF NOT EXISTS idx_marketing_leads_email ON marketing_leads (lower(email));
CREATE INDEX IF NOT EXISTS idx_marketing_leads_created_at ON marketing_leads (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_marketing_leads_status ON marketing_leads (status);

COMMENT ON TABLE marketing_leads IS 'Sprint 11 — Leads collectes depuis landing jurika.ai. Recréée en V24 après drop accidentel par une V23 obsolète. RG-MK01/02/03.';
