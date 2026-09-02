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
 * <h2>Pourquoi</h2>
 *
 * <p>Avant ce fix, chaque endpoint utilisait {@code hasRole('EMPLOYE')},
 * {@code hasRole('SUPERVISEUR')}, {@code hasRole('ADMIN_CABINET')} etc.
 * sans hierarchie -- un SUPERVISEUR ne pouvait pas creer de ticket
 * (endpoint reserve a EMPLOYE) ni faire d'action client. C'est inadapte :
 * l'admin d'un cabinet (SUPERVISEUR) doit forcement avoir acces a tout
 * ce que ses employes font, et un SUPER_ADMIN doit forcement avoir acces
 * a tout ce qu'un SUPERVISEUR fait.
 *
 * <h2>Hierarchie</h2>
 *
 * <pre>
 *   SUPER_ADMIN > SUPERVISEUR > EMPLOYE > CLIENT
 * </pre>
 *
 * Effet : {@code hasRole('EMPLOYE')} autorise aussi SUPERVISEUR et SUPER_ADMIN.
 * {@code hasAuthority('ROLE_EMPLOYE')} aussi.
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
                ROLE_SUPERVISEUR > ROLE_EMPLOYE
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
