-- Sprint 7 / TASK 7.2 -- Schema upstream minimal pour les IT TestContainers.
--
-- Les migrations Flyway de dataroom-service (V5-V12) reference workspaces,
-- entreprise_dossiers, users, tickets. Ces tables sont normalement creees
-- par le init-db.sh global + auth-service V1. En IT isole on les stub ici.
-- Schema simplifie : on conserve uniquement les colonnes/index/contraintes
-- requis par les FK et entities du dataroom-service.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Role non-superuser dedie aux tests RLS (FiscalListAndFilterIT cas 5).
-- L'utilisateur de connexion testcontainer est superuser et bypass RLS,
-- donc tester l'isolation cross-workspace exige SET LOCAL ROLE app_no_super
-- dans une transaction explicite. GRANT minimal sur les tables fiscales
-- pour autoriser les lectures (les ecritures se font sous la connexion
-- superuser standard via JPA, donc pas besoin d'INSERT/UPDATE pour ce role).
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_no_super') THEN
        CREATE ROLE app_no_super NOLOGIN NOSUPERUSER NOBYPASSRLS;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    code_workspace VARCHAR(20) UNIQUE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) DEFAULT '',
    full_name VARCHAR(255) DEFAULT '',
    role VARCHAR(50) DEFAULT 'EMPLOYE',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS entreprise_dossiers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    raison_sociale VARCHAR(500),
    forme_juridique VARCHAR(50),
    ice VARCHAR(50),
    rc_numero VARCHAR(100),
    rc_tribunal VARCHAR(255),
    ville VARCHAR(100),
    adresse_siege TEXT,
    capital_social_mad DECIMAL,
    date_constitution DATE,
    statut VARCHAR(50) DEFAULT 'EN_COURS',
    client_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Lot 1 (2026-09-04) — le libelle du dossier de ticket lit les sous-types de
-- MODIFICATION dans les donnees du workflow. Table creee par workflow-service
-- en production ; stubbee ici comme les autres tables amont.
CREATE TABLE IF NOT EXISTS workflow_progress (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL,
    ticket_id UUID NOT NULL,
    workflow_type VARCHAR(40),
    statut VARCHAR(20) DEFAULT 'EN_COURS',
    data JSONB DEFAULT '{}'::jsonb,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tickets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    reference VARCHAR(50),
    titre VARCHAR(500),
    type VARCHAR(100),
    statut VARCHAR(50) DEFAULT 'CREATION_TICKET',
    dossier_id UUID REFERENCES entreprise_dossiers(id),
    cloture_at TIMESTAMPTZ,
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Lot L0 (E9) : role d'execution des services, comme init-db.sh en production :
-- non proprietaire, NOSUPERUSER, NOBYPASSRLS. Les migrations de droits (E10)
-- echouent s'il est absent ; les tests connectes en jurika_app (E11 et
-- suivantes) prouvent la RLS, que l'utilisateur superuser du conteneur ignore.
DO $$ BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'jurika_app') THEN
        CREATE ROLE jurika_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD 'jurika_app_it';
    END IF;
END $$;
