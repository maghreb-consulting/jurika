-- Extensions PostgreSQL requises par les migrations Flyway de auth-service.
-- En prod : infrastructure/scripts/init-db.sh fait la meme chose au demarrage du conteneur PG.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
