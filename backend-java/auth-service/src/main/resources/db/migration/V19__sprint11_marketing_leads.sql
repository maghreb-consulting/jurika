-- Sprint 11 TASK 1 — Marketing leads (demo requests captures from landing)
-- RG-MK01/02/03 — endpoint POST /api/v1/public/leads/demo-request
--
-- Numérotation : V19 (gap V9-V18 dans auth-service accepté par Flyway)
-- pour cohérence avec la convention cross-module du projet.

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

-- Lookup par email (anti-doublon manuel + métriques)
CREATE INDEX IF NOT EXISTS idx_marketing_leads_email ON marketing_leads (lower(email));
CREATE INDEX IF NOT EXISTS idx_marketing_leads_created_at ON marketing_leads (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_marketing_leads_status ON marketing_leads (status);

COMMENT ON TABLE marketing_leads IS 'Sprint 11 — Leads collectes depuis landing jurika.ai (modal Demander une demo). RG-MK01/02/03.';
COMMENT ON COLUMN marketing_leads.source IS 'Provenance : marketing-site | spa | manual | api';
COMMENT ON COLUMN marketing_leads.status IS 'Workflow commercial : NEW | CONTACTED | DEMO_BOOKED | LOST | CONVERTED';
