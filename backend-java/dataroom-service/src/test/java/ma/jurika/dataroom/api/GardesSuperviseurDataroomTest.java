package ma.jurika.dataroom.api;

import ma.jurika.common.security.RoleHierarchyAutoConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.util.SimpleMethodInvocation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0, etape E5 : les ecritures de l'employe en Data Room (envoi, brouillons, suppression, versions, etat des demandes)
 * sont des actions que le superviseur ne fait pas (CDC section 3.2 : il
 * « ne cree pas de ticket, n'execute pas de workflow, ne genere pas d'acte »).
 * Elles ne lui etaient ouvertes que par l'heritage SUPERVISEUR > EMPLOYE.
 *
 * <p>Le test evalue la garde {@code @PreAuthorize} REELLE de chaque methode
 * avec le gestionnaire d'expressions de {@link RoleHierarchyAutoConfiguration}
 * (celui qu'utilise la securite de methode des services, cf.
 * RoleHierarchyCaracterisationTest dans jurika-common).
 */
class GardesSuperviseurDataroomTest {

    static Stream<Method> actions() {
        return Stream.of(
                methode(JuridiqueController.class, "uploadJuridique"),
                methode(JuridiqueController.class, "uploadJuridiqueBatch"),
                methode(JuridiqueController.class, "enregistrerBrouillon"),
                methode(JuridiqueController.class, "validerBrouillon"),
                methode(JuridiqueController.class, "supprimerBrouillon"),
                methode(JuridiqueController.class, "deleteJuridique"),
                methode(JuridiqueController.class, "deleteJuridiqueBulk"),
                methode(JuridiqueController.class, "replaceAsNewVersion"),
                methode(JuridiqueController.class, "restoreVersion"),
                methode(DemandesController.class, "updateDemande")
        );
    }

    /**
     * Lot L0, etape E5b : actions accordees EXPLICITEMENT au superviseur (et
     * au SUPER_ADMIN) que le CDC reserve a l'employe (sections 3.2 et 3.3) :
     * edition Collabora, requetes et validation des demandes, depots,
     * extraction de pieces, visibilite document par document.
     */
    static Stream<Method> actionsExplicites() {
        return Stream.of(
                methode(WopiController.class, "ouvrirSeance"),
                methode(WopiController.class, "fermerSeance"),
                methode(DemandesController.class, "createRequete"),
                methode(DemandesController.class, "valider"),
                methode(DemandesController.class, "complement"),
                methode(DepotController.class, "upload"),
                methode(DepotController.class, "delete"),
                methode(IdentityController.class, "extract"),
                methode(JuridiqueController.class, "changerVisibilite")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actionsExplicites")
    void superviseur_refuse_sur_les_actions_explicites(Method action) {
        assertThat(autorise("ROLE_SUPERVISEUR", action)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actionsExplicites")
    void super_admin_refuse_sur_les_actions_explicites(Method action) {
        assertThat(autorise("ROLE_SUPER_ADMIN", action)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actionsExplicites")
    void employe_autorise_sur_les_actions_explicites(Method action) {
        assertThat(autorise("ROLE_EMPLOYE", action)).isTrue();
    }

    @org.junit.jupiter.api.Test
    void client_garde_le_depot_et_sa_suppression() {
        assertThat(autorise("ROLE_CLIENT", methode(DepotController.class, "upload"))).isTrue();
        assertThat(autorise("ROLE_CLIENT", methode(DepotController.class, "delete"))).isTrue();
    }

    /** Actions du CLIENT (creer une demande, repondre a une requete). */
    static Stream<Method> actionsClient() {
        return Stream.of(
                methode(DemandesController.class, "createDemande"),
                methode(DemandesController.class, "repondre")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actionsClient")
    void superviseur_refuse_sur_les_actions_du_client(Method action) {
        assertThat(autorise("ROLE_SUPERVISEUR", action)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actions")
    void superviseur_refuse(Method action) {
        assertThat(autorise("ROLE_SUPERVISEUR", action)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actions")
    void employe_autorise(Method action) {
        assertThat(autorise("ROLE_EMPLOYE", action)).isTrue();
    }

    static Method methode(Class<?> type, String nom) {
        List<Method> trouvees = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.getName().equals(nom) && m.isAnnotationPresent(PreAuthorize.class))
                .toList();
        assertThat(trouvees).as("%s#%s garde", type.getSimpleName(), nom).hasSize(1);
        return trouvees.get(0);
    }

    static boolean autorise(String role, Method methode) {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(RoleHierarchyAutoConfiguration.class)) {
            MethodSecurityExpressionHandler handler = ctx.getBean(MethodSecurityExpressionHandler.class);
            Authentication auth = new TestingAuthenticationToken("u", "p", role);
            EvaluationContext ec = handler.createEvaluationContext(() -> auth,
                    new SimpleMethodInvocation(new Object(), methode));
            String expression = methode.getAnnotation(PreAuthorize.class).value();
            return ExpressionUtils.evaluateAsBoolean(
                    handler.getExpressionParser().parseExpression(expression), ec);
        }
    }
}
