package ma.jurika.ai.rag;

import ma.jurika.common.trial.EssaiQuotas;
import ma.jurika.common.trial.EssaiRegles;
import ma.jurika.common.trial.TrialAccessChecker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Lot L7 (E4), RG-ESS-04 : 20 questions au chatbot pendant l'essai gratuit.
 *
 * <p>{@link #consommer(UUID)} est appele APRES le filtre des donnees
 * personnelles et AVANT l'appel au modele : la question est comptee, ou
 * refusee (HTTP 402, message clair) si les 20 sont deja posees. Increment
 * atomique (ai V18). Hors essai : aucun effet.
 */
@Component
public class QuestionsEssaiChatbot {

    private final JdbcTemplate jdbc;
    private final ObjectProvider<TrialAccessChecker> checker;

    public QuestionsEssaiChatbot(JdbcTemplate jdbc, ObjectProvider<TrialAccessChecker> checker) {
        this.jdbc = jdbc;
        this.checker = checker;
    }

    public void consommer(UUID workspaceId) {
        TrialAccessChecker c = checker.getIfAvailable();
        if (c == null || workspaceId == null) return;
        boolean enEssai = c.getState(workspaceId).map(TrialAccessChecker.TrialState::enEssai).orElse(false);
        if (!enEssai) return;
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        List<Integer> compte = tx.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.current_workspace_id', ?, true)",
                    String.class, workspaceId.toString());
            return jdbc.queryForList("""
                    INSERT INTO chatbot_questions_essai (workspace_id, nombre) VALUES (?, 1)
                    ON CONFLICT (workspace_id) DO UPDATE
                        SET nombre = chatbot_questions_essai.nombre + 1, mis_a_jour_le = now()
                        WHERE chatbot_questions_essai.nombre < ?
                    RETURNING nombre
                    """, Integer.class, workspaceId, EssaiRegles.MAX_QUESTIONS_CHATBOT);
        });
        if (compte == null || compte.isEmpty()) {
            throw EssaiQuotas.limiteQuestionsAtteinte();
        }
    }
}
