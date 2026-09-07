package ma.jurika.ticket.domain.service;

import ma.jurika.ticket.domain.model.DelaiUnite;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Calcul d'une echeance legale a partir d'un point de depart et d'un delai.
 *
 * <p>Fonction pure, sans etat ni dependance : c'est le seul endroit ou une date
 * limite est calculee, et il est directement testable sur des valeurs de dates.
 *
 * <p><b>Les mois sont des mois calendaires.</b> {@code plusMonths} ramene au
 * dernier jour du mois cible quand le quantieme n'existe pas (31/01 + 1 mois =
 * 28/02). Une arithmetique en jours donnerait le 02/03, soit deux jours APRES
 * l'expiration reelle : l'alerte se leverait trop tard, ce qui est pire que pas
 * d'alerte du tout.
 */
public final class EcheanceCalculator {

    /**
     * Fuseau de reference pour convertir l'horodatage de cochage en date civile.
     * Le cabinet et ses administrations sont a Casablanca ; utiliser le fuseau
     * de la JVM ferait dependre une date legale de la machine qui execute le
     * calcul.
     */
    public static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");

    private EcheanceCalculator() {}

    /** Date limite, a partir d'une date de depart deja civile. */
    public static LocalDate echeance(LocalDate depart, int valeur, DelaiUnite unite) {
        if (depart == null || unite == null) {
            throw new IllegalArgumentException("Depart et unite obligatoires");
        }
        return unite == DelaiUnite.MOIS ? depart.plusMonths(valeur) : depart.plusDays(valeur);
    }

    /** Date limite, a partir de l'horodatage de cochage de l'etape de reference. */
    public static LocalDate echeance(Instant departCochage, int valeur, DelaiUnite unite) {
        if (departCochage == null) {
            throw new IllegalArgumentException("Date de cochage obligatoire");
        }
        return echeance(LocalDate.ofInstant(departCochage, ZONE), valeur, unite);
    }

    /** Date du jour dans le fuseau de reference. */
    public static LocalDate aujourdhui() {
        return LocalDate.now(ZONE);
    }
}
