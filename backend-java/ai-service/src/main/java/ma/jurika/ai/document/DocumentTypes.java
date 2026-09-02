package ma.jurika.ai.document;

import java.util.Set;

/**
 * Catalogue des 42 types de documents generes par la plateforme JURIKA.
 * Reference : docs/v2/Documents_a_fournir.md
 */
public final class DocumentTypes {

    private DocumentTypes() {}

    // Creation SARL (9)
    public static final String STATUTS_CONSTITUTIFS = "STATUTS_CONSTITUTIFS";
    // Phase E2 (2026-08-09) — Retrait du dernier LEGACY : les gabarits
    // STATUTS_CONSTITUTIFS_SARL / _AU (placeholder {{}}) ont été supprimés. La
    // refonte des statuts (voie MODIFICATION) passe désormais par la voie directeur
    // (STATUTS_SARL_DIRECTEUR / _AU via RefonteStatutsVarsBuilder). La voie CRÉATION
    // LEGACY (ACTE_NOMINATION_GERANT, DECLARATION_SOUSCRIPTION_VERSEMENT, ANNONCE_JAL_*,
    // AVIS_CONSTITUTION_SARL) avait déjà été retirée (voie directeur uniquement).
    public static final String ATTESTATION_BLOCAGE_FONDS = "ATTESTATION_BLOCAGE_FONDS";
    public static final String DEMANDE_CERTIFICAT_NEGATIF = "DEMANDE_CERTIFICAT_NEGATIF";
    public static final String ANNONCE_JAL_CONSTITUTION = "ANNONCE_JAL_CONSTITUTION";
    public static final String FORMULAIRE_RC_IMMATRICULATION = "FORMULAIRE_RC_IMMATRICULATION";
    public static final String DECLARATION_EXISTENCE_FISCALE = "DECLARATION_EXISTENCE_FISCALE";
    public static final String DECLARATION_AFFILIATION_CNSS = "DECLARATION_AFFILIATION_CNSS";

    // Modification
    public static final String STATUTS_MODIFIES = "STATUTS_MODIFIES";
    // 2026-08-10 (audit directeur) — l'avenant STATUTS_MODIFIES_SARL / _AU a été retiré
    // (doublon des statuts refondus STATUTS_REFONDUS_*, voie directeur Phase E2).
    public static final String PV_AGE_MODIFICATION = "PV_AGE_MODIFICATION";
    public static final String ANNONCE_JAL_MODIFICATIVE = "ANNONCE_JAL_MODIFICATIVE";
    public static final String FORMULAIRE_RC_MODIFICATIF = "FORMULAIRE_RC_MODIFICATIF";
    // Modification (Phase E1) — PV à résolutions typées (modèles directeur unifiés SARL / SARL AU,
    // 32 types de résolutions pilotés par $RESOLUTION_TYPE). Réutilisent le noyau séance (Phase A).
    // Le versioning des statuts (STATUTS_MODIFIES_*) et le retrait de la voie LEGACY (PV_AGE_*)
    // sont différés au lot E2.
    public static final String PV_MODIFICATION_SARL = "PV_MODIFICATION_SARL";
    public static final String PV_MODIFICATION_SARL_AU = "PV_MODIFICATION_SARL_AU";
    // Phase 2 (2026-08-11) — Annonce légale de modification (avis JAL, boucle DECISIONS
    // publiables) déclinée SARL vs SARL AU. Remplace l'ancien ANNONCE_JAL_MODIFICATIVE non câblé.
    public static final String ANNONCE_LEGALE_MODIFICATION_SARL = "ANNONCE_LEGALE_MODIFICATION_SARL";
    public static final String ANNONCE_LEGALE_MODIFICATION_SARL_AU = "ANNONCE_LEGALE_MODIFICATION_SARL_AU";

