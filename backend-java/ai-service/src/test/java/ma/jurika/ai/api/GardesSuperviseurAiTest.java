package ma.jurika.ai.api;

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
 * Lot L0, etape E5 : la generation, le rendu et l'edition d'actes d'ai-service
 * sont des actions que le superviseur ne fait pas (CDC section 3.2 : il
 * « ne cree pas de ticket, n'execute pas de workflow, ne genere pas d'acte »).
 * Elles ne lui etaient ouvertes que par l'heritage SUPERVISEUR > EMPLOYE.
 *
 * <p>Le test evalue la garde {@code @PreAuthorize} REELLE de chaque methode
 * avec le gestionnaire d'expressions de {@link RoleHierarchyAutoConfiguration}
 * (celui qu'utilise la securite de methode des services, cf.
 * RoleHierarchyCaracterisationTest dans jurika-common).
 */
class GardesSuperviseurAiTest {

    static Stream<Method> actions() {
        return Stream.of(
                methode(DocumentController.class, "generate"),
                methode(ma.jurika.ai.workflow.WorkflowDocumentController.class, "generate"),
                methode(ma.jurika.ai.workflow.WorkflowDocumentController.class, "listTemplates"),
                methode(DocumentRenderController.class, "templateToPdf"),
                methode(DocumentRenderController.class, "docxToPdf"),
                methode(DocumentEditController.class, "htmlToDocx"),
                methode(DocumentEditController.class, "htmlToPdf")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("actions")
    void superviseur_refuse(Method action) {
        assertThat(autorise("ROLE_SUPERVISEUR", action)).isFalse();
    }

    /**
     * Lot L0, etape E5b : le SUPER_ADMIN gere la plateforme et ne travaille
     * pas dans les dossiers (CDC section 3.1) : la generation et l'edition
     * d'actes ne lui sont plus ouvertes.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("actions")
    void super_admin_refuse(Method action) {
        assertThat(autorise("ROLE_SUPER_ADMIN", action)).isFalse();
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
