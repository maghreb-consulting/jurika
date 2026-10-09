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

/** Lot L1, etape E8 : seul le superviseur du cabinet regle le droit de suppression (CDC 3.2). */
class DroitSuppressionDataroomSecurityTest {

    @Test
    void superviseur_seul() {
        for (String nom : new String[]{"definir", "lire"}) {
            Method m = Arrays.stream(DroitSuppressionDataroomController.class.getDeclaredMethods())
                    .filter(x -> x.getName().equals(nom)).findFirst().orElseThrow();
            assertThat(autorise("ROLE_SUPERVISEUR", m)).as(nom).isTrue();
            assertThat(autorise("ROLE_EMPLOYE", m)).as(nom).isFalse();
            assertThat(autorise("ROLE_CLIENT", m)).as(nom).isFalse();
            assertThat(autorise("ROLE_SUPER_ADMIN", m)).as(nom).isFalse();
        }
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
