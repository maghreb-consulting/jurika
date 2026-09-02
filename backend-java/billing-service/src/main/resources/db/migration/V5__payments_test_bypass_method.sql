-- ════════════════════════════════════════════════════════════════════════════
-- BUG 14 (2026-06-07) bis — methode TEST_BYPASS pour le wizard signup
-- ════════════════════════════════════════════════════════════════════════════
-- Permet de tester de bout en bout le flux signup -> plan -> recap ->
-- paiement -> activation + identifiants SANS configurer Stripe ni effectuer
-- de virement reel. Activable uniquement si la property
-- jurika.billing.test-bypass-enabled=true (defaut false en prod).
--
-- L'usage cote PreparePaymentUseCase : si method=TEST_BYPASS et property
-- enabled, le Payment est cree IMMEDIATEMENT en status=COMPLETED + shadow
-- subscription cree + workspace active + identifiants emis.
-- ════════════════════════════════════════════════════════════════════════════

ALTER TABLE payments DROP CONSTRAINT IF EXISTS ck_payment_method;
ALTER TABLE payments ADD CONSTRAINT ck_payment_method
    CHECK (method IN ('CARD', 'BANK_TRANSFER', 'CHEQUE', 'CASH', 'TEST_BYPASS'));

COMMENT ON COLUMN payments.method IS
    'CARD | BANK_TRANSFER | CHEQUE | CASH | TEST_BYPASS (dev only, gated par jurika.billing.test-bypass-enabled).';
