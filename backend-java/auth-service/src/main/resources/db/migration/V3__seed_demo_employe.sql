-- =====================================================================
-- Seed : 1 EMPLOYE dans le workspace DEMO (JUR-DEMO1)
-- Permet de tester le workflow complet (creation ticket, workflow, debours)
-- A SUPPRIMER en production
--
-- Login : code workspace=JUR-DEMO1, email=karim@jurika.ma, password=Admin@2026
-- =====================================================================

INSERT INTO users (id, workspace_id, email, password_hash, first_name, last_name, phone, role, totp_enabled, is_active)
VALUES
    ('33333333-3333-3333-3333-333333333333',
     '11111111-1111-1111-1111-111111111111',  -- workspace JUR-DEMO1
     'karim@jurika.ma',
     '$2b$12$nI/bjJKoqMRJGTR1HQjJiO5oEsAsBJBilAJDud7Mc5CRdC.1yT4qK',  -- bcrypt(Admin@2026, cost=12)
     'Karim',
     'Benali',
     '+212600112233',  -- demo phone pour le flow 2FA SMS
     'EMPLOYE',
     FALSE,
     TRUE)
ON CONFLICT (workspace_id, email) DO NOTHING;
