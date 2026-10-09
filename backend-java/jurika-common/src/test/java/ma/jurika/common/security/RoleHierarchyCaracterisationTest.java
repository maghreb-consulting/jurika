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
 * Effet REEL de {@link RoleHierarchyAutoConfiguration} sur les gardes
 * {@code @PreAuthorize} telles qu'elles sont ecrites dans les services.
 *
 * <p>Lot L0, etape E2 : caracterisation de l'etat d'avant (la hierarchie
 * SUPERVISEUR > EMPLOYE ouvrait au superviseur toute garde ecrite pour
 * l'employe). Etape E5 : l'heritage est retire, le test est inverse. Le
 * superviseur observe sans agir (CDC section 3.2) ; le SUPER_ADMIN n'herite
 * plus des actions de l'employe (section 3.1).
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
    void superviseur_refuse_sur_hasAuthority_ROLE_EMPLOYE() {
        runner.run(ctx -> {
            authentifier("ROLE_SUPERVISEUR");
            GardesEmploye gardes = ctx.getBean(GardesEmploye.class);
            assertThatThrownBy(gardes::hasAuthorityEmploye).isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void superviseur_refuse_sur_hasRole_EMPLOYE() {
        runner.run(ctx -> {
            authentifier("ROLE_SUPERVISEUR");
            GardesEmploye gardes = ctx.getBean(GardesEmploye.class);
            assertThatThrownBy(gardes::hasRoleEmploye).isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void superviseur_refuse_sur_hasAnyAuthority_EMPLOYE_SUPER_ADMIN() {
        // Forme de WorkflowDocumentController:163, DocumentController:60,
        // DocumentRenderController:70,107 (generation d'actes).
        runner.run(ctx -> {
            authentifier("ROLE_SUPERVISEUR");
            GardesEmploye gardes = ctx.getBean(GardesEmploye.class);
            assertThatThrownBy(gardes::hasAnyAuthorityEmployeOuSuperAdmin).isInstanceOf(AccessDeniedException.class);
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
    void super_admin_n_herite_plus_des_gardes_employe() {
        runner.run(ctx -> {
            authentifier("ROLE_SUPER_ADMIN");
            GardesEmploye gardes = ctx.getBean(GardesEmploye.class);
            assertThatThrownBy(gardes::hasAuthorityEmploye).isInstanceOf(AccessDeniedException.class);
        });
    }

    @Test
    void employe_passe_sa_garde_et_herite_du_client() {
        runner.run(ctx -> {
            authentifier("ROLE_EMPLOYE");
            GardesEmploye gardes = ctx.getBean(GardesEmploye.class);
            assertThatCode(gardes::hasAuthorityEmploye).doesNotThrowAnyException();
            assertThatCode(gardes::hasAuthorityClient).doesNotThrowAnyException();
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

        @PreAuthorize("hasAuthority('ROLE_CLIENT')")
        public void hasAuthorityClient() { }
    }
}
