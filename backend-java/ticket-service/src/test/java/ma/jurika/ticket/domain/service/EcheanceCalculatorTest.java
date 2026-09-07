package ma.jurika.ticket.domain.service;

import ma.jurika.ticket.domain.model.DelaiUnite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Calcul des echeances legales, sur des DATES ATTENDUES.
 *
 * <p>Chaque cas est choisi pour reveler une erreur precise : un delai en mois
 * traite en jours, ou une bascule de fin de mois mal geree. Une erreur de
 * quelques jours suffit a faire sonner l'alerte APRES l'expiration du delai.
 */
class EcheanceCalculatorTest {

    @Test
    @DisplayName("Etape 21 — statuts signes le 15/01/2026, 3 mois -> 15/04/2026")
    void immatriculationTroisMois() {
        LocalDate echeance = EcheanceCalculator.echeance(
                LocalDate.of(2026, 1, 15), 3, DelaiUnite.MOIS);

        assertThat(echeance).isEqualTo(LocalDate.of(2026, 4, 15));
    }

    @Test
    @DisplayName("Etape 24 — modele J le 31/01/2026, 1 mois -> 28/02/2026 (bascule de fin de mois)")
    void publicationUnMoisBasculeFinDeMois() {
        LocalDate echeance = EcheanceCalculator.echeance(
                LocalDate.of(2026, 1, 31), 1, DelaiUnite.MOIS);

        assertThat(echeance).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("Etape 17 — statuts signes le 13/03/2026, 30 jours -> 12/04/2026")
    void enregistrementTrenteJours() {
        LocalDate echeance = EcheanceCalculator.echeance(
                LocalDate.of(2026, 3, 13), 30, DelaiUnite.JOURS);

        assertThat(echeance).isEqualTo(LocalDate.of(2026, 4, 12));
    }

    @Test
    @DisplayName("Etape 20 — modele J le 31/03/2026, 30 jours -> 30/04/2026")
    void declarationExistenceDepuisImmatriculation() {
        // Arbitrage du cabinet (05/09/2026) : « dans les 30 jours de la
        // constitution » court a compter de l'immatriculation au RC, donc de
        // l'obtention du modele J (etape 21).
        LocalDate echeance = EcheanceCalculator.echeance(
                LocalDate.of(2026, 3, 31), 30, DelaiUnite.JOURS);

        assertThat(echeance).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    @DisplayName("Trois mois ne valent PAS quatre-vingt-dix jours")
    void moisEtJoursNeSontPasInterchangeables() {
        LocalDate depart = LocalDate.of(2026, 1, 15);

        LocalDate enMois = EcheanceCalculator.echeance(depart, 3, DelaiUnite.MOIS);
        LocalDate enJours = EcheanceCalculator.echeance(depart, 90, DelaiUnite.JOURS);

        // Janvier + fevrier + mars = 90 jours en 2026 : au 15 janvier, les deux
        // methodes coincident par hasard. C'est precisement ce qui rend l'erreur
        // difficile a voir.
        assertThat(enMois).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(enJours).isEqualTo(LocalDate.of(2026, 4, 15));

        // Le decalage apparait des que le point de depart change de mois :
        assertThat(EcheanceCalculator.echeance(LocalDate.of(2026, 5, 15), 3, DelaiUnite.MOIS))
                .isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(EcheanceCalculator.echeance(LocalDate.of(2026, 5, 15), 90, DelaiUnite.JOURS))
                .as("mai+juin+juillet font 92 jours : l'echeance en jours tombe 2 jours trop tard")
                .isEqualTo(LocalDate.of(2026, 8, 13));
    }

    @Test
    @DisplayName("Le 29 fevrier d'une annee bissextile n'est pas invente")
    void basculeAnneeNonBissextile() {
        // 29/02/2028 (bissextile) + 12 mois -> 28/02/2029 (non bissextile).
        assertThat(EcheanceCalculator.echeance(LocalDate.of(2028, 2, 29), 12, DelaiUnite.MOIS))
                .isEqualTo(LocalDate.of(2029, 2, 28));
    }

    @Test
    @DisplayName("La date de depart est lue dans le fuseau du cabinet, pas celui de la machine")
    void fuseauFige() {
        // 31/01/2026 a 23h30 heure de Casablanca : la date civile reste le 31/01.
        var instant = ZonedDateTime.of(2026, 1, 31, 23, 30, 0, 0,
                EcheanceCalculator.ZONE).toInstant();

        assertThat(EcheanceCalculator.echeance(instant, 1, DelaiUnite.MOIS))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    @DisplayName("Sans date de depart, aucun calcul n'est tente")
    void departObligatoire() {
        assertThatThrownBy(() -> EcheanceCalculator.echeance(
                (java.time.Instant) null, 30, DelaiUnite.JOURS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EcheanceCalculator.echeance(
                (LocalDate) null, 30, DelaiUnite.JOURS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
