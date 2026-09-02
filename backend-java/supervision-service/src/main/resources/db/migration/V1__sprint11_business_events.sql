-- Sprint 11 TASK 6 — Business events table pour analytics funnel.
--
-- Cette table est dans supervision-service (1ere migration V1 du module
-- qui etait vierge cote Flyway, cf. AUDIT_SPRINT_11_PREALABLE §7).
--
-- Reference logique "V18 cross-module" dans les docs/commits Sprint 11
-- pour aligner avec le plan canonique. Physique : V1 locale.
--
-- Contrat HTTP cross-service (decide TASK 6 par utilisateur) :
--   - Les services emetteurs (auth-service, marketing-site, etc.) NE font
--     PAS d'INSERT direct cross-tier en base. Ils appellent
--     POST /internal/events sur supervision-service qui persiste.
--   - Endpoint /internal/** : non expose au public, auth interne (header
--     X-Internal-Token ou JWT internal claim). MVP Sprint 11 : whitelist
--     gateway + auth simple par header secret.
--
-- 11 events instrumentes (cf. plan §6) :
--   PAGE_VIEWED, DEMO_REQUESTED, SIGNUP_STARTED, SIGNUP_STEP_COMPLETED,
--   SIGNUP_COMPLETED, EMAIL_VERIFIED, FIRST_LOGIN, FIRST_TICKET_CREATED,
--   FIRST_DOCUMENT_UPLOADED, TRIAL_EXPIRED, BILLING_PAGE_VIEWED.

CREATE TABLE IF NOT EXISTS business_events (
    id           BIGSERIAL       PRIMARY KEY,
    workspace_id UUID,                                  -- NULL pour events anonymes (PAGE_VIEWED landing, DEMO_REQUESTED)
    event_type   VARCHAR(50)     NOT NULL,
    properties   JSONB           NOT NULL DEFAULT '{}'::jsonb,
    occurred_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    source       VARCHAR(20)     NOT NULL DEFAULT 'backend',  -- marketing-site | spa | mobile | backend
    received_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Index pour les queries funnel par event_type (cardinalite ~10 valeurs)
CREATE INDEX IF NOT EXISTS idx_business_events_event_type ON business_events (event_type);
-- Index time-series sur occurred_at (ranges glissants 7/30 jours)
CREATE INDEX IF NOT EXISTS idx_business_events_occurred_at ON business_events (occurred_at DESC);
-- Index workspace pour drill-down (utilise par dashboard SuperAdmin)
CREATE INDEX IF NOT EXISTS idx_business_events_workspace_id ON business_events (workspace_id) WHERE workspace_id IS NOT NULL;
-- Index source pour split landing vs SPA dans funnel admin
CREATE INDEX IF NOT EXISTS idx_business_events_source ON business_events (source);

COMMENT ON TABLE business_events IS 'Sprint 11 TASK 6 -- Events analytics du funnel commercial. Sources : marketing-site (landing), spa (frontend-react), backend (auth-service, etc.), mobile (futur).';
COMMENT ON COLUMN business_events.workspace_id IS 'NULL pour events anonymes pre-signup (PAGE_VIEWED, DEMO_REQUESTED) ; UUID workspace pour events authentifies.';
COMMENT ON COLUMN business_events.properties IS 'Payload arbitraire JSON : { path, plan, source, etc. } selon event_type.';
COMMENT ON COLUMN business_events.source IS 'marketing-site | spa | mobile | backend.';
