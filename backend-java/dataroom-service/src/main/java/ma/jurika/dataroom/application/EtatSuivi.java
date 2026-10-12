package ma.jurika.dataroom.application;

/**
 * Lot L5 (E9, RG-COM-07) : les demandes des clients et les requetes du cabinet
 * sont suivies par etat -- non traitee, en cours, traitee.
 *
 * <p>Les demandes du client portent deja ces trois etats. Les requetes du
 * cabinet au client gardent leur machine a etats detaillee (dataroom V22 :
 * OUVERTE, REPONDUE, A_COMPLETER, CLOTUREE), qui commande les gestes du client
 * et de l'employe ; elle n'est ni migree ni appauvrie. L'ecran affiche l'etat de
 * suivi, calcule ICI par une correspondance explicite :
 *
 * <table>
 *   <caption>Requete du cabinet : statut detaille vers etat de suivi</caption>
 *   <tr><th>Statut</th><th>Etat</th><th>Raison</th></tr>
 *   <tr><td>OUVERTE</td><td>NON_TRAITEE</td><td>le client n'a pas encore repondu</td></tr>
 *   <tr><td>REPONDUE</td><td>EN_COURS</td><td>reponse recue, a verifier par le cabinet</td></tr>
 *   <tr><td>A_COMPLETER</td><td>EN_COURS</td><td>complement demande au client</td></tr>
 *   <tr><td>CLOTUREE</td><td>TRAITEE</td><td>le cabinet a valide la reponse</td></tr>
 * </table>
 *
 * <p>Aucune reprise de donnees n'est necessaire : aucune migration (dataroom V38-V39
 * reservees, inutilisees ici). Un statut inconnu ne recoit AUCUN etat (null) :
 * pas de valeur de repli qui masquerait une donnee hors du referentiel.
 */
public enum EtatSuivi {
    NON_TRAITEE, EN_COURS, TRAITEE;

    public static final String CLIENT_TO_EMPLOYE = "CLIENT_TO_EMPLOYE";
    public static final String EMPLOYE_TO_CLIENT = "EMPLOYE_TO_CLIENT";

    /** Etat de suivi d'une demande ou d'une requete ; null si le statut est inconnu. */
    public static EtatSuivi of(String direction, String statut) {
        if (statut == null) return null;
        if (EMPLOYE_TO_CLIENT.equals(direction)) {
            return switch (statut) {
                case "OUVERTE" -> NON_TRAITEE;
                case "REPONDUE", "A_COMPLETER" -> EN_COURS;
                case "CLOTUREE" -> TRAITEE;
                default -> null;
            };
        }
        return switch (statut) {
            case "NON_TRAITEE" -> NON_TRAITEE;
            case "EN_COURS" -> EN_COURS;
            case "TRAITEE" -> TRAITEE;
            default -> null;
        };
    }
}
