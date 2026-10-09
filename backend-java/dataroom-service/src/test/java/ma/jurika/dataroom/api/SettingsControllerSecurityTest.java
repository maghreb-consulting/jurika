package ma.jurika.dataroom.api;

import ma.jurika.common.security.RoleHierarchyAutoConfiguration;
import ma.jurika.dataroom.api.dto.DataroomDtos.ToggleSuspensionRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.UpdatePermissionsRequest;
import ma.jurika.dataroom.application.DataroomSettingsService;
import ma.jurika.dataroom.application.access.ClientAccessLogQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Lot L0, etape E4 : les trois actions de gestion de l'acces client
 * (permissions, suspension, lien) sont ouvertes EXPLICITEMENT au superviseur
 * (RG-CLI-01 : « par l'employe responsable ou par le superviseur » ;
 * CDC section 3.2 : « Gere les acces des clients »), et non plus par la
 * seule hierarchie SUPERVISEUR > EMPLOYE, qui sera retiree a l'etape E5.
 *
 * <p>Le test verifie la garde {@code @PreAuthorize} elle-meme : la garde est
 * franchie si la methode atteint le service (ou echoue pour une autre raison
 * que {@link AccessDeniedException}).
 */
class SettingsControllerSecurityTest {

    private static final UUID DOSSIER = UUID.randomUUID();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RoleHierarchyAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superviseur_gere_l_acces_client() {
        verifier("ROLE_SUPERVISEUR", true);
    }

    @Test
    void employe_gere_l_acces_client() {
        verifier("ROLE_EMPLOYE", true);
    }

    @Test
    void client_ne_gere_pas_l_acces_client() {
        verifier("ROLE_CLIENT", false);
    }

    private void verifier(String role, boolean garderFranchie) {
        runner.run(ctx -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("u", "p", role));
            SettingsController c = ctx.getBean(SettingsController.class);
            assertThat(franchit(() -> c.updatePermissions(DOSSIER,
                    new UpdatePermissionsRequest(true, true, true))))
                    .as("PATCH permissions pour %s", role).isEqualTo(garderFranchie);
            assertThat(franchit(() -> c.toggleSuspension(DOSSIER, new ToggleSuspensionRequest(true))))
                    .as("PATCH suspension pour %s", role).isEqualTo(garderFranchie);
            assertThat(franchit(() -> c.regenerateLink(DOSSIER)))
                    .as("POST regenerate-link pour %s", role).isEqualTo(garderFranchie);
        });
    }

    /** true si la garde laisse passer (aucune AccessDeniedException). */
    private static boolean franchit(Runnable appel) {
        try {
            appel.run();
            return true;
        } catch (AccessDeniedException refus) {
            return false;
        } catch (RuntimeException autre) {
            // Le service simule renvoie null : l'echec vient d'apres la garde.
            return true;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean
        SettingsController settingsController() {
            return new SettingsController(mock(DataroomSettingsService.class),
                    mock(ClientAccessLogQueryService.class));
        }
    }
}
