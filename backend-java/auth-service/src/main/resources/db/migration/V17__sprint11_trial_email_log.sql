-- Sprint 11 TASK 5 — Trial email log (anti double-envoi)
--
-- Une ligne par (workspace_id, email_type). PK composite garantit
-- l'idempotence du scheduler quotidien (RG-SU10).
--
-- email_type valeurs :
--   - CABINET_WELCOME  (envoye immediate post-signup par RegisterWorkspaceUseCase)
--   - TRIAL_DAY_1
--   - TRIAL_DAY_7
--   - TRIAL_DAY_12
--   - TRIAL_EXPIRED    (envoye apres expiration par TrialExpirationScheduler)

CREATE TABLE IF NOT EXISTS trial_email_log (
    workspace_id  UUID         NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
    email_type    VARCHAR(40)  NOT NULL,
    sent_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    smtp_message_id VARCHAR(200),
    PRIMARY KEY (workspace_id, email_type)
);

CREATE INDEX IF NOT EXISTS idx_trial_email_log_sent_at ON trial_email_log (sent_at DESC);

COMMENT ON TABLE trial_email_log IS 'Sprint 11 RG-SU10 -- anti double-envoi emails trial (PK composite)';
