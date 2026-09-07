-- Sprint 10 -- Schema upstream pour IT TestContainers dashboard-service.
-- Stub minimal des tables referencees par les aggregators.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS workspaces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    code_workspace VARCHAR(20) UNIQUE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    -- Colonne Sprint 11 lue par SuperAdminDashboardAggregator (repartition par
    -- forfait). Son absence faisait echouer superAdminAggregator AVANT ce lot.
    selected_plan VARCHAR(20),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    trial_status VARCHAR(20),
    trial_ends_at TIMESTAMPTZ,
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
    statut VARCHAR(50) DEFAULT 'CREATION_TICKET',
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





-- Lot IA-1 -- tables consommees par AgentSignalsAggregator
CREATE TABLE IF NOT EXISTS dataroom_depots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID REFERENCES entreprise_dossiers(id),
    title VARCHAR(200),
    filename VARCHAR(255),
    object_key VARCHAR(500),
    size_bytes BIGINT DEFAULT 0,
    uploaded_by UUID,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

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

-- ---------------------------------------------------------------------
-- Lot 1 (2026-09-04) -- referentiel des demarches + cochage. Les dashboards
-- y lisent les echeances legales, qui remplacent les alertes fiscales.
-- Schema minimal, aligne sur ticket-service V20/V21.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS demarches_referentiel (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_type VARCHAR(30) NOT NULL,
    ordre SMALLINT NOT NULL,
    phase_code VARCHAR(8) NOT NULL,
    phase_libelle VARCHAR(60) NOT NULL,
    libelle VARCHAR(400) NOT NULL,
    statut_ticket VARCHAR(30) NOT NULL,
    acteur VARCHAR(80),
    organisme VARCHAR(300),
    obligatoire CHAR(1) NOT NULL,
    condition_application TEXT,
    pieces_entrantes TEXT,
    document_produit TEXT,
    justificatifs_texte TEXT,
    modele_jurika VARCHAR(200),
    delai VARCHAR(300),
    cout_indicatif TEXT,
    variables_alimentees TEXT,
    delai_valeur SMALLINT,
    delai_unite VARCHAR(5),
    delai_reference_ordre SMALLINT,
    UNIQUE (workflow_type, ordre)
);

CREATE TABLE IF NOT EXISTS demarches_justificatifs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    demarche_id UUID NOT NULL REFERENCES demarches_referentiel(id) ON DELETE CASCADE,
    alternative_groupe SMALLINT NOT NULL,
    document_type VARCHAR(60) NOT NULL,
    libelle TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ticket_demarches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    ticket_id UUID NOT NULL REFERENCES tickets(id),
    demarche_id UUID NOT NULL REFERENCES demarches_referentiel(id),
    etat VARCHAR(20) NOT NULL DEFAULT 'A_FAIRE',
    motif TEXT,
    acteur_id UUID,
    coche_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (ticket_id, demarche_id)
);
