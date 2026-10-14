package ma.jurika.billing.application.contrat;

/**
 * Lot L7 (E6) : identite du cabinet et du signataire, lue chez auth-service (proprietaire des
 * tables {@code workspaces} et {@code users}) avec le jeton du superviseur qui accepte. Aucune
 * valeur de repli : un champ absent reste nul et le contrat le nomme.
 */
public interface IdentiteCabinetSource {

    record Identite(String denomination, String adresse, String rcNumero, String ice, String email,
                    String signataireNom, String signatairePrenom, String signataireCompte) {}

    /**
     * @param jetonPorteur valeur complete de l'en-tete Authorization ("Bearer ...") de la requete
     *                     du superviseur.
     */
    Identite lire(String jetonPorteur);
}
