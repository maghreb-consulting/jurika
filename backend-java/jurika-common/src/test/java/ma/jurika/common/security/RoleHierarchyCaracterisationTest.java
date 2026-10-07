package ma.jurika.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Caracterisation (lot L0, etape E2) : constate l'effet REEL de
 * {@link RoleHierarchyAutoConfiguration} sur les gardes {@code @PreAuthorize}
 * telles qu'elles sont ecrites dans les services.
 *
 * <p>Etat constate avant L0 : la hierarchie SUPERVISEUR > EMPLOYE ouvre au
 * superviseur toute garde ecrite pour l'employe, y compris sous la forme
 * {@code hasAuthority('ROLE_EMPLOYE')}. Seule la forme
 * {@code hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')} (ticket, workflow)
 * le refuse. Le CDC (section 3.2) veut l'inverse : le superviseur observe
 * sans agir. Ce test sera inverse a l'etape E5 (retrait de l'heritage).
 */
class RoleHierarchyCaracterisationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RoleHierarchyAutoConfiguration.class))
            .withUserConfiguration(MethodSecurityConfig.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superviseur_passe_hasAuthority_ROLE_EMPLOYE_par_heritage() {
        runner.run(ctx -> {
            authentifier("ROLE_SUPERVISEUR");
            GardesEmploye gardes = ctx.getBean(GardesEmploye.class);
            assertThatCode(gardes::hasAuthorityEmploye).doesNotThrowAnyException();
        });
    }

    @Test
    void superviseur_passe_hasRole_EMPLOYE_par_heritage() {
        runner.run(ctx -> {
            authentifier("ROLE_SUPERVISEUR");
            GardesEmploye gardes = ctx.getBean(GardesEmploye.class);
            assertThatCode(gardes::hasRoleEmploye).doesNotThrowAnyException();
        });
    }

    @Test
    void superviseur_passe_hasAnyAuthority_EMPLOYE_SUPER_ADMIN_par_heritage() {
        // Forme de WorkflowDocumentController:163, DocumentController:60,
        // DocumentRenderController:70,107 (generation d'actes).
        runner.run(ctx -> {
            authentifier("ROLE_SUPERVISEUR");
            GardesEmploye gardes = ctx.getBean(GardesEmploye.class);
            assertThatCode(gardes::hasAnyAuthorityEmployeOuSuperAdmin).doesNotThrowAnyException();
        });
    }

    @Test
    void superviseur_refuse_seulement_par_exclusion_explicite() {
        // Forme de TicketController:87 et WorkflowController.
        runner.run(ctx -> {
            authentifier("ROLE_SUPERVISEUR");
            GardesEmploye gardes = ctx.getBean(GardesEmploye.class);
            assertThatThrownBy(gardes::employeSaufSuperviseur).isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void client_refuse_sur_garde_employe() {
        runner.run(ctx -> {
            authentifier("ROLE_CLIENT");
            GardesEmploye gardes = ctx.getBean(GardesEmploye.class);
            assertThatThrownBy(gardes::hasAuthorityEmploye).isInstanceOf(AccessDeniedException.class);
        });
    }

    private static void authentifier(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("u", "p", role));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {
        @Bean
        GardesEmploye gardesEmploye() {
            return new GardesEmploye();
        }
    }

    static class GardesEmploye {
        @PreAuthorize("hasAuthority('ROLE_EMPLOYE')")
        public void hasAuthorityEmploye() { }

        @PreAuthorize("hasRole('EMPLOYE')")
        public void hasRoleEmploye() { }

        @PreAuthorize("hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPER_ADMIN')")
        public void hasAnyAuthorityEmployeOuSuperAdmin() { }

        @PreAuthorize("hasRole('EMPLOYE') and !hasRole('SUPERVISEUR')")
        public void employeSaufSuperviseur() { }
    }
}
