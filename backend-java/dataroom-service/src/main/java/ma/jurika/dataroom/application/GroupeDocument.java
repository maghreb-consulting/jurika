package ma.jurika.dataroom.application;

import java.util.Set;

/**
 * Rangement d'un document dans le dossier d'un ticket.
 *
 * <p>Trois groupes, tels que les distingue le guide du cabinet : ce que JURIKA
 * a produit, ce que les administrations ont delivre, ce que le client a fourni.
 *
 * <p>La correspondance type -> groupe est la MEME que celle du backfill de la
 * migration dataroom V24 : un document depose aujourd'hui doit se ranger comme
 * ceux qui l'ont precede.
 */
public enum GroupeDocument {

    ACTES_GENERES("Actes générés"),
    JUSTIFICATIFS_ADMINISTRATIFS("Justificatifs administratifs"),
    PIECES_CLIENT("Pièces client");

    private static final Set<String> ACTES = Set.of(
            "STATUTS", "PV_AGE", "PV_AGO", "PV_MODIFICATION", "PV_DISSOLUTION", "PV_LIQUIDATION",
            "ACTE_NOMINATION", "ANNONCE_JAL", "CONVOCATION", "FEUILLE_PRESENCE",
            "RAPPORT_GESTION", "RAPPORT_LIQUIDATION", "ETAT_ACTES_FORMATION",
            "NOTE_CONFORMITE", "BORDEREAU_REMISE", "FICHE_RENSEIGNEMENTS",
            // Lot 5 — les trois formulaires administratifs sont PRODUITS par JURIKA.
            "DEMANDE_TAXE_PROFESSIONNELLE", "DECLARATION_EXISTENCE",
            "DECLARATION_IMMATRICULATION_RC");

    private static final Set<String> JUSTIFICATIFS = Set.of(
            "RC", "ICE", "TP", "CNSS", "CN", "APOSTILLE", "BULLETIN_IF", "ACCUSE_RBE",
            "ATTESTATION_ENREGISTREMENT", "ATTESTATION_BLOCAGE_CAPITAL", "JOURNAL_ANNONCE",
            "PUBLICATION_BO", "LIVRES_LEGAUX", "AUTORISATION_SECTORIELLE",
            "IDENTIFIANTS_SIMPL", "RECEPISSE_CNDP", "RIB");

    private static final Set<String> PIECES = Set.of(
            "CIN_NOUVELLE", "CIN_ANCIENNE", "CNIE_GERANT", "PIECE_IDENTITE", "CONTRAT_BAIL",
            "CONTRAT_DOMICILIATION", "TITRE_PROPRIETE", "POUVOIR", "VALIDATION_CLIENT",
            "RAPPORT_COMMISSAIRE_APPORTS");

    private final String libelle;

    GroupeDocument(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    /**
     * Groupe deduit du type, ou {@code null} si la nature n'est pas deductible
     * (type {@code AUTRE}, notamment). On ne range pas de force dans un groupe
     * faux : la colonne reste NULL en base.
     */
    public static GroupeDocument deduire(String documentType) {
        if (documentType == null) return null;
        if (ACTES.contains(documentType)) return ACTES_GENERES;
        if (JUSTIFICATIFS.contains(documentType)) return JUSTIFICATIFS_ADMINISTRATIFS;
        if (PIECES.contains(documentType)) return PIECES_CLIENT;
        return null;
    }

    /**
     * Groupe D'AFFICHAGE : les documents dont la nature n'est pas deductible
     * sont montres sous « Pieces client » plutot que masques. Le type n'est PAS
     * ecrit en base pour autant — on ne fabrique pas une donnee qu'on n'a pas.
     */
    public static GroupeDocument pourAffichage(String groupeEnBase, String documentType) {
        if (groupeEnBase != null && !groupeEnBase.isBlank()) {
            try {
                return valueOf(groupeEnBase);
            } catch (IllegalArgumentException ignored) {
                // Valeur inconnue en base : on retombe sur la deduction plutot
                // que de faire disparaitre le document.
            }
        }
        GroupeDocument deduit = deduire(documentType);
        return deduit != null ? deduit : PIECES_CLIENT;
    }
}
