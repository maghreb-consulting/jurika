package ma.jurika.dataroom.integration;

import ma.jurika.dataroom.application.fiscal.EcheancesGenerator;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceEntity;
import ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 14 ter C1 -- EcheancesGenerationIT (8 cas).
 *
 * Tests purement deterministes de {@link EcheancesGenerator} : aucune dependance
 * Spring ni DB requise. Couvre RG-DF20 (generation 10 types d'echeances DGI,
 * alertes J-15) et la coherence du calendrier 2026/2027 (TVA, IS acomptes,
 * declarations annuelles, TP/TSC, 9421, IR).
 *
 * Cas plan original (audit) :
 *   1. TVA mensuel  -> 12 TVA + 4 IS + 4 annuelles = 20 echeances
 *   2. TVA trim     -> 4 TVA + 4 IS + 4 annuelles = 12 echeances
 *   3. IS acomptes  -> mars/juin/sept/dec
 *   4. IR salaires mensuel (RAS_HONORAIRES) -- pas encore couvert par le generateur
 *   5. CNSS         -- pas encore couvert par le generateur
 *   6. Changement regime TVA mid-year -> recalcul futures uniquement -- non implemente
 *   7. Dossier dissous -- non implemente par le generator (responsabilite caller)
 *   8. Exercice CLOTURE -- non implemente par le generator (responsabilite caller)
 *
 * Pour les cas 4-8 hors-perimetre code actuel : on documente le comportement
 * factuel via assertions de coherence (J-15 systematique, statut PLANIFIEE,
 * workspace/dossier/exercice IDs propages).
 */
class EcheancesGenerationIT {

    private final EcheancesGenerator generator = new EcheancesGenerator();

    private UUID workspaceId;
    private UUID dossierId;
    private ExerciceFiscalEntity exercice2027;

    @BeforeEach
    void setup() {
        workspaceId = UUID.randomUUID();
        dossierId = UUID.randomUUID();
        exercice2027 = new ExerciceFiscalEntity();
        exercice2027.setId(UUID.randomUUID());
        exercice2027.setWorkspaceId(workspaceId);
        exercice2027.setDossierId(dossierId);
        exercice2027.setAnnee((short) 2027);
        exercice2027.setDateDebut(LocalDate.of(2027, 1, 1));
        exercice2027.setDateFin(LocalDate.of(2027, 12, 31));
        exercice2027.setStatut("OUVERT");
    }

    @Test
    @DisplayName("RG-DF20 cas 1 : TVA mensuel -> 12 TVA + 4 IS acomptes + 4 declarations annuelles = 20 echeances")
    void tvaMensuelGenerates20Echeances() {
        List<AlerteEcheanceEntity> ech = generator.generateForExercice(exercice2027, true);

        assertThat(ech).hasSize(20);
        long tva = ech.stream().filter(a -> "TVA_MENSUELLE".equals(a.getTypeEcheance())).count();
        long isAcomptes = ech.stream()
                .filter(a -> a.getTypeEcheance() != null && a.getTypeEcheance().startsWith("IS_ACOMPTE_"))
                .count();
        long annuelles = ech.stream()
                .map(AlerteEcheanceEntity::getTypeEcheance)
                .filter(t -> Set.of("IS_DECLARATION_ANNUELLE", "TP_TSC_DECLARATION",
                        "ETAT_9421", "IR_DECLARATION_ANNUELLE").contains(t))
                .count();
        assertThat(tva).isEqualTo(12);
        assertThat(isAcomptes).isEqualTo(4);
        assertThat(annuelles).isEqualTo(4);
    }

    @Test
    @DisplayName("RG-DF20 cas 2 : TVA trimestrielle -> 4 TVA + 4 IS acomptes + 4 declarations annuelles = 12 echeances")
    void tvaTrimestrielleGenerates12Echeances() {
        List<AlerteEcheanceEntity> ech = generator.generateForExercice(exercice2027, false);

        assertThat(ech).hasSize(12);
        long tva = ech.stream().filter(a -> "TVA_TRIMESTRIELLE".equals(a.getTypeEcheance())).count();
        assertThat(tva).isEqualTo(4);
    }

    @Test
    @DisplayName("RG-DF20 cas 3 : IS acomptes -> 31/03, 30/06, 30/09, 31/12 (CGI Art. 169)")
    void isAcomptesScheduleIsCorrectQuarterlyDates() {
        List<AlerteEcheanceEntity> ech = generator.generateForExercice(exercice2027, true);

        var isAcomptesDates = ech.stream()
                .filter(a -> a.getTypeEcheance() != null && a.getTypeEcheance().startsWith("IS_ACOMPTE_"))
                .collect(Collectors.toMap(AlerteEcheanceEntity::getTypeEcheance,
                        AlerteEcheanceEntity::getDateEcheance));
        assertThat(isAcomptesDates).containsEntry("IS_ACOMPTE_T1", LocalDate.of(2027, 3, 31));
        assertThat(isAcomptesDates).containsEntry("IS_ACOMPTE_T2", LocalDate.of(2027, 6, 30));
        assertThat(isAcomptesDates).containsEntry("IS_ACOMPTE_T3", LocalDate.of(2027, 9, 30));
        assertThat(isAcomptesDates).containsEntry("IS_ACOMPTE_T4", LocalDate.of(2027, 12, 31));
    }

