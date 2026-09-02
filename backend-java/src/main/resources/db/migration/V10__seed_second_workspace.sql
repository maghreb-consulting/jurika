-- V9__seed_second_workspace.sql
-- Seed un deuxième espace de travail (TE-2026) pour tester l'isolation (Multi-Tenant RLS)
-- User pwd = Test1234!
DO $$
DECLARE
    new_ws_id UUID := gen_random_uuid();
BEGIN
    -- 1. Create Workspace TE-2026
    INSERT INTO workspaces (id, name, code_workspace, is_active)
    VALUES (
        new_ws_id,
        'Workspace Test Isolation',
        'TE-2026',
        true
    ) ON CONFLICT (code_workspace) DO NOTHING;

    -- 2. Ensure we get the ID if it already existed
    SELECT id INTO new_ws_id FROM workspaces WHERE code_workspace = 'TE-2026' LIMIT 1;

    -- 3. Create a default supervisor for this workspace
    INSERT INTO users (id, workspace_id, email, password_hash, full_name, role, is_active, totp_enabled, totp_secret)
    VALUES (
        gen_random_uuid(),
        new_ws_id,
        'superviseur.test@gje.ma',
        '$2a$12$.LGwFi67/kRgh9EN3WbdsuhXF.1aRWtxrrPedoCA0nCrmUn44LKpu', -- Test1234!
        'Superviseur Test (TE-2026)',
        'SUPERVISEUR',
        true,
        true,
        'FwYtZ/7+rST+cAlD4+uB1omej99hJ1+CbNiTFdJCjRU=' -- same test secret
    ) ON CONFLICT (email, workspace_id) DO NOTHING;

    -- 4. Create an employee for this workspace
    INSERT INTO users (id, workspace_id, email, password_hash, full_name, role, is_active, totp_enabled, totp_secret)
    VALUES (
        gen_random_uuid(),
        new_ws_id,
        'employe.test@gje.ma',
        '$2a$12$.LGwFi67/kRgh9EN3WbdsuhXF.1aRWtxrrPedoCA0nCrmUn44LKpu', -- Test1234!
        'Employé Test (TE-2026)',
        'EMPLOYE',
        true,
        true,
        'FwYtZ/7+rST+cAlD4+uB1omej99hJ1+CbNiTFdJCjRU='
    ) ON CONFLICT (email, workspace_id) DO NOTHING;

END $$;
