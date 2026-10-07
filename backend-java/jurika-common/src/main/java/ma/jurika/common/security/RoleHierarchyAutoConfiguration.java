package ma.jurika.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/**
 * Auto-configuration de la hierarchie de roles pour tous les services
 * qui dependent de jurika-common.
 *
 * <h2>Hierarchie</h2>
 *
 * <pre>
 *   SUPER_ADMIN > SUPERVISEUR
 *   EMPLOYE > CLIENT
 * </pre>
 *
 * <p>Lot L0 (E5) : l'heritage {@code SUPERVISEUR > EMPLOYE} est RETIRE.
 * Le superviseur observe sans agir (CDC section 3.2 : il « ne cree pas de
 * ticket, n'execute pas de workflow, ne genere pas d'acte ») ; avec
 * l'heritage, toute garde ecrite pour l'employe lui etait ouverte (constat
 * prouve par RoleHierarchyCaracterisationTest). Ses lectures et les actions
 * que le CDC lui accorde sont autorisees EXPLICITEMENT par chaque garde.
 * Consequence voulue : le SUPER_ADMIN n'herite plus non plus des actions de
 * l'employe (CDC section 3.1 : il gere la plateforme, pas les dossiers).
 *
 * <h2>Note Spring Security 6</h2>
 *
 * Les deux beans DOIVENT etre {@code static} pour eviter l'avertissement
 * d'initialisation precoce avec {@link org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity}.
 */
@AutoConfiguration
@ConditionalOnClass(RoleHierarchy.class)
public class RoleHierarchyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPER_ADMIN > ROLE_SUPERVISEUR
                ROLE_EMPLOYE > ROLE_CLIENT
                """);
    }

    @Bean
    @ConditionalOnMissingBean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}