    @Test
    @DisplayName("RG-DF20 cas 4 : declarations annuelles -> annee N+1 (IS=31/03, TP/TSC=31/01, 9421=28/02, IR=30/04)")
    void declarationsAnnuellesAreScheduledNextYear() {
        List<AlerteEcheanceEntity> ech = generator.generateForExercice(exercice2027, true);

        var annuelles = ech.stream()
                .filter(a -> Set.of("IS_DECLARATION_ANNUELLE", "TP_TSC_DECLARATION",
                        "ETAT_9421", "IR_DECLARATION_ANNUELLE").contains(a.getTypeEcheance()))
                .collect(Collectors.toMap(AlerteEcheanceEntity::getTypeEcheance,
                        AlerteEcheanceEntity::getDateEcheance));
        assertThat(annuelles)
                .containsEntry("IS_DECLARATION_ANNUELLE", LocalDate.of(2028, 3, 31))
                .containsEntry("TP_TSC_DECLARATION", LocalDate.of(2028, 1, 31))
                .containsEntry("ETAT_9421", LocalDate.of(2028, 2, 28))
                .containsEntry("IR_DECLARATION_ANNUELLE", LocalDate.of(2028, 4, 30));
    }

    @Test
    @DisplayName("RG-DF20 cas 5 : TVA mensuelle -> date echeance = 20 du mois suivant ; bord mois 12 bascule annee+1")
    void tvaMensuelleDueDateIs20OfNextMonthAndRollsOverDecember() {
        List<AlerteEcheanceEntity> ech = generator.generateForExercice(exercice2027, true);

        List<LocalDate> tvaDates = ech.stream()
                .filter(a -> "TVA_MENSUELLE".equals(a.getTypeEcheance()))
                .map(AlerteEcheanceEntity::getDateEcheance)
                .sorted()
                .toList();
        assertThat(tvaDates).hasSize(12);
        assertThat(tvaDates.get(0)).isEqualTo(LocalDate.of(2027, 2, 20));  // janvier -> 20 fevrier
        assertThat(tvaDates.get(10)).isEqualTo(LocalDate.of(2027, 12, 20)); // novembre -> 20 decembre
        assertThat(tvaDates.get(11)).isEqualTo(LocalDate.of(2028, 1, 20));  // decembre -> 20 janvier N+1
        // toutes les dates sont le 20 du mois
        assertThat(tvaDates).allMatch(d -> d.getDayOfMonth() == 20);
    }

    @Test
    @DisplayName("RG-DF20 cas 6 : TVA trimestrielle -> fin de mois (avril, juillet, octobre, janvier N+1)")
    void tvaTrimestrielleDueDatesAreEndOfMonth() {
        List<AlerteEcheanceEntity> ech = generator.generateForExercice(exercice2027, false);

        List<LocalDate> tvaDates = ech.stream()
                .filter(a -> "TVA_TRIMESTRIELLE".equals(a.getTypeEcheance()))
                .map(AlerteEcheanceEntity::getDateEcheance)
                .sorted()
                .toList();
        assertThat(tvaDates).containsExactly(
                LocalDate.of(2027, 4, 30),  // T1 -> avril N
                LocalDate.of(2027, 7, 31),  // T2 -> juillet N
                LocalDate.of(2027, 10, 31), // T3 -> octobre N
                LocalDate.of(2028, 1, 31)   // T4 -> janvier N+1 (selon EcheancesGenerator.dueMonthsAfterQuarter)
        );
    }

    @Test
    @DisplayName("RG-DF20 cas 7 : statut PLANIFIEE + date_alerte = date_echeance - 15j (J-15) sur TOUTES les echeances")
    void allEcheancesArePlannedWithJMinus15Alert() {
        List<AlerteEcheanceEntity> ech = generator.generateForExercice(exercice2027, true);

        assertThat(ech).allSatisfy(a -> {
            assertThat(a.getStatut()).isEqualTo("PLANIFIEE");
            assertThat(a.getDateAlerte()).isEqualTo(a.getDateEcheance().minusDays(15));
        });
    }

    @Test
    @DisplayName("RG-DF20 cas 8 : workspaceId/dossierId/exerciceId propages sur TOUTES les echeances + annee bisextile 2028")
    void identifiersPropagatedAndLeapYearEdgeCase() {
        List<AlerteEcheanceEntity> echStandard = generator.generateForExercice(exercice2027, true);
        assertThat(echStandard).allSatisfy(a -> {
            assertThat(a.getWorkspaceId()).isEqualTo(workspaceId);
            assertThat(a.getDossierId()).isEqualTo(dossierId);
            assertThat(a.getExerciceFiscalId()).isEqualTo(exercice2027.getId());
        });

        // Annee bisextile 2028 : ETAT_9421 doit etre 28 fevrier (jamais 29) -- regle CGI Art. 156 reste 28/02
        // Mais 2028 lui-meme bisextile : date_alerte = 28/02 - 15j = 13/02
        ExerciceFiscalEntity ex2028 = new ExerciceFiscalEntity();
        ex2028.setId(UUID.randomUUID());
        ex2028.setWorkspaceId(workspaceId);
        ex2028.setDossierId(dossierId);
        ex2028.setAnnee((short) 2028);
        ex2028.setStatut("OUVERT");

        List<AlerteEcheanceEntity> ech2028 = generator.generateForExercice(ex2028, false);
        var etat9421 = ech2028.stream()
                .filter(a -> "ETAT_9421".equals(a.getTypeEcheance()))
                .findFirst().orElseThrow();
        assertThat(etat9421.getDateEcheance()).isEqualTo(LocalDate.of(2029, 2, 28));
        assertThat(etat9421.getDateAlerte()).isEqualTo(LocalDate.of(2029, 2, 13));
    }
}
