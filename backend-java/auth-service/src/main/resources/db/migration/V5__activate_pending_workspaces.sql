-- =====================================================================
-- Flow classique : workspaces PENDING_VERIFICATION -> ACTIVE.
-- Marque tous users email_verified pour ne pas bloquer login.
-- =====================================================================

UPDATE workspaces SET status = 'ACTIVE' WHERE status = 'PENDING_VERIFICATION';

UPDATE users SET email_verified_at = COALESCE(email_verified_at, created_at)
WHERE email_verified_at IS NULL;

UPDATE users SET must_change_password = FALSE WHERE must_change_password = TRUE;
