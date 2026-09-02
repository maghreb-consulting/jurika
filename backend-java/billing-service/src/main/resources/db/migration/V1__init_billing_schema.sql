-- ════════════════════════════════════════════════════════════════════════════
-- JURIKA — Sprint 12 Billing — schema initial billing-service
-- ════════════════════════════════════════════════════════════════════════════
-- Base PG dediee : jurika_billing (decision archi #4 — isolation financiere).
-- Pas de RLS multi-tenant ici : un seul `workspace_id` par ligne, et le filtrage
-- est applicatif (les controllers verifient via @PreAuthorize + TenantContext).
-- L'isolation est physique (DB separee) + applicative, conformement aux
-- recommandations PCI-DSS pour les donnees de facturation.
-- ════════════════════════════════════════════════════════════════════════════

-- ─── Subscriptions ────────────────────────────────────────────────────────
-- Stocke l'abonnement Stripe courant et son historique pour chaque workspace.
-- Le UNIQUE (workspace_id, status) garantit qu'il ne peut y avoir qu'un seul
-- abonnement dans un meme statut a un instant donne (1 active + 1 cancelled
-- historique = OK ; 2 actives = interdit).
-- RG-BL11 : 1 ligne par souscription.
-- RG-BL07 : status `cancelled` conserve l'acces jusqu'a current_period_end.
CREATE TABLE subscriptions (
    id                       BIGSERIAL PRIMARY KEY,
    workspace_id             UUID NOT NULL,
    stripe_customer_id       VARCHAR(255) NOT NULL,
    stripe_subscription_id   VARCHAR(255) NOT NULL,
    plan_code                VARCHAR(50) NOT NULL,
    status                   VARCHAR(50) NOT NULL,
    current_period_start     TIMESTAMP NOT NULL,
    current_period_end       TIMESTAMP NOT NULL,
    cancel_at_period_end     BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled_at             TIMESTAMP,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_subscription_stripe_id UNIQUE (stripe_subscription_id),
    CONSTRAINT uq_workspace_status       UNIQUE (workspace_id, status),
    CONSTRAINT ck_plan_code              CHECK (plan_code IN ('starter', 'business', 'enterprise')),
    CONSTRAINT ck_status                 CHECK (status IN ('trialing', 'active', 'past_due', 'cancelled', 'incomplete', 'incomplete_expired', 'unpaid'))
);

CREATE INDEX idx_subscriptions_workspace ON subscriptions (workspace_id);
CREATE INDEX idx_subscriptions_status    ON subscriptions (status);
CREATE INDEX idx_subscriptions_stripe_customer ON subscriptions (stripe_customer_id);

COMMENT ON TABLE  subscriptions IS 'Sprint 12 — abonnements Stripe par workspace. RG-BL11.';
COMMENT ON COLUMN subscriptions.status IS 'active | past_due | cancelled | trialing | incomplete | incomplete_expired | unpaid (miroir Stripe).';
COMMENT ON COLUMN subscriptions.cancel_at_period_end IS 'TRUE = annule a la fin de la periode payee. Acces preserve jusqu''a current_period_end (RG-BL07).';

-- ─── Invoices ─────────────────────────────────────────────────────────────
-- Une ligne par invoice Stripe persistee localement pour affichage + audit.
-- Le PDF reste hoste cote Stripe (champ invoice_pdf_url) — pas de stockage local.
-- RG-BL05 : 1 invoice par paiement reussi.
-- RG-BL12 : TVA 20% Maroc en HT + TTC (calcule backend, pas Stripe Tax).
CREATE TABLE invoices (
    id                       BIGSERIAL PRIMARY KEY,
    workspace_id             UUID NOT NULL,
    subscription_id          BIGINT REFERENCES subscriptions(id) ON DELETE SET NULL,
    stripe_invoice_id        VARCHAR(255) NOT NULL,
    number                   VARCHAR(100) NOT NULL,
    amount_due_cents         BIGINT NOT NULL,
    amount_paid_cents        BIGINT NOT NULL DEFAULT 0,
    amount_ht_cents          BIGINT NOT NULL DEFAULT 0,
    amount_tva_cents         BIGINT NOT NULL DEFAULT 0,
    currency                 VARCHAR(10) NOT NULL DEFAULT 'mad',
    status                   VARCHAR(50) NOT NULL,
    tva_rate                 NUMERIC(5,2) NOT NULL DEFAULT 20.00,
    invoice_pdf_url          VARCHAR(1024),
    hosted_invoice_url       VARCHAR(1024),
    issued_at                TIMESTAMP NOT NULL,
    paid_at                  TIMESTAMP,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_invoice_stripe_id UNIQUE (stripe_invoice_id),
    CONSTRAINT ck_invoice_status    CHECK (status IN ('paid', 'open', 'void', 'uncollectible', 'draft')),
    CONSTRAINT ck_amounts_positive  CHECK (amount_due_cents >= 0 AND amount_paid_cents >= 0)
);

CREATE INDEX idx_invoices_workspace    ON invoices (workspace_id);
CREATE INDEX idx_invoices_subscription ON invoices (subscription_id);
CREATE INDEX idx_invoices_status       ON invoices (status);
CREATE INDEX idx_invoices_issued_at    ON invoices (issued_at DESC);

COMMENT ON TABLE  invoices IS 'Sprint 12 — miroir local des invoices Stripe. RG-BL05/RG-BL12.';
COMMENT ON COLUMN invoices.amount_ht_cents  IS 'Montant HT en centimes MAD. Calcule backend (RG-BL12).';
COMMENT ON COLUMN invoices.amount_tva_cents IS 'Montant TVA en centimes MAD. Calcule backend (RG-BL12).';

-- ─── Payment methods ──────────────────────────────────────────────────────
-- Reference vers le payment method Stripe + last4/brand pour affichage masque.
-- AUCUN PAN stocke (PCI compliance via tokenization Stripe).
CREATE TABLE payment_methods (
    id                          BIGSERIAL PRIMARY KEY,
    workspace_id                UUID NOT NULL,
    stripe_payment_method_id    VARCHAR(255) NOT NULL,
    stripe_customer_id          VARCHAR(255) NOT NULL,
    type                        VARCHAR(50) NOT NULL DEFAULT 'card',
    brand                       VARCHAR(50),
    last4                       VARCHAR(4),
    exp_month                   INT,
    exp_year                    INT,
    is_default                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_payment_method_stripe_id UNIQUE (stripe_payment_method_id),
    CONSTRAINT ck_payment_method_type      CHECK (type IN ('card'))
);

CREATE INDEX idx_payment_methods_workspace ON payment_methods (workspace_id);

COMMENT ON TABLE payment_methods IS 'Sprint 12 — reference au Stripe payment method. Aucun PAN persiste (PCI).';

-- ─── Webhook events ───────────────────────────────────────────────────────
-- Persiste TOUS les events recus AVANT traitement (decision archi #6) pour
-- replay possible en cas d'incident + idempotency via stripe_event_id unique.
-- RG-BL18 : pas de double traitement.
-- Retention 3 mois apres processed=true (cleanup cron mensuel, decision #10).
CREATE TABLE webhook_events (
    id                       BIGSERIAL PRIMARY KEY,
    stripe_event_id          VARCHAR(255) NOT NULL,
    event_type               VARCHAR(100) NOT NULL,
    payload                  JSONB NOT NULL,
    processed                BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at             TIMESTAMP,
    attempts                 INT NOT NULL DEFAULT 0,
    error_message            TEXT,
    received_at              TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_webhook_event_stripe_id UNIQUE (stripe_event_id)
);

CREATE INDEX idx_webhook_events_type        ON webhook_events (event_type);
CREATE INDEX idx_webhook_events_unprocessed ON webhook_events (processed) WHERE processed = FALSE;
CREATE INDEX idx_webhook_events_received_at ON webhook_events (received_at DESC);

COMMENT ON TABLE  webhook_events IS 'Sprint 12 — store-and-forward des events Stripe pour idempotency + replay (decision archi #6).';
COMMENT ON COLUMN webhook_events.processed IS 'TRUE apres traitement reussi. Cleanup > 3 mois (decision #10).';
