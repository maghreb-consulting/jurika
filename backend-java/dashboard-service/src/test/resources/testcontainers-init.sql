-- Sprint 10 -- Schema upstream pour IT TestContainers dashboard-service.
-- Stub minimal des tables referencees par les aggregators.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    code_workspace VARCHAR(20) UNIQUE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    -- Colonnes reelles de auth-service (V1__init_auth_schema, V16__sprint11_*)
    -- que l'aggregateur SUPER_ADMIN interroge sur `workspaces` : sans elles,
    -- DashboardSprint10IT.superAdminAggregator echoue en BadSqlGrammar.
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    trial_status VARCHAR(20),
    selected_plan VARCHAR(20),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    phone VARCHAR(50),
    role VARCHAR(50) DEFAULT 'EMPLOYE',
    -- Traçabilité (2026-07-15) : statut acteur (ACTIVE / PENDING / INACTIVE)
    -- pour le filtre "Statut de l'acteur" (actifs vs retires).
    status VARCHAR(50) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS entreprise_dossiers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    raison_sociale VARCHAR(500),
    statut VARCHAR(50) DEFAULT 'EN_COURS',
    client_id UUID REFERENCES users(id),
    responsable_id UUID,          -- Lot S : owner durable du dossier (Lot IA-1 scoping)
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tickets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    reference VARCHAR(50),
    titre VARCHAR(500),
    type VARCHAR(100),
    statut VARCHAR(50) DEFAULT 'NOUVEAU',
    priorite VARCHAR(20) DEFAULT 'NORMALE',
    dossier_id UUID REFERENCES entreprise_dossiers(id),
    assigne_id UUID REFERENCES users(id),
    cloture_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dataroom_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID REFERENCES entreprise_dossiers(id),
    document_type VARCHAR(40),
    title VARCHAR(200),
    filename VARCHAR(255),
    size_bytes BIGINT DEFAULT 0,
    is_current BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dataroom_comptable_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID,
    size_bytes BIGINT DEFAULT 0,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS dataroom_fiscal_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID,
    size_bytes BIGINT DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS dataroom_exercices_fiscaux (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID REFERENCES entreprise_dossiers(id),
    statut VARCHAR(20) DEFAULT 'OUVERT',
    closed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS dataroom_alertes_echeances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID REFERENCES entreprise_dossiers(id),
    exercice_fiscal_id UUID,
    type_echeance VARCHAR(40),
    date_echeance DATE,
    statut VARCHAR(20) DEFAULT 'PLANIFIEE',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Lot IA-1 -- tables consommees par AgentSignalsAggregator
CREATE TABLE IF NOT EXISTS deadlines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    ticket_id UUID REFERENCES tickets(id),
    dossier_id UUID REFERENCES entreprise_dossiers(id),
    title VARCHAR(200),
    due_at TIMESTAMPTZ NOT NULL,
    severity VARCHAR(10) DEFAULT 'INFO',
    statut VARCHAR(20) DEFAULT 'OUVERTE',
    assigne_id UUID,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dataroom_demandes_client (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID REFERENCES entreprise_dossiers(id),
    sujet VARCHAR(200),
    statut VARCHAR(20) DEFAULT 'NON_TRAITEE',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS dataroom_client_access_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID REFERENCES entreprise_dossiers(id),
    user_id UUID,
    action VARCHAR(20),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    workspace_id UUID,
    user_id UUID,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80),
    entity_id UUID,
    metadata JSONB,
    source_service VARCHAR(40),
    correlation_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
