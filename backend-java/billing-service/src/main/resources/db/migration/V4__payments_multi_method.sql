-- ════════════════════════════════════════════════════════════════════════════
-- BUG 14 (2026-06-07) — Paiements multi-moyens
-- ════════════════════════════════════════════════════════════════════════════
-- Avant : seul Stripe Checkout etait possible (T5 SubscribeUseCase →
-- checkout-session). En prod cabinet Maroc, 3 moyens hors-ligne sont demandes
-- en plus de la carte bancaire : virement bancaire, cheque, especes au siege.
--
-- La table `payments` est INDEPENDANTE de `subscriptions` (les hors-ligne
-- n'ont pas de subscription Stripe). La sub n'est cree qu'apres validation
-- du paiement, via le webhook checkout.session.completed (CARD) ou via
-- ValidatePaymentUseCase (BANK_TRANSFER/CHEQUE/CASH).
--
-- Status :
--   PENDING   — paiement initie, attend validation (CARD = checkout en cours,
--                BANK_TRANSFER/CHEQUE/CASH = attend confirmation back-office)
--   COMPLETED — paiement valide -> declenche activation workspace + envoi creds
--   FAILED    — Stripe a refuse OU back-office a rejete
--   CANCELLED — user a abandonne le checkout ou retiree par admin
-- ════════════════════════════════════════════════════════════════════════════

CREATE TABLE payments (
    id                  BIGSERIAL PRIMARY KEY,
    workspace_id        UUID NOT NULL,
    plan_code           VARCHAR(50) NOT NULL,
    billing_period      VARCHAR(20) NOT NULL DEFAULT 'monthly',
    amount_mad_cents    BIGINT NOT NULL,
    currency            VARCHAR(10) NOT NULL DEFAULT 'mad',
    method              VARCHAR(30) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    stripe_payment_id   VARCHAR(255),   -- checkout session id ou payment intent id
    stripe_checkout_url VARCHAR(2048),  -- url hostee Stripe (pour CARD)
    bank_reference      VARCHAR(255),   -- reference virement saisie par user
    cheque_number       VARCHAR(50),
    cheque_date         DATE,
    proof_url           VARCHAR(2048),  -- justificatif uploade (scan virement/cheque)
    notes               TEXT,           -- notes back-office (raison rejet, suivi)
    validated_by        VARCHAR(255),   -- email ou id user qui a valide manuellement
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_payment_method CHECK (method IN ('CARD', 'BANK_TRANSFER', 'CHEQUE', 'CASH')),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_payment_plan   CHECK (plan_code IN ('essentiel', 'business', 'entreprise')),
    CONSTRAINT ck_payment_period CHECK (billing_period IN ('monthly', 'yearly')),
    CONSTRAINT ck_payment_amount CHECK (amount_mad_cents >= 0)
);

CREATE INDEX idx_payments_workspace        ON payments (workspace_id);
CREATE INDEX idx_payments_status           ON payments (status);
CREATE INDEX idx_payments_method           ON payments (method);
CREATE INDEX idx_payments_created_at       ON payments (created_at DESC);
CREATE INDEX idx_payments_stripe_id        ON payments (stripe_payment_id) WHERE stripe_payment_id IS NOT NULL;
CREATE INDEX idx_payments_workspace_status ON payments (workspace_id, status, created_at DESC);

COMMENT ON TABLE  payments IS
    'BUG 14 (2026-06-07) — paiements multi-moyens (carte/virement/cheque/cash) du workspace.';
COMMENT ON COLUMN payments.status IS
    'PENDING = attend validation ; COMPLETED = declenche activation + envoi identifiants.';
COMMENT ON COLUMN payments.stripe_payment_id IS
    'method=CARD : Stripe checkout_session_id (puis convertible en payment_intent post-completion).';
COMMENT ON COLUMN payments.validated_by IS
    'BANK_TRANSFER/CHEQUE/CASH : email SUPERVISEUR ou SUPER_ADMIN ayant declenche le PATCH /validate.';
