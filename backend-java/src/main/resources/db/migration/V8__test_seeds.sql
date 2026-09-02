-- V8__test_seeds.sql
-- Seed the second employee for automated testing

DO $$
DECLARE
    ws_id UUID;
BEGIN
    -- Get workspace ID for MA-2026
    SELECT id INTO ws_id FROM workspaces WHERE code_workspace = 'MA-2026' LIMIT 1;
    
    -- If not found, take the first active one
    IF ws_id IS NULL THEN
        SELECT id INTO ws_id FROM workspaces WHERE is_active = true LIMIT 1;
    END IF;

    IF ws_id IS NOT NULL THEN
        INSERT INTO users (id, workspace_id, email, password_hash, full_name, role, is_active, totp_enabled, totp_secret)
        VALUES (
            gen_random_uuid(),
            ws_id,
            'employe2@test.com',
            '$2a$12$.LGwFi67/kRgh9EN3WbdsuhXF.1aRWtxrrPedoCA0nCrmUn44LKpu',
            'Employe 2 Test',
            'EMPLOYE',
            true,
            true,
            'FwYtZ/7+rST+cAlD4+uB1omej99hJ1+CbNiTFdJCjRU='
        ) ON CONFLICT (email, workspace_id) DO NOTHING;
    END IF;
END $$;
