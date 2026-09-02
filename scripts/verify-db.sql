-- =====================================================================
-- JURIKA — Verification base de donnees apres les smoke tests
-- A lancer dans DBeaver apres chaque etape du smoke-tests.http
-- =====================================================================

-- ATTENTION : la RLS bloque la lecture si tu n'es pas en superuser.
-- Option A : connecte-toi en tant que "postgres" (superuser) — bypass RLS
-- Option B : definis le workspace explicitement avant les SELECT :
--   SET LOCAL app.current_workspace_id = '<workspace_id>';

-- ===================================================================
-- BLOC 1 — Apres l'install (avant les tests)
-- ===================================================================

-- Verifier les migrations appliquees (3 tables flyway distinctes)
SELECT 'auth' AS service, version, description, success, installed_on
FROM flyway_history_auth ORDER BY installed_rank;

SELECT 'ticket' AS service, version, description, success, installed_on
FROM flyway_history_ticket ORDER BY installed_rank;

SELECT 'workflow' AS service, version, description, success, installed_on
FROM flyway_history_workflow ORDER BY installed_rank;

-- Verifier les seeds (V2 auth)
SELECT id, plan, monthly_price_mad FROM subscriptions;
SELECT id, code, name, status FROM workspaces;

-- ===================================================================
-- BLOC 2 — Apres l'etape 2 (register)
-- ===================================================================

-- Tu dois voir DEMO1 + Cabinet Test (avec son code JUR-XXXXX)
SELECT code, name, contact_email, status, created_at FROM workspaces ORDER BY created_at;

-- Le user Karim doit etre cree comme SUPERVISEUR
SELECT u.email, u.first_name, u.last_name, u.role, w.code AS workspace
FROM users u
JOIN workspaces w ON w.id = u.workspace_id
WHERE u.email = 'karim@cabinet-test.ma';

-- ===================================================================
-- BLOC 3 — Apres l'etape 3 (login)
-- ===================================================================

-- Un refresh token doit etre stocke
SELECT user_id, expires_at, ip_address, user_agent, revoked_at
FROM refresh_tokens
ORDER BY issued_at DESC LIMIT 5;

-- Audit log doit contenir LOGIN_SUCCESS
SELECT created_at, action, user_id, ip_address, metadata
FROM audit_log
WHERE action LIKE 'LOGIN%'
ORDER BY created_at DESC LIMIT 10;

-- ===================================================================
-- BLOC 4 — Apres l'etape 5 (ticket cree)
-- ===================================================================

-- Recupere ton workspace_id (le tien, pas DEMO)
SELECT id FROM workspaces WHERE code != 'JUR-DEMO1';

-- Puis remplace UUID ci-dessous et execute :
-- SET LOCAL app.current_workspace_id = '<uuid-recupere-ci-dessus>';

-- Voir tes tickets
SELECT reference, titre, type, statut, priorite, created_at
FROM tickets
ORDER BY created_at DESC;

-- ===================================================================
-- BLOC 5 — Apres l'etape 7 (workflow start)
-- ===================================================================

-- Voir le workflow_progress lie au ticket
SELECT t.reference, t.statut AS ticket_statut,
       wp.workflow_type, wp.current_step, wp.total_steps,
       wp.statut AS workflow_statut, jsonb_pretty(wp.data) AS data
FROM tickets t
JOIN workflow_progress wp ON wp.ticket_id = t.id
ORDER BY t.created_at DESC;

-- ===================================================================
-- BLOC 6 — Apres l'etape 9 (debours)
-- ===================================================================

-- Voir les debours par ticket
SELECT t.reference, d.libelle, d.categorie, d.montant_mad, d.date_engagement
FROM ticket_debours d
JOIN tickets t ON t.id = d.ticket_id
ORDER BY d.created_at DESC;

-- Total debours par ticket
SELECT t.reference, t.titre, SUM(d.montant_mad) AS total_mad
FROM tickets t
LEFT JOIN ticket_debours d ON d.ticket_id = t.id
GROUP BY t.id, t.reference, t.titre
ORDER BY total_mad DESC NULLS LAST;

-- ===================================================================
-- BLOC 7 — Apres l'etape 10 (annulation)
-- ===================================================================

-- Le ticket doit etre ANNULE avec annulation_motif
SELECT reference, statut, annulation_motif, annule_at
FROM tickets
WHERE statut = 'ANNULE';

-- Le commentaire d'annulation doit etre dans ticket_comments
SELECT t.reference, c.type, c.contenu, c.created_at
FROM ticket_comments c
JOIN tickets t ON t.id = c.ticket_id
WHERE c.type = 'ANNULATION'
ORDER BY c.created_at DESC;

-- ===================================================================
-- BLOC 8 — Verification RLS multi-tenant (test critique)
-- ===================================================================

-- En tant que superuser, on voit tout :
SELECT workspace_id, COUNT(*) FROM tickets GROUP BY workspace_id;

-- En tant que jurika_user, RLS doit bloquer les tickets d'un autre workspace.
-- Connecte-toi avec le user jurika_user et execute :
--   SET LOCAL app.current_workspace_id = '11111111-1111-1111-1111-111111111111';
--   SELECT COUNT(*) FROM tickets;
-- Tu ne dois voir QUE les tickets de DEMO1.
--
--   SET LOCAL app.current_workspace_id = '<ton-workspace-id>';
--   SELECT COUNT(*) FROM tickets;
-- Tu ne dois voir QUE les tiens.