    // Dissolution / Liquidation (Phase C) — modèles directeur.
    // Un seul PV unifié couvre les trois étapes du cycle (dissolution / cours de
    // liquidation / clôture) via $PV_ETAPE, décliné SARL vs SARL AU. Le rapport du
    // liquidateur est un document distinct. Les anciens codes (PV_AGE_DISSOLUTION,
    // PV_AGE_LIQUIDATION_CLOTURE, RAPPORT_LIQUIDATION, PV_DISSOLUTION_SARL(_AU),
    // PV_LIQUIDATION_SARL(_AU)) ont été retirés (remplacés).
    public static final String PV_DISSOLUTION_LIQUIDATION_SARL = "PV_DISSOLUTION_LIQUIDATION_SARL";
    public static final String PV_DISSOLUTION_LIQUIDATION_SARL_AU = "PV_DISSOLUTION_LIQUIDATION_SARL_AU";
    public static final String RAPPORT_LIQUIDATION_DIRECTEUR = "RAPPORT_LIQUIDATION_DIRECTEUR";
    // 2026-08-12 — Annonce légale de DISSOLUTION (avis de dissolution anticipée + nomination
    // du liquidateur + siège de la liquidation), modèles directeur déclinés SARL / SARL AU.
    // Premier des deux avis du cycle (l'avis de clôture relève de la Liquidation).
    public static final String ANNONCE_LEGALE_DISSOLUTION_SARL = "ANNONCE_LEGALE_DISSOLUTION_SARL";
    public static final String ANNONCE_LEGALE_DISSOLUTION_SARL_AU = "ANNONCE_LEGALE_DISSOLUTION_SARL_AU";
    // 2026-08-13 — Annonce légale de CLÔTURE DE LIQUIDATION (approbation des comptes définitifs,
    // boni/mali, quitus au liquidateur), modèles directeur déclinés SARL / SARL AU. Second et
    // dernier avis du cycle : il fonde la radiation de la société au registre du commerce.
    public static final String ANNONCE_LEGALE_LIQUIDATION_SARL = "ANNONCE_LEGALE_LIQUIDATION_SARL";
    public static final String ANNONCE_LEGALE_LIQUIDATION_SARL_AU = "ANNONCE_LEGALE_LIQUIDATION_SARL_AU";
    public static final String ANNONCE_JAL_DISSOLUTION = "ANNONCE_JAL_DISSOLUTION";
    public static final String FORMULAIRE_RC_DISSOLUTION = "FORMULAIRE_RC_DISSOLUTION";
    public static final String ANNONCE_JAL_LIQUIDATION = "ANNONCE_JAL_LIQUIDATION";
    public static final String FORMULAIRE_RC_RADIATION = "FORMULAIRE_RC_RADIATION";

    // Succursale — documents COMPAGNONS conservés (annexes RC / JAL / déclarations non
    // remplacées par les PV directeur de la Phase D). Les PV/décisions remplacés ont été
    // retirés : PV_AGE_OUVERTURE_SUCCURSALE (+ alias), ANNONCE_JAL_OUVERTURE_SUCCURSALE,
    // STATUTS_MODIFIES_SUCCURSALE (voie succursale : « l'ouverture ne modifie pas les statuts »),
    // DECISION_CONSEIL_ETRANGER, PROCURATION_DIRECTEUR_RESIDENT, PV_AGE_FERMETURE_SUCCURSALE.
    // ⚠ Signalé : ces compagnons n'ont pas de modèle directeur ni de câblage actif ; ils sont
    // conservés (hors périmètre Phase D) en attendant leurs propres modèles.
    // Lot DIVERS §B (2026-08-13) — ANNONCE_JAL_OUVERTURE_SUCCURSALE RETIRÉ : le directeur a
    // livré son remplaçant (ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL / _AU), qui couvre le
    // même avis avec les variables du dictionnaire des assemblées et les blocs conditionnels
    // dotation / responsable absents de l'ancien. Le doublon aurait laissé deux avis
    // d'ouverture concurrents pour le même acte.
    public static final String FORMULAIRE_RC_SUCCURSALE = "FORMULAIRE_RC_SUCCURSALE";
    public static final String FORMULAIRE_RC_SUCCURSALE_ETR = "FORMULAIRE_RC_SUCCURSALE_ETR";
    public static final String ANNONCE_JAL_OUVERTURE_ETR = "ANNONCE_JAL_OUVERTURE_ETR";
    public static final String DECLARATION_EXISTENCE_SUCCURSALE = "DECLARATION_EXISTENCE_SUCCURSALE";
    public static final String DECLARATION_CNSS_SUCCURSALE = "DECLARATION_CNSS_SUCCURSALE";
    public static final String ANNONCE_JAL_FERMETURE = "ANNONCE_JAL_FERMETURE";
    public static final String FORMULAIRE_RC_RADIATION_SUCCURSALE = "FORMULAIRE_RC_RADIATION_SUCCURSALE";

