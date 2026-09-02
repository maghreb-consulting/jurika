-- Sprint 1 / TASK 5 : indexes pour le hardening refresh token (RG-SAAS-12)
--   * idx_refresh_tokens_user_active     : accelere count + scan des sessions actives par user
--   * idx_refresh_tokens_expires         : accelere le job de cleanup quotidien
-- Voir RefreshTokenCleanupJob + LoginUseCase (max 5 sessions actives par user).

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_active
    ON refresh_tokens (user_id, workspace_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires
    ON refresh_tokens (expires_at)
    WHERE revoked_at IS NULL;
