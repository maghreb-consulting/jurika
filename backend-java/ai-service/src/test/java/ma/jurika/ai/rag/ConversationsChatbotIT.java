package ma.jurika.ai.rag;

import ma.jurika.ai.rag.ConversationsChatbotService.Proprietaire;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L5b E2 (RG-COM-12) : conversations du chatbot persistees par utilisateur.
 *
 * <p>Schema : les VRAIES migrations, dans l'ordre de la base partagee jurika_db (auth, puis ai
 * jusqu'a V17). Deux utilisateurs du cabinet A et un du cabinet B : chacun ne voit que ses
 * conversations (filtrage applicatif, connexion proprietaire comme ai-service aujourd'hui ;
 * puis RLS en role d'execution {@code jurika_app}). Sont aussi prouves : le titre tire de la
 * premiere question, le renommage, l'archivage (consultable, plus de question), le tri par
 * derniere activite et le refus RG-COM-04 sans aucun enregistrement. Aucun modele de langage :
 * les reponses restituent les passages du corpus commun.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConversationsChatbotIT {

    private static final UUID WS_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID WS_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");
    private static final Proprietaire A1 = new Proprietaire(WS_A, UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000e1"));
    private static final Proprietaire A2 = new Proprietaire(WS_A, UUID.fromString("aaaaaaaa-0000-0000-0000-0000000000e2"));
    private static final Proprietaire B1 = new Proprietaire(WS_B, UUID.fromString("bbbbbbbb-0000-0000-0000-0000000000e1"));

    private static final String QUESTION = "Quelle majorite pour la cession de parts sociales a un tiers ?";

    private final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("jurika_it_chatbot_conv")
            .withUsername("jurika_it")
            .withPassword("jurika_it")
            .withInitScript("rag-commun-it-init.sql");

    private JdbcTemplate owner;
    private ConversationsChatbotService service;

    @BeforeAll
    void demarrer() {
        pg.start();
        migrer("filesystem:../auth-service/src/main/resources/db/migration", "flyway_history_auth");
        migrer("classpath:db/migration", "flyway_history_ai");

        DriverManagerDataSource ds = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        owner = new JdbcTemplate(ds);
        owner.update("INSERT INTO rag_corpus_chunks (source, article, chunk_text, portee, doc_id, chunk_index) "
                        + "VALUES ('Loi 5-96', 'Article 58', ?, 'COMMUNE', ?, 0)",
                "La cession de parts sociales a des tiers requiert l'agrement de la majorite des associes "
                        + "representant au moins les trois quarts du capital.",
                UUID.randomUUID());
        service = new ConversationsChatbotService(owner, new RagService(new CorpusRetriever(owner), false, 5));
    }

    @AfterAll
    void arreter() {
        pg.stop();
    }

    @BeforeEach
    void vider() {
        owner.update("DELETE FROM chatbot_conversations");
    }

    private void migrer(String location, String table) {
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations(location)
                .table(table)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load().migrate();
    }

    @SuppressWarnings("unchecked")
    private static UUID idDe(Map<String, Object> creation) {
        return (UUID) ((Map<String, Object>) creation.get("conversation")).get("id");
    }

    private static List<Object> ids(List<Map<String, Object>> conversations) {
        return conversations.stream().map(c -> c.get("id")).toList();
    }

    private int compter(String table) {
        return owner.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static void introuvable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------------------------- propriete

    @Test
    void chacun_ne_voit_que_ses_conversations_meme_dans_le_meme_cabinet() {
        UUID convA1 = idDe(service.creer(A1, QUESTION));
        UUID convA2 = idDe(service.creer(A2, "Quel est le capital minimum d'une SARL ?"));
        UUID convB1 = idDe(service.creer(B1, "Comment ceder des parts sociales ?"));

        assertThat(ids(service.lister(A1, false))).containsExactly(convA1);
        assertThat(ids(service.lister(A2, false))).containsExactly(convA2);
        assertThat(ids(service.lister(B1, false))).containsExactly(convB1);

        // Collegue du meme cabinet, puis autre cabinet : 404 partout, rien n'est modifie.
        for (Proprietaire autre : List.of(A2, B1)) {
            introuvable(() -> service.lire(autre, convA1));
            introuvable(() -> service.poser(autre, convA1, "Et pour une SA ?"));
            introuvable(() -> service.renommer(autre, convA1, "Pirate"));
            introuvable(() -> service.archiver(autre, convA1, true));
        }
        Map<String, Object> intacte = service.lire(A1, convA1);
        assertThat(intacte.get("titre")).isEqualTo(QUESTION);
        assertThat(intacte.get("archivee")).isEqualTo(false);
        assertThat((List<?>) intacte.get("echanges")).hasSize(1);
        // Meme une conversation archivee d'un autre reste invisible.
        service.archiver(A1, convA1, true);
        assertThat(service.lister(A2, true)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void la_conversation_garde_questions_reponses_et_sources_citees() {
        UUID conv = idDe(service.creer(A1, QUESTION));
        service.poser(A1, conv, "Faut-il l'agrement des associes pour une cession de parts sociales ?");

        Map<String, Object> lue = service.lire(A1, conv);
        List<Map<String, Object>> echanges = (List<Map<String, Object>>) lue.get("echanges");
        assertThat(echanges).extracting(e -> e.get("question"))
                .containsExactly(QUESTION, "Faut-il l'agrement des associes pour une cession de parts sociales ?");
        assertThat(String.valueOf(echanges.get(0).get("reponse"))).contains("trois quarts du capital");
        assertThat(echanges.get(0).get("ragMode")).isEqualTo("extraits");
        List<Map<String, Object>> sources = (List<Map<String, Object>>) echanges.get(0).get("sources");
        assertThat(sources).extracting(s -> s.get("reference")).containsExactly("Loi 5-96");
        assertThat(sources).extracting(s -> s.get("article")).containsExactly("Article 58");
        assertThat(lue.get("nombreEchanges")).isEqualTo(2L);
    }

    @Test
    void liste_triee_par_derniere_activite() {
        UUID ancienne = idDe(service.creer(A1, QUESTION));
        UUID recente = idDe(service.creer(A1, "Comment ceder des parts sociales ?"));
        assertThat(ids(service.lister(A1, false))).containsExactly(recente, ancienne);

        service.poser(A1, ancienne, "Et la cession entre associes ?");
        assertThat(ids(service.lister(A1, false))).containsExactly(ancienne, recente);
    }

    // ----------------------------------------------------------------- titre

    @Test
    void titre_tire_de_la_premiere_question() {
        assertThat(service.lister(A1, false)).isEmpty();
        UUID conv = idDe(service.creer(A1, "  Quelle   majorite pour la cession ?  "));
        assertThat(service.lire(A1, conv).get("titre")).isEqualTo("Quelle majorite pour la cession ?");
        // La question suivante ne change pas le titre.
        service.poser(A1, conv, "Et pour une SA ?");
        assertThat(service.lire(A1, conv).get("titre")).isEqualTo("Quelle majorite pour la cession ?");
    }

    @Test
    void titre_long_tronque_sans_couper_un_mot() {
        String longue = "Quelle est la majorite requise pour la cession de parts sociales a des tiers "
                + "dans une SARL dont les statuts ne prevoient aucune clause particuliere ?";
        UUID conv = idDe(service.creer(A1, longue));
        String titre = String.valueOf(service.lire(A1, conv).get("titre"));

        assertThat(titre).hasSizeLessThanOrEqualTo(ConversationsChatbotService.TITRE_INITIAL_MAX).endsWith("\u2026");
        String debut = titre.substring(0, titre.length() - 1);
        assertThat(longue).startsWith(debut);
        // Le mot qui suit le titre commence apres un espace : aucun mot n'est coupe.
        assertThat(longue.charAt(debut.length())).isEqualTo(' ');
        assertThat(titre).isEqualTo("Quelle est la majorite requise pour la cession de parts sociales a des tiers\u2026");
    }

    @Test
    void titre_d_un_seul_mot_tres_long_coupe_a_la_limite() {
        String titre = ConversationsChatbotService.titreDe("x".repeat(200));
        assertThat(titre).hasSize(ConversationsChatbotService.TITRE_INITIAL_MAX).endsWith("\u2026");
    }

    @Test
    void renommage() {
        UUID conv = idDe(service.creer(A1, QUESTION));
        service.renommer(A1, conv, "  Cession   de parts  ");
        assertThat(service.lire(A1, conv).get("titre")).isEqualTo("Cession de parts");

        assertThatThrownBy(() -> service.renommer(A1, conv, "   "))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.renommer(A1, conv, "Dossier de client.sarl@gmail.com"))
                .isInstanceOf(DonneePersonnelleDetecteeException.class);
        assertThat(service.lire(A1, conv).get("titre")).isEqualTo("Cession de parts");
    }

    // ------------------------------------------------------------- archivage

    @Test
    void archivage_consultable_sans_nouvelle_question_puis_desarchivage() {
        UUID conv = idDe(service.creer(A1, QUESTION));
        Map<String, Object> archivee = service.archiver(A1, conv, true);
        assertThat(archivee.get("archivee")).isEqualTo(true);
        assertThat(archivee.get("archiveeLe")).isNotNull();

        assertThat(service.lister(A1, false)).isEmpty();
        assertThat(ids(service.lister(A1, true))).containsExactly(conv);
        assertThat((List<?>) service.lire(A1, conv).get("echanges")).hasSize(1);

        assertThatThrownBy(() -> service.poser(A1, conv, "Et pour une SA ?"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(compter("chatbot_echanges")).isEqualTo(1);

        Map<String, Object> rouverte = service.archiver(A1, conv, false);
        assertThat(rouverte.get("archivee")).isEqualTo(false);
        assertThat(rouverte.get("archiveeLe")).isNull();
        service.poser(A1, conv, "Et pour une SA ?");
        assertThat(compter("chatbot_echanges")).isEqualTo(2);
        assertThat(ids(service.lister(A1, false))).containsExactly(conv);
    }

    // ---------------------------------------------------------------- RG-COM-04

    @Test
    void question_bloquee_jamais_enregistree() {
        RagService interdit = new RagService(new CorpusRetriever(owner), false, 5) {
            @Override
            public Map<String, Object> ask(String question) {
                throw new AssertionError("une question bloquee ne doit pas atteindre la recherche");
            }
        };
        ConversationsChatbotService garde = new ConversationsChatbotService(owner, interdit);

        assertThatThrownBy(() -> garde.creer(A1, "Le client client.sarl@gmail.com peut-il etre gerant ?"))
                .isInstanceOf(DonneePersonnelleDetecteeException.class);
        assertThat(compter("chatbot_conversations")).isZero();
        assertThat(compter("chatbot_echanges")).isZero();

        UUID conv = idDe(service.creer(A1, QUESTION));
        assertThatThrownBy(() -> garde.poser(A1, conv, "Joignable au 0612345678, peut-il ceder ?"))
                .isInstanceOf(DonneePersonnelleDetecteeException.class);
        assertThat(compter("chatbot_conversations")).isEqualTo(1);
        assertThat(compter("chatbot_echanges")).isEqualTo(1);
        assertThat(owner.queryForList("SELECT question FROM chatbot_echanges", String.class))
                .noneMatch(q -> q.contains("0612345678"));
    }

    // --------------------------------------------------------------- RLS jurika_app

    private void enRoleExecution(Consumer<JdbcTemplate> travail) throws SQLException {
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), "jurika_app", "jurika_app_it")) {
            travail.accept(new JdbcTemplate(new SingleConnectionDataSource(c, true)));
        }
    }

    @Test
    void rls_le_role_d_execution_ne_voit_que_le_cabinet_de_la_transaction() throws Exception {
        UUID convA1 = idDe(service.creer(A1, QUESTION));
        UUID convB1 = idDe(service.creer(B1, "Comment ceder des parts sociales ?"));

        enRoleExecution(app -> {
            ConversationsChatbotService enApp =
                    new ConversationsChatbotService(app, new RagService(new CorpusRetriever(app), false, 5));
            // Le service pose le cabinet dans chaque transaction : il fonctionne sous RLS.
            assertThat(ids(enApp.lister(B1, false))).containsExactly(convB1);
            UUID nouvelle = idDe(enApp.creer(B1, "Et la cession entre associes ?"));
            assertThat(ids(enApp.lister(B1, false))).contains(nouvelle);
            // Meme en demandant le cabinet A par son filtre, une session du cabinet B ne lit rien de A.
            app.execute("SELECT set_config('app.current_workspace_id', '" + WS_B + "', false)");
            assertThat(app.queryForList("SELECT id FROM chatbot_conversations WHERE workspace_id = ?", UUID.class, WS_A))
                    .isEmpty();
            assertThat(app.queryForList("SELECT id FROM chatbot_echanges WHERE workspace_id = ?", UUID.class, WS_A))
                    .isEmpty();
            assertThat(app.update("UPDATE chatbot_conversations SET titre = 'pirate' WHERE id = ?", convA1)).isZero();
            // Une ecriture au nom d'un autre cabinet est refusee par la politique.
            assertThatThrownBy(() -> app.update("INSERT INTO chatbot_conversations (workspace_id, user_id, titre) "
                    + "VALUES (?, ?, 'x')", WS_A, A1.userId())).rootCause().hasMessageContaining("row-level security");
            // Aucune suppression accordee (malgre les privileges par defaut du schema) :
            // l'archivage remplace la suppression ; un echange enregistre ne se reecrit pas.
            assertThatThrownBy(() -> app.update("DELETE FROM chatbot_echanges")).rootCause().hasMessageContaining("permission denied");
            assertThatThrownBy(() -> app.update("DELETE FROM chatbot_conversations")).rootCause().hasMessageContaining("permission denied");
            assertThatThrownBy(() -> app.update("UPDATE chatbot_echanges SET reponse = 'x'")).rootCause().hasMessageContaining("permission denied");
        });
        assertThat(owner.queryForObject("SELECT titre FROM chatbot_conversations WHERE id = ?", String.class, convA1))
                .isEqualTo(QUESTION);
    }
}
