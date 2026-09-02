-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Workspaces
CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    code_workspace VARCHAR(20) UNIQUE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Subscriptions
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    type VARCHAR(50) NOT NULL DEFAULT 'STARTER',
    max_users INTEGER DEFAULT 5,
    storage_limit_gb DECIMAL DEFAULT 10,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'EMPLOYE',
    totp_secret VARCHAR(255),
    totp_enabled BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    failed_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    fcm_token VARCHAR(500),
    last_login TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(workspace_id, email)
);

-- Dossiers
CREATE TABLE entreprise_dossiers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    auteur_id UUID NOT NULL REFERENCES users(id),
    reference VARCHAR(50) UNIQUE,
    denomination VARCHAR(500) NOT NULL,
    ice VARCHAR(50),
    rc_number VARCHAR(100),
    forme_juridique VARCHAR(50) NOT NULL,
    capital_social DECIMAL,
    siege_social TEXT,
    gerant VARCHAR(255),
    objet_social TEXT,
    date_echeance DATE,
    statut VARCHAR(50) DEFAULT 'EN_COURS',
    lecture_seule BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Fiche juridique
CREATE TABLE fiche_juridique (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID NOT NULL UNIQUE REFERENCES entreprise_dossiers(id),
    statut_juridique VARCHAR(100),
    representant_legal VARCHAR(255),
    date_constitution DATE,
    derniere_modification TIMESTAMP DEFAULT NOW()
);

-- Documents
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID NOT NULL REFERENCES entreprise_dossiers(id),
    title VARCHAR(500) NOT NULL,
    file_path_uuid VARCHAR(500),
    file_url TEXT,
    version_number INTEGER DEFAULT 1,
    is_ia_generated BOOLEAN DEFAULT FALSE,
    edit_mention TEXT,
    is_current BOOLEAN DEFAULT TRUE,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Tickets
CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID NOT NULL REFERENCES entreprise_dossiers(id),
    auteur_id UUID NOT NULL REFERENCES users(id),
    assigned_to UUID REFERENCES users(id),
    type VARCHAR(100) NOT NULL,
    statut VARCHAR(50) DEFAULT 'OUVERT',
    description TEXT,
    blocked_reason TEXT,
    blocked_since TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    closed_at TIMESTAMP
);

-- Evenements juridiques
CREATE TABLE evenements_juridiques (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID NOT NULL REFERENCES entreprise_dossiers(id),
    type_evenement VARCHAR(100) NOT NULL,
    description TEXT,
    date_evenement DATE DEFAULT CURRENT_DATE,
    reference_doc VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Historique
CREATE TABLE historique (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID REFERENCES entreprise_dossiers(id),
    user_id UUID REFERENCES users(id),
    action VARCHAR(255) NOT NULL,
    ancienne_valeur TEXT,
    nouvelle_valeur TEXT,
    ip_address VARCHAR(50),
    timestamp TIMESTAMP DEFAULT NOW()
);

-- Notifications
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Audit log
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID REFERENCES workspaces(id),
    user_id UUID REFERENCES users(id),
    action VARCHAR(255) NOT NULL,
    ip_address VARCHAR(50),
    old_value TEXT,
    new_value TEXT,
    timestamp TIMESTAMP DEFAULT NOW()
);

-- Chatbot history
CREATE TABLE chatbot_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id UUID NOT NULL REFERENCES users(id),
    query TEXT NOT NULL,
    response TEXT,
    sources_refs TEXT,
    timestamp TIMESTAMP DEFAULT NOW()
);

-- Transferts
CREATE TABLE transferts_dossiers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    dossier_id UUID NOT NULL REFERENCES entreprise_dossiers(id),
    ancien_auteur_id UUID NOT NULL REFERENCES users(id),
    nouvel_auteur_id UUID NOT NULL REFERENCES users(id),
    superviseur_id UUID NOT NULL REFERENCES users(id),
    raison TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_users_workspace ON users(workspace_id);
CREATE INDEX idx_dossiers_workspace ON entreprise_dossiers(workspace_id);
CREATE INDEX idx_dossiers_auteur ON entreprise_dossiers(auteur_id);
CREATE INDEX idx_tickets_workspace ON tickets(workspace_id);
CREATE INDEX idx_tickets_dossier ON tickets(dossier_id);
CREATE INDEX idx_documents_dossier ON documents(dossier_id);
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_historique_dossier ON historique(dossier_id);
CREATE INDEX idx_audit_workspace ON audit_log(workspace_id);
