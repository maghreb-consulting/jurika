package ma.jurika.dataroom.application;

import java.util.List;

/**
 * Catalogue des types de document acceptes par la Data Room.
 *
 * <p>Lot 2 (2026-09-07). La liste deroulante « Uploader document » vivait en dur
 * dans le frontend : elle proposait 16 types alors que la base en accepte 44
 * depuis le lot 1. Consequence concrete : un employe qui deposait a la main un
 * certificat negatif, un contrat de domiciliation, un titre de propriete, une
 * attestation d'enregistrement, un pouvoir ou un rapport de commissaire aux
 * apports le rangeait sous « AUTRE » — le document devenait introuvable par
 * type, et n'etait plus reconnu comme le justificatif attendu par la demarche
 * correspondante.
 *
 * <p>Une seule source fait donc foi desormais : cette liste, exposee par
 * l'API, alignee sur la contrainte {@code CHECK} de la table et sur le
 * rangement de {@link GroupeDocument}. Ajouter un type reste un seul geste
 * (migration + cette liste), et le frontend suit sans qu'on y pense.
 */
public final class DocumentTypeCatalogue {

    /**
     * @param code    valeur stockee en base ({@code CONTRAT_DOMICILIATION})
     * @param libelle intitule affiche a l'employe
     * @param groupe  rangement dans le dossier du ticket, {@code null} si la
     *                nature ne se deduit pas (cas d'{@code AUTRE})
     */
    public record TypeDocument(String code, String libelle, String groupe) {}

    /**
     * Ordre d'affichage : du plus structurant au plus accessoire, « Autre » en
     * dernier. C'est l'ordre dans lequel un juriste cherche une piece.
     */
    private static final List<String[]> TYPES = List.of(
            // — Actes rediges par le cabinet —
            new String[] {"STATUTS", "Statuts"},
            new String[] {"PV_AGE", "PV AGE"},
            new String[] {"PV_AGO", "PV AGO"},
            new String[] {"PV_MODIFICATION", "PV Modification"},
            new String[] {"PV_DISSOLUTION", "PV Dissolution"},
            new String[] {"PV_LIQUIDATION", "PV Liquidation"},
            new String[] {"ACTE_NOMINATION", "Acte de nomination"},
            new String[] {"CONVOCATION", "Convocation"},
            new String[] {"FEUILLE_PRESENCE", "Feuille de présence"},
            new String[] {"ANNONCE_JAL", "Annonce légale (JAL)"},
            new String[] {"RAPPORT_GESTION", "Rapport de gestion"},
            new String[] {"RAPPORT_LIQUIDATION", "Rapport de liquidation"},
            new String[] {"ETAT_ACTES_FORMATION", "État des actes en formation"},
            new String[] {"FICHE_RENSEIGNEMENTS", "Fiche de renseignements"},
            new String[] {"NOTE_CONFORMITE", "Note de conformité"},
            new String[] {"BORDEREAU_REMISE", "Bordereau de remise"},
            // Lot 5 (2026-09-07) — formulaires administratifs DEPOSES par le cabinet.
            // A ne pas confondre avec les documents RECUS qu'ils font obtenir (TP,
            // BULLETIN_IF, RC), qui figurent plus bas parmi les justificatifs.
            new String[] {"DEMANDE_TAXE_PROFESSIONNELLE", "Demande d'inscription à la taxe professionnelle"},
            new String[] {"DECLARATION_EXISTENCE", "Déclaration d'existence"},
            new String[] {"DECLARATION_IMMATRICULATION_RC", "Déclaration d'immatriculation au RC (modèle 2)"},
            // — Justificatifs delivres par les administrations —
            new String[] {"CN", "Certificat négatif"},
            new String[] {"ATTESTATION_ENREGISTREMENT", "Attestation d'enregistrement"},
            new String[] {"ATTESTATION_BLOCAGE_CAPITAL", "Attestation de blocage du capital"},
            new String[] {"RC", "Registre du commerce"},
            new String[] {"ICE", "Identifiant ICE"},
            new String[] {"TP", "Taxe professionnelle"},
            new String[] {"BULLETIN_IF", "Bulletin d'identification fiscale"},
            new String[] {"CNSS", "CNSS"},
            new String[] {"ACCUSE_RBE", "Accusé bénéficiaires effectifs"},
            new String[] {"JOURNAL_ANNONCE", "Journal d'annonces légales"},
            new String[] {"PUBLICATION_BO", "Publication au Bulletin officiel"},
            new String[] {"LIVRES_LEGAUX", "Livres légaux cotés et paraphés"},
            new String[] {"AUTORISATION_SECTORIELLE", "Autorisation sectorielle"},
            new String[] {"IDENTIFIANTS_SIMPL", "Identifiants SIMPL"},
            new String[] {"RECEPISSE_CNDP", "Récépissé CNDP"},
            new String[] {"APOSTILLE", "Apostille"},
            new String[] {"RIB", "RIB"},
            // — Pieces fournies par le client —
            new String[] {"CIN_NOUVELLE", "CIN (nouvelle)"},
            new String[] {"CIN_ANCIENNE", "CIN (ancienne)"},
            new String[] {"CNIE_GERANT", "CNIE du gérant"},
            new String[] {"PIECE_IDENTITE", "Pièce d'identité"},
            new String[] {"CONTRAT_BAIL", "Contrat de bail"},
            new String[] {"CONTRAT_DOMICILIATION", "Contrat de domiciliation"},
            new String[] {"TITRE_PROPRIETE", "Titre de propriété"},
            new String[] {"POUVOIR", "Pouvoir / procuration"},
            new String[] {"RAPPORT_COMMISSAIRE_APPORTS", "Rapport du commissaire aux apports"},
            new String[] {"VALIDATION_CLIENT", "Validation du client"},
            // — Dernier recours —
            new String[] {"AUTRE", "Autre"});

    private DocumentTypeCatalogue() {}

    public static List<TypeDocument> tous() {
        return TYPES.stream()
                .map(t -> {
                    GroupeDocument g = GroupeDocument.deduire(t[0]);
                    return new TypeDocument(t[0], t[1], g == null ? null : g.name());
                })
                .toList();
    }
}
