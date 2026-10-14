package ma.jurika.common.trial;

/**
 * Lot L7 (E4) : regles chiffrees de l'essai gratuit, CDC 4.2 (source unique
 * pour tous les services).
 *
 * <ul>
 *   <li>RG-SOU-02 : 7 jours, sans moyen de paiement ;</li>
 *   <li>RG-ESS-04 : 3 dossiers, 1 superviseur et 1 employe, 500 Mo,
 *       20 questions au chatbot ;</li>
 *   <li>RG-ESS-07 : fonctions du forfait Professionnel ;</li>
 *   <li>RG-ESS-10 : lecture seule pendant 30 jours, puis suppression.</li>
 * </ul>
 */
public final class EssaiRegles {

    private EssaiRegles() {}

    /** RG-SOU-02. */
    public static final int DUREE_JOURS = 7;
    /** RG-ESS-10. */
    public static final int LECTURE_SEULE_JOURS = 30;
    /** RG-ESS-08 : rappels affiches a J-3 et a J-1. */
    public static final int RAPPEL_PREMIER_JOURS = 3;
    public static final int RAPPEL_DERNIER_JOURS = 1;

    /** RG-ESS-04. */
    public static final int MAX_DOSSIERS = 3;
    public static final int MAX_SUPERVISEURS = 1;
    public static final int MAX_EMPLOYES = 1;
    /** 500 Mo (en octets, Mo = 1024 x 1024 octets, comme les Go des forfaits). */
    public static final long MAX_STOCKAGE_OCTETS = 500L * 1024L * 1024L;
    public static final int MAX_QUESTIONS_CHATBOT = 20;

    /** RG-ESS-07 : code du forfait dont l'essai donne les fonctions. */
    public static final String FORFAIT_ESSAI = "PROFESSIONNEL";

    /** RG-ESS-06 : mention portee en filigrane par tout acte affiche pendant l'essai. */
    public static final String MENTION_FILIGRANE =
            "VERSION D'\u00c9VALUATION \u2014 SANS VALEUR JURIDIQUE";

    public static final String MESSAGE_TELECHARGEMENT_INTERDIT =
            "Aucun t\u00e9l\u00e9chargement n\u2019est permis pendant l\u2019essai gratuit : les documents se "
                    + "consultent dans la plateforme, en aper\u00e7u. Souscrivez un forfait pour les t\u00e9l\u00e9charger.";

    public static final String MESSAGE_LECTURE_SEULE =
            "Votre essai gratuit est termin\u00e9 : le cabinet est en lecture seule. Souscrivez un forfait "
                    + "pour reprendre votre travail, que vous conservez int\u00e9gralement.";

    public static final String MESSAGE_VERIFICATION_IMPOSSIBLE =
            "La v\u00e9rification de l\u2019abonnement du cabinet est momentan\u00e9ment impossible : "
                    + "r\u00e9essayez dans quelques instants.";
}
