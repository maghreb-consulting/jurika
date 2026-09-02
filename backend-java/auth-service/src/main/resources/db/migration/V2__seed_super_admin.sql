-- =====================================================================
-- Seed : 1 SUPER_ADMIN plateforme + 1 workspace de demonstration
-- A SUPPRIMER en production via une autre migration
-- =====================================================================

INSERT INTO subscriptions (id, plan, storage_quota_mb, max_employees, max_clients, monthly_price_mad, status)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'STARTER',    5120,  3,  10, 1500.00, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000002', 'PRO',        20480, 10, 50, 4500.00, 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000003', 'ENTERPRISE', 102400,50,200,12000.00, 'ACTIVE');

INSERT INTO workspaces (id, code, name, contact_email, subscription_id, status)
VALUES
    ('11111111-1111-1111-1111-111111111111',
     'JUR-DEMO1',
     'Maghreb Consulting (DEMO)',
     'demo@jurika.ma',
     '00000000-0000-0000-0000-000000000003',
     'ACTIVE');

-- Mot de passe : Admin@2026 (BCrypt cost 12)
-- Ce hash est genere par BCryptPasswordEncoder(12).encode("Admin@2026")
INSERT INTO users (id, workspace_id, email, password_hash, first_name, last_name, role, totp_enabled, is_active)
VALUES
    ('22222222-2222-2222-2222-222222222222',
     '11111111-1111-1111-1111-111111111111',
     'admin@jurika.ma',
     '$2b$12$nI/bjJKoqMRJGTR1HQjJiO5oEsAsBJBilAJDud7Mc5CRdC.1yT4qK',
     'Super',
     'Admin',
     'SUPER_ADMIN',
     FALSE,
     TRUE);