    // Succursale (Phase D) — modèles directeur unifiés SARL / SARL AU.
    // Trois workflows, six PV : création succursale d'une société marocaine (identité BD),
    // création au Maroc d'une succursale d'une société étrangère (société mère saisie),
    // et fermeture de succursale. Réutilisent le noyau séance (Phase A). Les anciens codes
    // (PV_AGE_OUVERTURE_SUCCURSALE, PV_OUVERTURE_SUCCURSALE, ANNONCE_JAL_OUVERTURE_SUCCURSALE,
    // DECISION_CONSEIL_ETRANGER, PROCURATION_DIRECTEUR_RESIDENT, STATUTS_MODIFIES_SUCCURSALE,
    // PV_AGE_FERMETURE_SUCCURSALE) sont retirés (remplacés).
    public static final String PV_CREATION_SUCCURSALE_MAROC_SARL = "PV_CREATION_SUCCURSALE_MAROC_SARL";
    public static final String PV_CREATION_SUCCURSALE_MAROC_SARL_AU = "PV_CREATION_SUCCURSALE_MAROC_SARL_AU";
    public static final String PV_CREATION_SUCCURSALE_ETRANGERE_SARL = "PV_CREATION_SUCCURSALE_ETRANGERE_SARL";
    public static final String PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU = "PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU";
    public static final String PV_FERMETURE_SUCCURSALE_SARL = "PV_FERMETURE_SUCCURSALE_SARL";
    public static final String PV_FERMETURE_SUCCURSALE_SARL_AU = "PV_FERMETURE_SUCCURSALE_SARL_AU";

    // Lot DIVERS §B (2026-08-13) — annonce légale d'OUVERTURE de succursale (avis publié au
    // journal d'annonces légales, blocs conditionnels dotation / responsable). Remplace
    // l'ancien ANNONCE_JAL_OUVERTURE_SUCCURSALE, retiré.
    public static final String ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL =
            "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL";
    public static final String ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU =
            "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU";

    // Lot DIVERS §C (2026-08-13) — VARIANTE ÉTRANGÈRE de l'avis d'ouverture.
    // ⚠ origin = "derive-jurika" : ces deux modèles NE viennent PAS du directeur.
    // Ils sont dérivés des modèles 05_ (mère marocaine) en remplaçant le SEUL chapeau
    // d'identification par les variables $SOCIETE_MERE_* ; le corps est repris mot
    // pour mot. À FAIRE VALIDER PAR LE DIRECTEUR avant usage en production.
    public static final String ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL =
            "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL";
    public static final String ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU =
            "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU";

    // Lot DIVERS §D (2026-08-13) — annonce légale de FERMETURE de succursale (modèles
    // directeur). Avis linéaire : le MOTIF y est publié (« Cette fermeture est motivée
    // par … »), contrairement à la dissolution où il reste au PV.
    public static final String ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL =
            "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL";
    public static final String ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL_AU =
            "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL_AU";

    // PV AGO — approbation des comptes (AGO annuelle). La voie « affectation du
    // résultat » (PV_AGO_SARL / _AU, PV_AGO_AFFECTATION_RESULTAT_*, AFFECTATION_RESULTAT)
    // a été retirée (Phase B) : elle est remplacée par les PV directeur d'approbation
    // des comptes ci-dessous. RAPPORT_GESTION (rapport de la gérance) est conservé.
    public static final String PV_APPROBATION_COMPTES_SARL = "PV_APPROBATION_COMPTES_SARL";
    public static final String PV_APPROBATION_COMPTES_SARL_AU = "PV_APPROBATION_COMPTES_SARL_AU";
    public static final String RAPPORT_GESTION = "RAPPORT_GESTION";

    // Transversaux (5)
    public static final String ETAT_DEBOURS_PDF = "ETAT_DEBOURS_PDF";
    public static final String FICHE_JURIDIQUE = "FICHE_JURIDIQUE";
    public static final String LETTRE_CONVOCATION_AGE = "LETTRE_CONVOCATION_AGE";
    public static final String LETTRE_CONVOCATION_AGO = "LETTRE_CONVOCATION_AGO";
    public static final String FEUILLE_PRESENCE = "FEUILLE_PRESENCE";
    // 2026-08-10 (audit directeur) — PV_OUVERTURE_COMPTE_BANCAIRE retiré : template
    // orphelin (généré par aucun workflow, voie création legacy retirée en E2).

    // Modeles deterministes officiels du directeur (CREATION SARL / SARL AU)
    public static final String STATUTS_SARL_DIRECTEUR = "STATUTS_SARL_DIRECTEUR";
    public static final String STATUTS_SARL_AU_DIRECTEUR = "STATUTS_SARL_AU_DIRECTEUR";
    public static final String ACTE_NOMINATION_GERANT_DIRECTEUR = "ACTE_NOMINATION_GERANT_DIRECTEUR";
    public static final String ANNONCE_LEGALE_DIRECTEUR = "ANNONCE_LEGALE_DIRECTEUR";

