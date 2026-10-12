package ma.jurika.ai.api;

import ma.jurika.ai.rag.CorpusRetriever;
import ma.jurika.ai.rag.DonneePersonnelleDetecteeException;
import ma.jurika.ai.rag.FiltreDonneesPersonnelles;
import ma.jurika.ai.rag.RagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L0, etape E24 (perimetre point E, CDC section 3.1, RG-ADM-03) : l'ajout et le retrait
 * des sources du chatbot sont reserves au super-admin (JURIKA). Gardes evaluees
 * avec le gestionnaire d'expressions reel (GardesSuperviseurAiTest).
 *
 * <p>Lot L5 E8 : la liste des sources communes est reservee au super-admin ; la question
 * au chatbot est ouverte au cabinet (employe, superviseur) et au super-admin, pas au client ;
 * une question portant une donnee personnelle est refusee avant toute recherche (RG-COM-04).
 */
class ChatbotSourcesSecurityTest {

    @ParameterizedTest
    @ValueSource(strings = {"addSource", "deleteSource", "listSources"})
    void super_admin_autorise(String action) {
        assertThat(GardesSuperviseurAiTest.autorise("ROLE_SUPER_ADMIN",
                GardesSuperviseurAiTest.methode(ChatbotController.class, action))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"addSource", "deleteSource", "listSources"})
    void roles_du_cabinet_et_client_refuses(String action) {
        for (String role : new String[] {"ROLE_EMPLOYE", "ROLE_SUPERVISEUR", "ROLE_CLIENT"}) {
            assertThat(GardesSuperviseurAiTest.autorise(role,
                    GardesSuperviseurAiTest.methode(ChatbotController.class, action))).as(role).isFalse();
        }
    }

    @Test
    void question_ouverte_au_cabinet_et_au_super_admin_pas_au_client() {
        var ask = GardesSuperviseurAiTest.methode(ChatbotController.class, "ask");
        for (String role : new String[] {"ROLE_EMPLOYE", "ROLE_SUPERVISEUR", "ROLE_SUPER_ADMIN"}) {
            assertThat(GardesSuperviseurAiTest.autorise(role, ask)).as(role).isTrue();
        }
        assertThat(GardesSuperviseurAiTest.autorise("ROLE_CLIENT", ask)).isFalse();
    }

    @Test
    void question_avec_donnee_personnelle_refusee_avant_le_service() {
        RagService interdit = new RagService(new CorpusRetriever((org.springframework.jdbc.core.JdbcTemplate) null) {
            @Override
            public List<Map<String, Object>> searchHybrid(String q, int limit) {
                throw new AssertionError("la question ne doit pas etre traitee");
            }
        }, false, 5) {
            @Override
            public Map<String, Object> ask(String question) {
                throw new AssertionError("la question ne doit pas atteindre le service");
            }
        };
        ChatbotController controller = new ChatbotController(interdit, null);

        assertThatThrownBy(() -> controller.ask(new ChatbotController.AskRequest(
                "Le client client.sarl@gmail.com, ne le 12/05/1980, peut-il etre gerant ?")))
                .isInstanceOf(DonneePersonnelleDetecteeException.class)
                .hasMessageNotContaining("client.sarl@gmail.com")
                .satisfies(ex -> assertThat(((DonneePersonnelleDetecteeException) ex).natures())
                        .contains(FiltreDonneesPersonnelles.Nature.EMAIL));
    }

    @Test
    void refus_422_nomme_la_nature_sans_la_valeur() {
        Map<String, Object> corps = ChatbotController.corpsRefus(
                List.of(FiltreDonneesPersonnelles.Nature.TELEPHONE, FiltreDonneesPersonnelles.Nature.RIB));
        assertThat(corps).containsEntry("status", 422).containsEntry("code", "DONNEE_PERSONNELLE");
        assertThat(String.valueOf(corps.get("message"))).contains("num\u00e9ro de t\u00e9l\u00e9phone").contains("RIB");
        assertThat(corps.get("donnees")).asList().hasSize(2);
    }
}
