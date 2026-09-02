-- =====================================================================
-- JURIKA V8 -- RG-DC27 : Notification email comptable a chaque upload comptable
-- Reference : docs/v2/Regles_de_Gestion_V2.md RG-DC27
--
-- Ajout colonne `accountant_email` (nullable) sur dataroom_settings.
-- Si renseignee, un email "notif upload comptable" est envoye a cette adresse.
-- Si NULL, pas de notification.
-- =====================================================================

ALTER TABLE dataroom_settings
    ADD COLUMN IF NOT EXISTS accountant_email VARCHAR(255);

ALTER TABLE dataroom_settings
    ADD COLUMN IF NOT EXISTS notify_accountant_on_upload BOOLEAN NOT NULL DEFAULT FALSE;