    // PV de seance (incident) transverses — generables dans tout workflow tenant une AG (Phase 4)
    public static final String PV_DEFAUT_QUORUM_SARL = "PV_DEFAUT_QUORUM_SARL";
    public static final String PV_DEFAUT_QUORUM_SARL_AU = "PV_DEFAUT_QUORUM_SARL_AU";
    public static final String PV_IRREGULARITE_CONVOCATION_SARL = "PV_IRREGULARITE_CONVOCATION_SARL";
    public static final String PV_IRREGULARITE_CONVOCATION_SARL_AU = "PV_IRREGULARITE_CONVOCATION_SARL_AU";

    // Documents de seance partages (Phase A) — Convocation (debut) + Feuille de presence (apres AG).
    // Modeles directeur unifies : un seul fichier couvre SARL et SARL AU via $ASSOCIE_UNIQUE.
    public static final String CONVOCATION_AG = "CONVOCATION_AG";
    public static final String FEUILLE_PRESENCE_AG = "FEUILLE_PRESENCE_AG";

    public static final Set<String> ALL = Set.of(
            STATUTS_CONSTITUTIFS,
            ATTESTATION_BLOCAGE_FONDS, DEMANDE_CERTIFICAT_NEGATIF, ANNONCE_JAL_CONSTITUTION,
            FORMULAIRE_RC_IMMATRICULATION, DECLARATION_EXISTENCE_FISCALE, DECLARATION_AFFILIATION_CNSS,
            STATUTS_MODIFIES,
            PV_AGE_MODIFICATION, ANNONCE_JAL_MODIFICATIVE, FORMULAIRE_RC_MODIFICATIF,
            PV_MODIFICATION_SARL, PV_MODIFICATION_SARL_AU,
            ANNONCE_LEGALE_MODIFICATION_SARL, ANNONCE_LEGALE_MODIFICATION_SARL_AU,
            PV_DISSOLUTION_LIQUIDATION_SARL, PV_DISSOLUTION_LIQUIDATION_SARL_AU,
            RAPPORT_LIQUIDATION_DIRECTEUR,
            ANNONCE_LEGALE_DISSOLUTION_SARL, ANNONCE_LEGALE_DISSOLUTION_SARL_AU,
            ANNONCE_LEGALE_LIQUIDATION_SARL, ANNONCE_LEGALE_LIQUIDATION_SARL_AU,
            ANNONCE_JAL_DISSOLUTION, FORMULAIRE_RC_DISSOLUTION,
            ANNONCE_JAL_LIQUIDATION, FORMULAIRE_RC_RADIATION,
            FORMULAIRE_RC_SUCCURSALE,
            FORMULAIRE_RC_SUCCURSALE_ETR, ANNONCE_JAL_OUVERTURE_ETR, DECLARATION_EXISTENCE_SUCCURSALE,
            DECLARATION_CNSS_SUCCURSALE, ANNONCE_JAL_FERMETURE,
            FORMULAIRE_RC_RADIATION_SUCCURSALE,
            PV_CREATION_SUCCURSALE_MAROC_SARL, PV_CREATION_SUCCURSALE_MAROC_SARL_AU,
            PV_CREATION_SUCCURSALE_ETRANGERE_SARL, PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU,
            PV_FERMETURE_SUCCURSALE_SARL, PV_FERMETURE_SUCCURSALE_SARL_AU,
            ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL, ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU,
            ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL,
            ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU,
            ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL,
            ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL_AU,
            PV_APPROBATION_COMPTES_SARL, PV_APPROBATION_COMPTES_SARL_AU, RAPPORT_GESTION,
            ETAT_DEBOURS_PDF, FICHE_JURIDIQUE, LETTRE_CONVOCATION_AGE,
            LETTRE_CONVOCATION_AGO, FEUILLE_PRESENCE,
            STATUTS_SARL_DIRECTEUR, STATUTS_SARL_AU_DIRECTEUR,
            ACTE_NOMINATION_GERANT_DIRECTEUR, ANNONCE_LEGALE_DIRECTEUR,
            PV_DEFAUT_QUORUM_SARL, PV_DEFAUT_QUORUM_SARL_AU,
            PV_IRREGULARITE_CONVOCATION_SARL, PV_IRREGULARITE_CONVOCATION_SARL_AU,
            CONVOCATION_AG, FEUILLE_PRESENCE_AG);
}
