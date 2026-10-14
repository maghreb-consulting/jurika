package ma.jurika.auth.api;

import ma.jurika.common.security.RoleHierarchyAutoConfiguration;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L7 (E13) : seul le superviseur lit l'acceptation des conditions de l'essai de son cabinet
 * (il est seul a accepter le contrat) ; la route ne prend aucun identifiant de cabinet.
 */
class EssaiAcceptationSecurityTest {

    @Test
    void superviseur_seul_et_aucun_parametre_de_cabinet() {
        Method m = Arrays.stream(EssaiAcceptationController.class.getDeclaredMethods())
                .filter(x -> x.getName().equals("acceptation")).findFirst().orElseThrow();
        assertThat(autorise("ROLE_SUPERVISEUR", m)).isTrue();
        assertThat(autorise("ROLE_EMPLOYE", m)).isFalse();
        assertThat(autorise("ROLE_CLIENT", m)).isFalse();
        assertThat(autorise("ROLE_SUPER_ADMIN", m)).isFalse();
        // Seul parametre : l'utilisateur authentifie (le cabinet vient du jeton).
        assertThat(m.getParameterTypes()).containsExactly(ma.jurika.common.security.AuthenticatedUser.class);
    }

    private static boolean autorise(String role, Method methode) {
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(RoleHierarchyAutoConfiguration.class)) {
            MethodSecurityExpressionHandler handler = ctx.getBean(MethodSecurityExpressionHandler.class);
            Authentication auth = new TestingAuthenticationToken("u", "p", role);
            EvaluationContext ec = handler.createEvaluationContext(() -> auth,
                    new SimpleMethodInvocation(new Object(), methode));
            return ExpressionUtils.evaluateAsBoolean(handler.getExpressionParser()
                    .parseExpression(methode.getAnnotation(PreAuthorize.class).value()), ec);
        }
    }
}
