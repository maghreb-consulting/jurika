package ma.jurika.common.billing;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Lot L7 (E5), RG-PLN-01 : le quota de dossiers s'entend PAR MOIS D'ABONNEMENT, quel que soit le
 * rythme de paiement ; le compteur repart a zero au debut de chaque mois.
 *
 * <p>Le mois d'abonnement commence chaque mois au jour anniversaire de la date de debut de
 * l'abonnement (heure de Casablanca). Jour anniversaire absent du mois (29, 30, 31) : dernier jour
 * du mois. Sans date de debut connue (workspace sans abonnement enregistre), le mois est le mois
 * civil : la decision est tracee au rapport du lot (Decisions a revoir).
 *
 * @param debut premier jour du mois d'abonnement (inclus)
 * @param fin   premier jour du mois suivant (exclu)
 */
public record PeriodeAbonnement(LocalDate debut, LocalDate fin) {

    public static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    /** Mois d'abonnement contenant {@code jour}, pour un abonnement commence le {@code ancre}. */
    public static PeriodeAbonnement contenant(LocalDate ancre, LocalDate jour) {
        if (ancre == null) {
            YearMonth m = YearMonth.from(jour);
            return new PeriodeAbonnement(m.atDay(1), m.plusMonths(1).atDay(1));
        }
        if (jour.isBefore(ancre)) {
            // Abonnement pas encore commence : premier mois.
            return new PeriodeAbonnement(ancre, echeance(ancre, 1));
        }
        long mois = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(ancre), YearMonth.from(jour));
        LocalDate debut = echeance(ancre, mois);
        if (debut.isAfter(jour)) {
            mois -= 1;
            debut = echeance(ancre, mois);
        }
        return new PeriodeAbonnement(debut, echeance(ancre, mois + 1));
    }

    /** Mois d'abonnement courant (heure de Casablanca). */
    public static PeriodeAbonnement courante(LocalDate ancre, Instant maintenant) {
        return contenant(ancre, LocalDate.ofInstant(maintenant, CASABLANCA));
    }

    /** Jour anniversaire du {@code n}-ieme mois apres {@code ancre} (borne au dernier jour du mois). */
    public static LocalDate echeance(LocalDate ancre, long n) {
        YearMonth m = YearMonth.from(ancre).plusMonths(n);
        return m.atDay(Math.min(ancre.getDayOfMonth(), m.lengthOfMonth()));
    }

    /** Debut du mois, en instant (minuit, Casablanca). */
    public Instant debutInstant() {
        return debut.atStartOfDay(CASABLANCA).toInstant();
    }

    /** Fin du mois (exclue), en instant (minuit, Casablanca). */
    public Instant finInstant() {
        return fin.atStartOfDay(CASABLANCA).toInstant();
    }

    /** Cle de la periode pour les registres d'alertes (annee-mois du debut, ex. 2026-10). */
    public String cle() {
        return YearMonth.from(debut).toString();
    }
}
