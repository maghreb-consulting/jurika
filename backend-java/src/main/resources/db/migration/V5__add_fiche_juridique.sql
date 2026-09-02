-- Clean up potentially inconsistent tables from failed previous attempts or legacy state
DROP TABLE IF EXISTS evenements_juridiques;
DROP TABLE IF EXISTS representants;
DROP TABLE IF EXISTS etablissement_secondaire;
DROP TABLE IF EXISTS fiche_juridique;

CREATE TABLE fiche_juridique (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    dossier_id UUID NOT NULL UNIQUE,
    ice VARCHAR(50),
    numero_rc VARCHAR(50),
    date_creation DATE,
    abreviation VARCHAR(100),
    capital_social DECIMAL(15,2),
    forme_juridique VARCHAR(50),
    statut_juridique VARCHAR(50),
    activite_principale TEXT,
    activite_reglementee BOOLEAN,
    autorisation_reglementee VARCHAR(255),
    code_naf VARCHAR(20),
    adresse_siege TEXT,
    activite_au_siege TEXT,
    enseigne VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE etablissement_secondaire (
    id UUID PRIMARY KEY,
    fiche_juridique_id UUID NOT NULL REFERENCES fiche_juridique(id) ON DELETE CASCADE,
    adresse TEXT,
    activite TEXT,
    denomination VARCHAR(255)
);

CREATE TABLE representants (
    id UUID PRIMARY KEY,
    fiche_juridique_id UUID NOT NULL REFERENCES fiche_juridique(id) ON DELETE CASCADE,
    nom_prenom VARCHAR(255),
    qualite VARCHAR(50),
    cin_chiffre VARCHAR(500),
    date_prise_fonction DATE
);

CREATE TABLE evenements_juridiques (
    id UUID PRIMARY KEY,
    fiche_juridique_id UUID NOT NULL REFERENCES fiche_juridique(id) ON DELETE CASCADE,
    date DATE,
    type_evenement VARCHAR(50),
    description TEXT,
    document_id UUID,
    auteur_nom VARCHAR(255),
    version_dossier INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
