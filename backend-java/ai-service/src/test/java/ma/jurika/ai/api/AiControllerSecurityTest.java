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
 * Lot L0, etape E6 : AiController n'avait AUCUNE garde @PreAuthorize ; ses
 * routes etaient ouvertes a tout utilisateur authentifie (client, superviseur
 * compris) par anyRequest().authenticated(). L'extraction de pieces et la
 * production de documents sont le travail de l'employe (RG-VAR-09, CDC
 * section 3.2) : chaque route est reservee a ROLE_EMPLOYE.
 */
class AiControllerSecurityTest {

    static Stream<Method> routes() {
        return Arrays.stream(AiController.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class));
    }

    @org.junit.jupiter.api.Test
    void le_controleur_expose_quatre_routes() {
        assertThat(routes().map(Method::getName))
                .containsExactlyInAnyOrder("extractCn", "extractCin", "extractGeneric", "generate");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void chaque_route_porte_une_garde(Method route) {
        assertThat(route.isAnnotationPresent(PreAuthorize.class)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void reservee_a_l_employe(Method route) {
        org.junit.jupiter.api.Assumptions.assumeTrue(route.isAnnotationPresent(PreAuthorize.class));
        assertThat(autorise("ROLE_EMPLOYE", route)).isTrue();
        assertThat(autorise("ROLE_SUPERVISEUR", route)).isFalse();
        assertThat(autorise("ROLE_SUPER_ADMIN", route)).isFalse();
        assertThat(autorise("ROLE_CLIENT", route)).isFalse();
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
