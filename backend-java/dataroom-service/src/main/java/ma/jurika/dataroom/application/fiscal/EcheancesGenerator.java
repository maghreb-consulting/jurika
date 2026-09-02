package ma.jurika.dataroom.application.fiscal;

import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceEntity;
import ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * RG-DF20 -- Generateur des 10 types d echeances DGI Maroc pour un exercice.
 *
 * Calendrier 2026/2027 :
 *  - TVA mensuelle : avant le 20 du mois suivant (CGI Art. 110-111)
 *  - TVA trimestrielle : avant fin du mois suivant le trimestre (CGI Art. 110-111)
 *  - IS acomptes : 31/03, 30/06, 30/09, 31/12 (CGI Art. 169)
 *  - IS annuelle : 3 mois apres cloture exercice = 31/03 (annee civile) (CGI Art. 20)
 *  - TP/TSC : 31 janvier (Loi 47-06 art. 13)
 *  - Etat 9421 : 28 fevrier (CGI Art. 156)
 *  - IR pro : 30 avril (CGI Art. 82)
 *
 * Alerte par defaut J-15 (RG-DF20).
 */
@Component
public class EcheancesGenerator {

    public List<AlerteEcheanceEntity> generateForExercice(ExerciceFiscalEntity ex,
                                                          boolean regimeTvaMensuel) {
        List<AlerteEcheanceEntity> list = new ArrayList<>();
        int year = ex.getAnnee();

        if (regimeTvaMensuel) {
            // TVA du mois M => avant le 20 du mois M+1
            for (int m = 1; m <= 12; m++) {
                int dueYear = (m == 12) ? year + 1 : year;
                int dueMonth = (m == 12) ? 1 : m + 1;
                list.add(echeance(ex, "TVA_MENSUELLE", LocalDate.of(dueYear, dueMonth, 20)));
            }
        } else {
            // TVA trimestrielle : avant fin mois suivant trimestre
            int[] dueMonthsAfterQuarter = {4, 7, 10, 1}; // janvier de N+1 pour T4
            for (int t = 1; t <= 4; t++) {
                int dueMonth = dueMonthsAfterQuarter[t - 1];
                int dueYear = (t == 4) ? year + 1 : year;
                LocalDate last = YearMonth.of(dueYear, dueMonth).atEndOfMonth();
                list.add(echeance(ex, "TVA_TRIMESTRIELLE", last));
            }
        }

        // IS acomptes
        list.add(echeance(ex, "IS_ACOMPTE_T1", LocalDate.of(year, 3, 31)));
        list.add(echeance(ex, "IS_ACOMPTE_T2", LocalDate.of(year, 6, 30)));
        list.add(echeance(ex, "IS_ACOMPTE_T3", LocalDate.of(year, 9, 30)));
        list.add(echeance(ex, "IS_ACOMPTE_T4", LocalDate.of(year, 12, 31)));

        // IS / TP / 9421 / IR annuelles -> annee N+1
        list.add(echeance(ex, "IS_DECLARATION_ANNUELLE", LocalDate.of(year + 1, 3, 31)));
        list.add(echeance(ex, "TP_TSC_DECLARATION",      LocalDate.of(year + 1, 1, 31)));
        list.add(echeance(ex, "ETAT_9421",                LocalDate.of(year + 1, 2, 28)));
        list.add(echeance(ex, "IR_DECLARATION_ANNUELLE", LocalDate.of(year + 1, 4, 30)));

        return list;
    }

    private AlerteEcheanceEntity echeance(ExerciceFiscalEntity ex, String type, LocalDate dueDate) {
        AlerteEcheanceEntity a = new AlerteEcheanceEntity();
        a.setWorkspaceId(ex.getWorkspaceId());
        a.setDossierId(ex.getDossierId());
        a.setExerciceFiscalId(ex.getId());
        a.setTypeEcheance(type);
        a.setDateEcheance(dueDate);
        a.setDateAlerte(dueDate.minusDays(15)); // RG-DF20 : J-15
        a.setStatut("PLANIFIEE");
        return a;
    }
}
