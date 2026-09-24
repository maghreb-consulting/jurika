package ma.jurika.workflow.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LE CONSTRUCTEUR UNIQUE DE LA CHARGE UTILE DU PARCOURS DE CRÉATION.
 *
 * <p>Une source — {@link MagasinVariables} — un constructeur — celui-ci — un
 * résolveur — {@code CreationSarlMapper}, déjà unique côté {@code ai-service}.
 *
 * <h2>Ce qu'il remplace</h2>
 *
 * <p>Deux constructeurs vivaient dans le navigateur. L'un,
 * {@code buildPayloadCreationSarl}, n'était plus appelé que par ses propres
 * tests ; l'autre, celui de {@code Step7Generation}, produisait tous les
 * documents en ayant perdu <b>29 des 54 clés</b> que le premier portait — dont
 * les dix champs de l'étape 9 et les signataires de l'étape 5. Aucun blanc n'en
 * sortait : des valeurs par défaut, plausibles et fausses, prenaient leur place.
 *
 * <h2>LA GARDE : LA CHARGE UTILE EST STRICTEMENT DÉRIVÉE</h2>
 *
 * <p><b>Elle n'est jamais un endroit où une valeur s'écrit ou se corrige.</b>
 * C'est ainsi que les 29 clés avaient disparu : la charge utile était un objet
 * mutable que chaque écran complétait à sa façon, et ce que l'un oubliait,
 * personne ne le réclamait.
 *
 * <p>Cette garde n'est pas une consigne, c'est une propriété du code :
 * {@link #construire} rend une structure <b>profondément non modifiable</b> —
 * cartes et listes imbriquées comprises. Toute tentative d'y écrire lève
 * {@link UnsupportedOperationException} à l'endroit exact de la faute, au lieu
 * de produire un document silencieusement différent.
 *
 * <p>Corollaire : <b>corriger une valeur se fait au magasin, jamais ici.</b>
 *
 * <h2>Comment les rattachements ont été établis</h2>
 *
 * <p>Aucun n'est déduit de la ressemblance des noms. Pour chaque clé, on a lu
 * <b>quelle variable le résolveur en tire déjà</b> — le
 * {@code put(v, "VAR", … X.get("clé") …)} du code en vigueur — puis confronté à
 * la définition du dictionnaire du 9 septembre. Le relevé complet est à
 * {@code output/lotC/rattachement-charge-utile.md}.
 *
 * <p>Les clés que ce relevé n'a pas pu trancher sont marquées
 * {@link #SANS_SOURCE_ETABLIE} : elles sont <b>produites avec une valeur
 * nulle</b>, jamais avec une valeur approchante. Une clé présente et vide se
 * voit au contrôle de complétude ; une clé remplie au jugé ne se voit nulle part.
 */
public final class ConstructeurChargeUtileCreation {

    private ConstructeurChargeUtileCreation() {
    }

    // ------------------------------------------------------------------
    // societe — 61 clés
    // ------------------------------------------------------------------
    private static final String[][] SOCIETE = {
            {"denomination", "DENOMINATION"},
            {"sigle", "SIGLE"},
            {"enseigne", "ENSEIGNE"},
            {"formeJuridique", "FORME_JURIDIQUE"},
            {"nationalite", "SOCIETE_NATIONALITE"},
            {"siegeSocial", "SIEGE_SOCIAL"},
            {"adresseSiege", "SIEGE_SOCIAL"},
            {"ville", "VILLE"},
            {"villeGreffe", "VILLE_GREFFE"},
            {"tribunalCompetent", "VILLE_GREFFE"},
            {"rcVille", "RC_VILLE"},
            {"rcNumero", "RC_NUMERO"},
            {"iceNumero", "ICE"},
            {"ifNumero", "IDENTIFIANT_FISCAL"},
            {"cnss", "CNSS_NUMERO"},
            {"taxeProfessionnelle", "IDENTIFIANT_TP"},
            {"taxeServicesCommunaux", "TAXE_SERVICES_COMMUNAUX_NUMERO"},
            {"certificatNegatifNumero", "CERTIFICAT_NEGATIF_NUMERO"},
            {"certificatNegatifDate", "CERTIFICAT_NEGATIF_DATE"},
            {"objetSocial", "OBJET_SOCIAL"},
            {"activiteSociete", "OBJET_SOCIAL"},
            {"capitalChiffres", "CAPITAL_CHIFFRES"},
            {"capitalSocial", "CAPITAL_CHIFFRES"},
            {"nombreParts", "NOMBRE_PARTS"},
            {"valeurNominalePart", "VALEUR_NOMINALE_PART"},
            {"valeurPart", "VALEUR_NOMINALE_PART"},
            {"modeLiberation", "MODE_LIBERATION"},
            {"depotFondsBloque", "DEPOT_FONDS_BLOQUE"},
            {"banqueDepositaire", "BANQUE_DEPOSITAIRE"},
            {"compteBancaireNumero", "COMPTE_BANCAIRE_NUMERO"},
            {"dureeSociete", "DUREE_SOCIETE"},
            {"dureeAnnees", "DUREE_SOCIETE"},
            {"dureeGerance", "DUREE_GERANCE"},
            {"gerantModeDesignation", "GERANT_MODE_DESIGNATION"},
            {"modeSignature", "MODE_SIGNATURE"},
            {"modeSignatureAdmin", "MODE_SIGNATURE_ADMIN"},
            {"signatureMandataire", "SIGNATURE_MANDATAIRE"},
            {"signaturePlafond", "SIGNATURE_PLAFOND_CHIFFRES"},
            {"signaturePlafondChiffres", "SIGNATURE_PLAFOND_CHIFFRES"},
            {"limitationPouvoirs", "LIMITATION_POUVOIRS"},
            {"mandataireNom", "MANDATAIRE_NOM"},
            {"mandataireActeDelegation", "MANDATAIRE_ACTE_DELEGATION"},
            {"commissaireApportsNom", "COMMISSAIRE_APPORTS_NOM"},
            {"commissaireComptesNom", "COMMISSAIRE_COMPTES_NOM"},
            {"dureeMandatCac", "DUREE_MANDAT_CAC"},
            {"exerciceDebut", "EXERCICE_DEBUT"},
            {"exerciceFin", "EXERCICE_FIN"},
            {"premierExerciceCloture", "PREMIER_EXERCICE_CLOTURE"},
            {"engagementsMandat", "ENGAGEMENTS_MANDAT"},
            {"articleDesignationStatuts", "ARTICLE_DESIGNATION_STATUTS"},
            {"dateActe", "DATE_ACTE"},
            {"dateSignature", "DATE_SIGNATURE"},
            {"dateConstitution", "DATE_COMMENCEMENT_EXPLOITATION"},
            {"dateDepotLegal", "DATE_DEPOT_LEGAL"},
            {"depotActesReference", "DEPOT_ACTES_REFERENCE"},
            {"heureActe", "HEURE_ACTE"},
            {"lieuSignature", "LIEU_SIGNATURE"},
            {"villeSignature", "LIEU_SIGNATURE"},
            {"nombreOriginaux", "NOMBRE_ORIGINAUX"},
            {"nbExemplaires", "NOMBRE_ORIGINAUX"},
            {"activites", null},           // cf. SANS_SOURCE_ETABLIE
    };

    // ------------------------------------------------------------------
    // formulaires — 30 clés
    // ------------------------------------------------------------------
    private static final String[][] FORMULAIRES = {
            {"directionRegionale", "DIRECTION_REGIONALE"},
            {"subdivision", "SUBDIVISION"},
            {"telephone", "TELEPHONE"},
            {"fax", "FAX"},
            {"email", "EMAIL"},
            {"enseigne", "ENSEIGNE"},
            {"domicileFiscal", "DOMICILE_FISCAL"},
            {"lieuActivite", "LIEU_ACTIVITE"},
            {"piecesProduites", "PIECES_PRODUITES"},
            {"dateDebutActivite", "DATE_COMMENCEMENT_EXPLOITATION"},
            {"dateCommencementExploitation", "DATE_COMMENCEMENT_EXPLOITATION"},
            {"regimeResultat", "DE_REGIME_RESULTAT"},
            {"activiteNature", "DE_ACTIVITE_NATURE"},
            {"tvaAssujettissement", "DE_TVA_ASSUJETTISSEMENT"},
            {"tvaFaitGenerateur", "DE_TVA_FAIT_GENERATEUR"},
            {"tvaPeriodicite", "DE_TVA_PERIODICITE"},
            {"tpObjet", "TP_OBJET"},
            {"tpObjetAutrePrecision", "TP_OBJET_AUTRE_PRECISION"},
            {"associePrincipalIf", "ASSOCIE_PRINCIPAL_IF"},
            {"associePrincipalVille", "ASSOCIE_PRINCIPAL_VILLE"},
            {"associePrincipalTel", "ASSOCIE_PRINCIPAL_TEL"},
            {"associePrincipalFax", "ASSOCIE_PRINCIPAL_FAX"},
            {"associePrincipalEmail", "ASSOCIE_PRINCIPAL_EMAIL"},
            {"brevetsMarquesReference", "BREVETS_MARQUES_REFERENCE"},
            {"capitalVariableMinimum", "CAPITAL_VARIABLE_MINIMUM"},
            {"siegePrecedentExploitant", "SIEGE_PRECEDENT_EXPLOITANT"},
            {"siegePrecedentRc", "SIEGE_PRECEDENT_RC"},
            {"succursalesMaroc", "SUCCURSALES_MAROC"},
            {"succursalesEtranger", "SUCCURSALES_ETRANGER"},
            {"succursalesPatente", "SUCCURSALES_PATENTE"},
    };

    /** {@code depot} — 6 clés, trois faits. */
    private static final String[][] DEPOT = {
            {"banque", "BANQUE_DEPOSITAIRE"},
            {"depositaireFonds", "BANQUE_DEPOSITAIRE"},
            {"numero", "COMPTE_BANCAIRE_NUMERO"},
            {"compteBancaireNumero", "COMPTE_BANCAIRE_NUMERO"},
            {"fondsBloque", "DEPOT_FONDS_BLOQUE"},
            {"depotFondsBloque", "DEPOT_FONDS_BLOQUE"},
    };

    private static final String[][] DOSSIER = {
            {"numero", "DOSSIER_NUMERO"},
            {"charge", "DOSSIER_CHARGE"},
            {"dateOuverture", "DOSSIER_DATE_OUVERTURE"},
    };

    private static final String[][] TICKET = {
            {"reference", "DOSSIER_NUMERO"},
            {"responsableNom", "DOSSIER_CHARGE"},
            {"createdAt", "DOSSIER_DATE_OUVERTURE"},
            {"dateAnnulation", "DOSSIER_DATE_ANNULATION"},
            {"statutAvantAnnulation", "ANNULATION_STATUT_ATTEINT"},
    };

    /** {@code creation} — les saisies du corpus lues par le résolveur pour DÉRIVER. */
    private static final String[][] CREATION = {
            {"bailDateEffet", "BAIL_DATE_EFFET"},
            {"bailDuree", "BAIL_DUREE"},
            {"bailLoyerChiffres", "BAIL_LOYER_CHIFFRES"},
            {"domiciliationRedevanceChiffres", "DOMICILIATION_REDEVANCE_CHIFFRES"},
    };

    // ------------------------------------------------------------------
    // Boucles
    // ------------------------------------------------------------------

    /** {@code gerants[]} — 25 clés. La boucle {@code DIRIGEANTS_PM} complète la personne morale. */
    private static final String[][] GERANT = {
            {"civilite", "GERANT_CIVILITE"},
            {"nom", "GERANT_NOM"},
            {"prenom", "GERANT_PRENOM"},
            {"adresse", "GERANT_ADRESSE"},
            {"nationalite", "GERANT_NATIONALITE"},
            {"dateNaissance", "GERANT_DATE_NAISSANCE"},
            {"naissance", "GERANT_DATE_NAISSANCE"},
            {"lieuNaissance", "GERANT_LIEU_NAISSANCE"},
            {"pieceType", "GERANT_PIECE_TYPE"},
            {"pieceNumero", "GERANT_PIECE_NUMERO"},
            {"cin", "GERANT_PIECE_NUMERO"},
            {"cinNumero", "GERANT_PIECE_NUMERO"},
            {"denomination", "DIRIGEANT_PM_DENOMINATION"},
            {"forme", "DIRIGEANT_PM_FORME"},
            {"formeJuridique", "DIRIGEANT_PM_FORME"},
            {"objetSocial", "DIRIGEANT_PM_OBJET"},
            {"rc", "DIRIGEANT_PM_RC"},
            {"rcNumero", "DIRIGEANT_PM_RC"},
            {"siege", "DIRIGEANT_PM_SIEGE"},
            {"representantLegal", "DIRIGEANT_PM_REPRESENTANT"},
    };

    /** {@code associes[]} — 48 clés, réparties sur quatre boucles du corpus. */
    private static final String[][] ASSOCIE = {
            {"civilite", "ASSOCIE_CIVILITE"},
            {"nom", "ASSOCIE_NOM"},
            {"prenom", "ASSOCIE_PRENOM"},
            {"ASSOCIE_NOM", "ASSOCIE_NOM"},
            {"ASSOCIE_PRENOM", "ASSOCIE_PRENOM"},
            {"ASSOCIE_NOMBRE_PARTS", "ASSOCIE_NOMBRE_PARTS"},
            {"adresse", "ASSOCIE_ADRESSE"},
            {"nationalite", "ASSOCIE_NATIONALITE"},
            {"dateNaissance", "ASSOCIE_DATE_NAISSANCE"},
            {"naissance", "ASSOCIE_DATE_NAISSANCE"},
            {"lieuNaissance", "ASSOCIE_LIEU_NAISSANCE"},
            {"pieceType", "ASSOCIE_PIECE_TYPE"},
            {"pieceNumero", "ASSOCIE_PIECE_NUMERO"},
            {"cin", "ASSOCIE_PIECE_NUMERO"},
            {"cinNumero", "ASSOCIE_PIECE_NUMERO"},
            {"typePersonne", "ASSOCIE_TYPE"},
            {"denomination", "ASSOCIE_DENOMINATION"},
            {"forme", "ASSOCIE_FORME"},
            {"formeJuridique", "ASSOCIE_FORME"},
            {"capital", "ASSOCIE_CAPITAL"},
            {"capitalEntite", "ASSOCIE_CAPITAL"},
            {"siege", "ASSOCIE_SIEGE"},
            {"rc", "ASSOCIE_RC_NUMERO"},
            {"rcNumero", "ASSOCIE_RC_NUMERO"},
            {"rcVille", "ASSOCIE_RC_VILLE"},
            {"villeGreffe", "ASSOCIE_RC_VILLE"},
            {"representantNom", "ASSOCIE_REPRESENTANT_NOM"},
            {"representantLegal", "ASSOCIE_REPRESENTANT_NOM"},
            {"representantQualite", "ASSOCIE_REPRESENTANT_QUALITE"},
            {"repQualite", "ASSOCIE_REPRESENTANT_QUALITE"},
            {"estGerant", "ASSOCIE_EST_GERANT"},
            {"isGerant", "ASSOCIE_EST_GERANT"},
            {"nombreParts", "ASSOCIE_NOMBRE_PARTS"},
            {"parts", "ASSOCIE_NOMBRE_PARTS"},
            {"partsChiffres", "ASSOCIE_NOMBRE_PARTS"},
            // boucle APPORTS_PAR_ASSOCIE, au même rang
            {"apportType", "APPORT_TYPE"},
            {"typeApport", "APPORT_TYPE"},
            {"apportNumeraire", "APPORT_NUMERAIRE_CHIFFRES"},
            {"apportNumeraireChiffres", "APPORT_NUMERAIRE_CHIFFRES"},
            {"apportNature", "APPORT_NATURE_VALEUR_CHIFFRES"},
            {"apportNatureValeur", "APPORT_NATURE_VALEUR_CHIFFRES"},
            {"apportNatureValeurChiffres", "APPORT_NATURE_VALEUR_CHIFFRES"},
            {"apportNatureDescription", "APPORT_NATURE_DESCRIPTION"},
            {"apportIndustrieDescription", "APPORT_INDUSTRIE_DESCRIPTION"},
            // boucle SOUSCRIPTIONS, au même rang
            {"montantSouscrit", "SOUSCRIPTEUR_MONTANT_SOUSCRIT"},
            {"montantVerse", "SOUSCRIPTEUR_MONTANT_VERSE"},
            {"montantLibere", "SOUSCRIPTEUR_MONTANT_VERSE"},
            {"montantApport", null},       // cf. SANS_SOURCE_ETABLIE
    };

    private static final String[][] SIGNATAIRE = {
            {"nom", "SIGNATAIRE_NOM"},
            {"qualite", "SIGNATAIRE_QUALITE"},
    };

    private static final String[][] DEMARCHE = {
            {"libelle", "DEMARCHE_DESIGNATION"},
            {"organisme", "DEMARCHE_ADMINISTRATION"},
            {"reference", "DEMARCHE_REFERENCE"},
            {"cocheAt", "DEMARCHE_DATE_DEPOT"},
    };

    /** Boucles du corpus dont les occurrences alimentent {@code associes[]} au même rang. */
    private static final List<String> BOUCLES_ASSOCIE =
            List.of("ASSOCIES", "APPORTS_PAR_ASSOCIE", "SOUSCRIPTIONS");
    /** Boucles du corpus dont les occurrences alimentent {@code gerants[]} au même rang. */
    private static final List<String> BOUCLES_GERANT = List.of("GERANTS", "DIRIGEANTS_PM");

    /**
     * Clés du contrat qu'aucune variable du magasin n'alimente, faute de
     * correspondance établie.
     *
     * <p>Elles sont <b>produites nulles</b>, jamais remplies au nom le plus
     * proche. Le relevé les porte, et le cabinet peut les trancher.
     *
     * <ul>
     *   <li>{@code societe.activites} — liste ou chaîne ? le résolveur accepte
     *       les deux, et le dictionnaire ne tranche pas.</li>
     *   <li>{@code associes[].montantApport} — calculé par le résolveur depuis
     *       les parts et la valeur nominale ; aucune variable ne le porte.</li>
     *   <li>{@code piecesManquantes} — liste produite par le contrôle de
     *       complétude, pas par une saisie.</li>
     * </ul>
     */
    public static final List<String> SANS_SOURCE_ETABLIE =
            List.of("societe.activites", "associes[].montantApport", "piecesManquantes");

    // ------------------------------------------------------------------

    /**
     * Construit la charge utile à partir des <b>seules</b> variables du magasin.
     *
     * @param variables sortie de {@link MagasinVariables#lirePourGeneration} :
     *                  {@code NOM -> valeur} pour les variables simples,
     *                  {@code BOUCLE -> [ {NOM -> valeur}, … ]} pour les boucles.
     * @return la charge utile, <b>profondément non modifiable</b>.
     */
    public static Map<String, Object> construire(Map<String, Object> variables) {
        Map<String, Object> v = variables == null ? Map.of() : variables;
        Map<String, Object> charge = new LinkedHashMap<>();

        charge.put("societe", bloc(v, SOCIETE));
        charge.put("formulaires", bloc(v, FORMULAIRES));
        charge.put("depot", bloc(v, DEPOT));
        charge.put("dossier", bloc(v, DOSSIER));
        charge.put("ticket", bloc(v, TICKET));
        charge.put("creation", bloc(v, CREATION));

        List<Map<String, Object>> gerants = boucle(v, BOUCLES_GERANT, GERANT, true);
        List<Map<String, Object>> associes = boucle(v, BOUCLES_ASSOCIE, ASSOCIE, false);

        charge.put("gerants", gerants);
        charge.put("associes", associes);
        charge.put("signataires", boucle(v, List.of("SIGNATAIRES"), SIGNATAIRE, false));
        charge.put("demarchesInterrompues", boucle(v, List.of("DEMARCHES_INTERROMPUES"), DEMARCHE, false));
        charge.put("etablissements", occurrences(v, "ETABLISSEMENTS"));

        // Le premier gérant sert la branche « gérant unique » du résolveur ;
        // l'associé unique, celle de la SARL AU. Aucun des deux n'est une
        // donnée de plus : ce sont des vues sur les boucles.
        charge.put("gerant", gerants.isEmpty() ? null : gerants.get(0));
        charge.put("associeUnique",
                estOui(v.get("ASSOCIE_UNIQUE")) && !associes.isEmpty() ? associes.get(0) : null);

        charge.put("valeurNominalePart", v.get("VALEUR_NOMINALE_PART"));
        charge.put("piecesManquantes", List.of());   // cf. SANS_SOURCE_ETABLIE

        return figer(charge);
    }

    // ------------------------------------------------------------------

    /** Un bloc-objet : chaque clé du contrat est PRODUITE, nulle si le magasin est muet. */
    private static Map<String, Object> bloc(Map<String, Object> v, String[][] table) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String[] paire : table) {
            out.put(paire[0], paire[1] == null ? null : v.get(paire[1]));
        }
        return out;
    }

    /**
     * Une boucle de la charge utile, composée d'une ou plusieurs boucles du
     * magasin lues <b>au même rang</b>.
     *
     * <p>Le corpus éclate une même personne sur plusieurs boucles — un associé
     * vit dans {@code ASSOCIES}, ses apports dans {@code APPORTS_PAR_ASSOCIE},
     * sa souscription dans {@code SOUSCRIPTIONS}. Le résolveur, lui, attend un
     * seul objet par personne. Le rang fait le lien, et rien d'autre.
     *
     * @param typeDerive ajoute {@code typePersonne} / {@code isStatutaire} /
     *                   {@code statutaire} / {@code designationMode} /
     *                   {@code dureeMandat} aux gérants — dérivés, jamais saisis.
     */
    private static List<Map<String, Object>> boucle(Map<String, Object> v, List<String> sources,
                                                     String[][] table, boolean typeDerive) {
        int taille = 0;
        for (String source : sources) taille = Math.max(taille, occurrences(v, source).size());

        List<Map<String, Object>> out = new ArrayList<>(taille);
        for (int rang = 0; rang < taille; rang++) {
            Map<String, Object> fusion = new LinkedHashMap<>();
            for (String source : sources) {
                List<Map<String, Object>> occ = occurrences(v, source);
                if (rang < occ.size()) fusion.putAll(occ.get(rang));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            for (String[] paire : table) {
                item.put(paire[0], paire[1] == null ? null : fusion.get(paire[1]));
            }
            if (typeDerive) {
                // Une personne morale se reconnaît à sa dénomination — le corpus
                // n'a pas de variable « type du gérant ».
                boolean morale = fusion.get("DIRIGEANT_PM_DENOMINATION") != null;
                item.put("typePersonne", morale ? "MORALE" : "PHYSIQUE");
                Object mode = v.get("GERANT_MODE_DESIGNATION");
                item.put("designationMode", mode);
                item.put("isStatutaire", estStatutaire(mode));
                item.put("statutaire", estStatutaire(mode));
                item.put("dureeMandat", v.get("DUREE_GERANCE"));
            }
            out.add(item);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> occurrences(Map<String, Object> v, String boucle) {
        Object o = v.get(boucle);
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    private static boolean estOui(Object valeur) {
        return valeur != null && "oui".equalsIgnoreCase(String.valueOf(valeur).trim());
    }

    private static Boolean estStatutaire(Object mode) {
        if (mode == null) return null;
        return String.valueOf(mode).trim().toLowerCase().startsWith("statut");
    }

    // ------------------------------------------------------------------
    // La garde
    // ------------------------------------------------------------------

    /**
     * Rend la structure profondément non modifiable.
     *
     * <p>{@link Collections#unmodifiableMap} ne protège que le premier niveau :
     * {@code charge.get("societe").put(…)} passerait. On descend donc dans les
     * cartes et les listes imbriquées — c'est précisément à ce niveau-là que les
     * écrans complétaient la charge utile chacun à sa façon.
     */
    private static Object figerValeur(Object valeur) {
        if (valeur instanceof Map<?, ?> m) {
            Map<String, Object> copie = new LinkedHashMap<>();
            m.forEach((k, val) -> copie.put(String.valueOf(k), figerValeur(val)));
            return Collections.unmodifiableMap(copie);
        }
        if (valeur instanceof List<?> l) {
            List<Object> copie = new ArrayList<>(l.size());
            for (Object o : l) copie.add(figerValeur(o));
            return Collections.unmodifiableList(copie);
        }
        return valeur;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> figer(Map<String, Object> charge) {
        return (Map<String, Object>) figerValeur(charge);
    }
}
