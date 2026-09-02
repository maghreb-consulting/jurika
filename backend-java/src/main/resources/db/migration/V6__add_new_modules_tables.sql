-- Module 4: Modification d'Entreprise
CREATE TABLE modification_dossiers (
    id UUID PRIMARY KEY,
    dossier_id UUID NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    type VARCHAR(100) NOT NULL,
    description TEXT,
    ancienne_valeur TEXT,
    nouvelle_valeur TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Module 6: Succursales
CREATE TABLE succursales (
    id UUID PRIMARY KEY,
    parent_dossier_id UUID NOT NULL REFERENCES entreprise_dossiers(id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    denomination VARCHAR(500) NOT NULL,
    adresse TEXT,
    rc_number VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Module 14: Workspace FAQ
CREATE TABLE workspace_faq (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    question TEXT NOT NULL,
    response TEXT,
    published_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_modifs_dossier ON modification_dossiers(dossier_id);
CREATE INDEX idx_succursales_parent ON succursales(parent_dossier_id);
CREATE INDEX idx_faq_workspace ON workspace_faq(workspace_id);
