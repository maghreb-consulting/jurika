package ma.jurika.ai.rag;

import ma.jurika.ai.rag.ConversationsChatbotService.Proprietaire;
import ma.jurika.common.exception.PlanLimitException;
import ma.jurika.common.trial.TrialAccessChecker;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L7 (E4), RG-ESS-04 : 20 questions au chatbot pendant l'essai, compteur
 * atomique (ai V18) sous le role d'execution jurika_app (RLS). La 21e question
 * est refusee avant d'atteindre le modele ; un cabinet abonne n'est pas limite.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuestionsEssaiChatbotIT {

    private static final UUID WS_ESSAI = UUID.fromString("e5a10000-0000-0000-0000-0000000000c1");
    private static final UUID WS_ABONNE = UUID.fromString("e5a10000-0000-0000-0000-0000000000c2");

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("jurika_it_essai_chatbot")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("rag-commun-it-init.sql");

    private JdbcTemplate owner;
    private JdbcTemplate app;

    @BeforeAll
    void demarrer() {
        pg.start();
        for (String[] m : new String[][]{
                {"filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth"},
                {"classpath:db/migration", "flyway_history_ai"}}) {
            Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                    .locations(m[0]).table(m[1]).baselineOnMigrate(true).baselineVersion("0").load().migrate();
        }
        owner = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        app = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), "jurika_app", "jurika_app_it"));
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    @SuppressWarnings("unchecked")
    private QuestionsEssaiChatbot compteur() {
        TrialAccessChecker checker = ws -> Optional.of(new TrialAccessChecker.TrialState(ws,
                WS_ESSAI.equals(ws) ? "TRIAL_ACTIVE" : "CONVERTED", 3, Instant.now(), null));
        ObjectProvider<TrialAccessChecker> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(checker);
        return new QuestionsEssaiChatbot(app, p);
    }

    @Test
    void vingt_questions_puis_refus_avant_le_modele() {
        AtomicInteger appelsModele = new AtomicInteger();
        RagService rag = new RagService(new CorpusRetriever(owner), false, 5) {
            @Override
            public java.util.Map<String, Object> ask(String question) {
                appelsModele.incrementAndGet();
                return java.util.Map.of("answer", "R", "sources", java.util.List.of(), "mode", "test");
            }
        };
        ConversationsChatbotService service = new ConversationsChatbotService(app, rag);
        service.setQuestionsEssai(compteur());
        Proprietaire p = new Proprietaire(WS_ESSAI, UUID.randomUUID());
        for (int i = 1; i <= 20; i++) {
            service.creer(p, "Question de cours numero " + i + " sur la cession de parts ?");
        }
        assertThat(appelsModele).hasValue(20);
        assertThatThrownBy(() -> service.creer(p, "Vingt et unieme question sur les parts sociales ?"))
                .isInstanceOf(PlanLimitException.class)
                .hasMessageContaining("20 questions");
        assertThat(appelsModele).hasValue(20);
        assertThat(owner.queryForObject("SELECT nombre FROM chatbot_questions_essai WHERE workspace_id = ?",
                Integer.class, WS_ESSAI)).isEqualTo(20);
    }

    @Test
    void cabinet_abonne_non_limite_et_aucun_compteur() {
        QuestionsEssaiChatbot c = compteur();
        for (int i = 0; i < 25; i++) {
            c.consommer(WS_ABONNE);
        }
        assertThat(owner.queryForObject("SELECT COUNT(*) FROM chatbot_questions_essai WHERE workspace_id = ?",
                Integer.class, WS_ABONNE)).isZero();
    }
}
