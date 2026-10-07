package ma.jurika.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Lot L0, etape E10a (G2) : {@link JwtAuthFilter} doit vider {@link TenantContext}
 * a la fin de CHAQUE requete, y compris sans jeton. Avant L0, la sortie
 * anticipee « pas de Bearer » ne passait pas par le finally : un workspace pose
 * pendant une requete publique (connexion, inscription...) restait sur le fil
 * et etait vu par la requete suivante servie par ce fil.
 */
class JwtAuthFilterTenantContextTest {

    private final JwtAuthFilter filtre = new JwtAuthFilter(mock(JwtPublicKeyProvider.class));

    @AfterEach
    void nettoyer() {
        TenantContext.clear();
    }

    @Test
    void requete_sans_jeton_ne_laisse_pas_de_workspace_sur_le_fil() throws Exception {
        UUID poseParLaRequete = UUID.randomUUID();
        filtre.doFilter(new MockHttpServletRequest("POST", "/api/v1/auth/login"),
                new MockHttpServletResponse(),
                (req, res) -> TenantContext.set(poseParLaRequete));
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void requete_sans_jeton_n_herite_pas_d_un_workspace_residuel() throws Exception {
        TenantContext.set(UUID.randomUUID());
        UUID[] vuPendantLaRequete = new UUID[1];
        filtre.doFilter(new MockHttpServletRequest("GET", "/api/v1/public/pricing"),
                new MockHttpServletResponse(),
                (req, res) -> vuPendantLaRequete[0] = TenantContext.get());
        assertThat(vuPendantLaRequete[0]).isNull();
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void jeton_invalide_ne_laisse_pas_de_workspace_sur_le_fil() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tickets");
        req.addHeader("Authorization", "Bearer pas-un-jwt");
        filtre.doFilter(req, new MockHttpServletResponse(),
                (r, s) -> TenantContext.set(UUID.randomUUID()));
        assertThat(TenantContext.get()).isNull();
    }
}
