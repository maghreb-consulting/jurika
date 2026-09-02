-- Add missing 'source' column to entreprise_dossiers
ALTER TABLE entreprise_dossiers ADD COLUMN IF NOT EXISTS source VARCHAR(50);

-- Add missing 'fcm_token' column to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS fcm_token VARCHAR(500);
