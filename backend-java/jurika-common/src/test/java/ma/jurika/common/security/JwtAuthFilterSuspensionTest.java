package ma.jurika.common.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L7 (E8), article 4.4 du contrat : le jeton d'un workspace SUSPENDU pour impaye n'ouvre que
 * les chemins de {@link AccesArchive} (le superviseur paie l'echeance) ; tout autre chemin est
 * refuse 403 {@code WORKSPACE_SUSPENDU}, dans chaque service (P6).
 */
class JwtAuthFilterSuspensionTest {

    private static final SecretKey CLE = new SecretKeySpec(
            "cle-de-test-du-filtre-suspension-32-octets-au-moins".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private static String jeton(boolean suspendu) {
        var b = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .claim(JwtClaims.TYPE, JwtClaims.TYPE_ACCESS)
                .claim(JwtClaims.USER_ID, UUID.randomUUID().toString())
                .claim(JwtClaims.WORKSPACE_ID, UUID.randomUUID().toString())
                .claim(JwtClaims.EMAIL, "superviseur@cabinet.invalid")
                .claim(JwtClaims.ROLE, Role.SUPERVISEUR.name());
        if (suspendu) b.claim(JwtClaims.SUSPENDU, true);
        return b.signWith(CLE, Jwts.SIG.HS256).compact();
    }

    private static MockHttpServletResponse appeler(String chemin, boolean suspendu, AtomicBoolean passe) throws Exception {
        JwtAuthFilter filtre = new JwtAuthFilter(() -> CLE);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", chemin);
        req.setRequestURI(chemin);
        req.addHeader("Authorization", "Bearer " + jeton(suspendu));
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtre.doFilter(req, res, (r, s) -> passe.set(true));
        return res;
    }

    @Test
    void workspace_suspendu_les_dossiers_sont_refuses_avec_le_code_de_la_suspension() throws Exception {
        AtomicBoolean passe = new AtomicBoolean(false);
        MockHttpServletResponse res = appeler("/api/v1/tickets", true, passe);
        assertThat(passe).isFalse();
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("\"code\":\"WORKSPACE_SUSPENDU\"").contains("suspendu");
    }

    @Test
    void workspace_suspendu_la_facturation_reste_ouverte_pour_payer() throws Exception {
        AtomicBoolean passe = new AtomicBoolean(false);
        MockHttpServletResponse res = appeler("/api/v1/billing/abonnement", true, passe);
        assertThat(passe).isTrue();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void workspace_non_suspendu_rien_n_est_restreint() throws Exception {
        AtomicBoolean passe = new AtomicBoolean(false);
        appeler("/api/v1/tickets", false, passe);
        assertThat(passe).isTrue();
    }
}
