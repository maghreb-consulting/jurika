package ma.jurika.ai.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0, etape E24 (perimetre § E, CDC § 3.1, RG-ADM-03) : l'ajout et le retrait
 * des sources du chatbot sont reserves au super-admin (JURIKA). Gardes evaluees
 * avec le gestionnaire d'expressions reel (GardesSuperviseurAiTest).
 */
class ChatbotSourcesSecurityTest {

    @ParameterizedTest
    @ValueSource(strings = {"addSource", "deleteSource"})
    void super_admin_autorise(String action) {
        assertThat(GardesSuperviseurAiTest.autorise("ROLE_SUPER_ADMIN",
                GardesSuperviseurAiTest.methode(ChatbotController.class, action))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"addSource", "deleteSource"})
    void roles_du_cabinet_et_client_refuses(String action) {
        for (String role : new String[] {"ROLE_EMPLOYE", "ROLE_SUPERVISEUR", "ROLE_CLIENT"}) {
            assertThat(GardesSuperviseurAiTest.autorise(role,
                    GardesSuperviseurAiTest.methode(ChatbotController.class, action))).as(role).isFalse();
        }
    }
}
