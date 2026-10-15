package ma.jurika.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L7 (E6, RG-CTR-03) : la passerelle refuse les routes du cabinet tant qu'un contrat est en
 * attente d'acceptation ; l'ecran du contrat et la session restent accessibles ; billing
 * injoignable sans reponse connue = refus (jamais un controle saute en silence).
 */
class ContratEnAttenteFilterTest {

    private static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static MockServerWebExchange requete(String chemin, String role) {
        MockServerHttpRequest.BaseBuilder<?> b = MockServerHttpRequest.get(chemin);
        if (role != null) {
            b.header(HeaderConstants.HEADER_ROLE, role).header(HeaderConstants.HEADER_WORKSPACE_ID, WS.toString());
        }
        return MockServerWebExchange.from(b);
    }

    private static boolean passe(ContratEnAttenteFilter f, MockServerWebExchange ex) {
        AtomicBoolean appele = new AtomicBoolean();
        GatewayFilterChain chain = e -> {
            appele.set(true);
            return Mono.empty();
        };
        f.filter(ex, chain).block();
        return appele.get();
    }

    @Test
    void contrat_en_attente_seul_l_ecran_du_contrat_et_la_session_passent() {
        ContratEnAttenteFilter f = new ContratEnAttenteFilter(ws -> Mono.just(true));
        MockServerWebExchange tickets = requete("/api/v1/tickets", "SUPERVISEUR");
        assertThat(passe(f, tickets)).isFalse();
        assertThat(tickets.getResponse().getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(tickets.getResponse().getBodyAsString().block()).contains("CONTRAT_EN_ATTENTE");
        assertThat(passe(f, requete("/api/v1/dataroom/x", "EMPLOYE"))).isFalse();
        assertThat(passe(f, requete("/api/v1/billing/subscription", "SUPERVISEUR"))).isFalse();
        assertThat(passe(f, requete("/api/v1/auth/users", "SUPERVISEUR"))).as("gestion de l'equipe").isFalse();

        assertThat(passe(f, requete("/api/v1/billing/contrat/etat", "SUPERVISEUR"))).isTrue();
        assertThat(passe(f, requete("/api/v1/billing/contrat/accepter", "SUPERVISEUR"))).isTrue();
        assertThat(passe(f, requete("/api/v1/auth/me", "EMPLOYE"))).isTrue();
        assertThat(passe(f, requete("/api/v1/auth/refresh", "EMPLOYE"))).isTrue();
        assertThat(passe(f, requete("/api/v1/auth/logout", "EMPLOYE"))).isTrue();
        assertThat(passe(f, requete("/api/v1/workspace/profile", "SUPERVISEUR"))).isTrue();
    }

    @Test
    void super_admin_client_et_requete_anonyme_ne_sont_pas_concernes() {
        ContratEnAttenteFilter f = new ContratEnAttenteFilter(ws -> Mono.just(true));
        assertThat(passe(f, requete("/api/v1/admin/workspaces", "SUPER_ADMIN"))).isTrue();
        assertThat(passe(f, requete("/api/v1/dataroom/x", "CLIENT"))).isTrue();
        assertThat(passe(f, requete("/api/v1/public/pricing", null))).isTrue();
    }

    @Test
    void sans_contrat_en_attente_tout_passe_et_la_reponse_est_gardee() {
        AtomicInteger appels = new AtomicInteger();
        ContratEnAttenteFilter f = new ContratEnAttenteFilter(ws -> {
            appels.incrementAndGet();
            return Mono.just(false);
        });
        assertThat(passe(f, requete("/api/v1/tickets", "SUPERVISEUR"))).isTrue();
        assertThat(passe(f, requete("/api/v1/tickets", "EMPLOYE"))).isTrue();
        assertThat(appels.get()).isEqualTo(1);
    }

    @Test
    void billing_injoignable_sans_reponse_connue_refuse_avec_un_code_clair() {
        ContratEnAttenteFilter f = new ContratEnAttenteFilter(ws -> Mono.error(new IllegalStateException("billing down")));
        MockServerWebExchange ex = requete("/api/v1/tickets", "SUPERVISEUR");
        assertThat(passe(f, ex)).isFalse();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("CONTRAT_VERIFICATION_INDISPONIBLE");
    }
}
