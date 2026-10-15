package ma.jurika.billing.application.contrat;

import java.time.Instant;
import java.util.Optional;

/**
 * Lot L7 (E13) : acceptation des conditions d'utilisation de l'essai gratuit (piece D,
 * RG-CTR-10) du cabinet, lue chez auth-service (proprietaire de {@code essai_conditions_acceptations})
 * avec le jeton du superviseur, pour la mention de l'exemplaire du contrat d'abonnement.
 */
public interface EssaiCabinetSource {

    /** Version des conditions de l'essai et instant de leur acceptation. */
    record AcceptationEssai(String version, Instant accepteesLe) {}

    /**
     * @param jetonPorteur valeur complete de l'en-tete Authorization ("Bearer ...") du superviseur.
     * @return vide si le cabinet n'a pas eu d'essai (reponse explicite d'auth) ; un echec (auth
     *         injoignable, reponse illisible ou incomplete) est un refus nomme, jamais un vide.
     */
    Optional<AcceptationEssai> lire(String jetonPorteur);
}
