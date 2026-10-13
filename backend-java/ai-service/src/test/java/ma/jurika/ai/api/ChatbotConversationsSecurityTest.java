package ma.jurika.ai.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L5b E2 (RG-COM-12) : les conversations du chatbot ont les memes ayants droit que la
 * question (employe, superviseur, super-admin) ; le client n'y a pas acces. Gardes evaluees
 * avec le gestionnaire d'expressions reel (GardesSuperviseurAiTest).
 */
class ChatbotConversationsSecurityTest {

    private static final String[] ACTIONS =
            {"lister", "creer", "lire", "poser", "renommer", "archiver", "desarchiver"};

    @ParameterizedTest
    @ValueSource(strings = {"lister", "creer", "lire", "poser", "renommer", "archiver", "desarchiver"})
    void cabinet_et_super_admin_autorises_client_refuse(String action) {
        var methode = GardesSuperviseurAiTest.methode(ChatbotConversationsController.class, action);
        for (String role : new String[] {"ROLE_EMPLOYE", "ROLE_SUPERVISEUR", "ROLE_SUPER_ADMIN"}) {
            assertThat(GardesSuperviseurAiTest.autorise(role, methode)).as(role).isTrue();
        }
        assertThat(GardesSuperviseurAiTest.autorise("ROLE_CLIENT", methode)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void toutes_les_routes_sont_gardees() {
        long routes = java.util.Arrays.stream(ChatbotConversationsController.class.getDeclaredMethods())
                .filter(m -> java.util.Arrays.stream(m.getAnnotations()).anyMatch(a ->
                        a.annotationType().getPackageName().equals("org.springframework.web.bind.annotation")
                                && a.annotationType().getSimpleName().endsWith("Mapping")))
                .count();
        assertThat(routes).isEqualTo(ACTIONS.length);
    }
}
