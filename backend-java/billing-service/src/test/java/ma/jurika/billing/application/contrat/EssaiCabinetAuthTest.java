package ma.jurika.billing.application.contrat;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L7 (E13) : lecture chez auth de l'acceptation des conditions de l'essai du cabinet. Une
 * reponse illisible, incomplete ou en erreur, ou auth injoignable : refus nomme (503), jamais
 * "pas d'essai" par defaut. Serveur HTTP local (127.0.0.1), aucun appel reseau reel.
 */
class EssaiCabinetAuthTest {

    private HttpServer serveur;
    private final AtomicReference<String> jetonRecu = new AtomicReference<>();

    private EssaiCabinetAuth servi(int statut, String corps) throws Exception {
        serveur = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serveur.createContext("/api/v1/workspace/essai/acceptation", ech -> {
            jetonRecu.set(ech.getRequestHeaders().getFirst("Authorization"));
            byte[] b = corps.getBytes(StandardCharsets.UTF_8);
            ech.getResponseHeaders().add("Content-Type", "application/json");
            ech.sendResponseHeaders(statut, b.length);
            try (OutputStream o = ech.getResponseBody()) { o.write(b); }
        });
        serveur.start();
        return new EssaiCabinetAuth("http://127.0.0.1:" + serveur.getAddress().getPort());
    }

    @AfterEach
    void arreter() {
        if (serveur != null) serveur.stop(0);
    }

    @Test
    void essai_lu_avec_version_et_date_et_jeton_du_superviseur_relaye() throws Exception {
        EssaiCabinetAuth a = servi(200, "{\"essai\":true,\"version\":\"D-V1\",\"accepteesLe\":\"2026-10-12T09:32:41Z\"}");
        assertThat(a.lire("Bearer abc")).contains(
                new EssaiCabinetSource.AcceptationEssai("D-V1", Instant.parse("2026-10-12T09:32:41Z")));
        assertThat(jetonRecu.get()).isEqualTo("Bearer abc");
    }

    @Test
    void sans_essai_reponse_explicite_vide() throws Exception {
        EssaiCabinetAuth a = servi(200, "{\"essai\":false,\"version\":null,\"accepteesLe\":null}");
        assertThat(a.lire("Bearer abc")).isEmpty();
    }

    @Test
    void reponse_incomplete_ou_sans_indicateur_refus_nomme() throws Exception {
        for (String corps : new String[]{"{}", "{\"essai\":true,\"version\":\"D-V1\"}",
                "{\"essai\":true,\"accepteesLe\":\"2026-10-12T09:32:41Z\"}",
                "{\"essai\":true,\"version\":\"D-V1\",\"accepteesLe\":\"12/10/2026\"}", "{\"essai\":\"peut-etre\"}"}) {
            EssaiCabinetAuth a = servi(200, corps);
            assertThatThrownBy(() -> a.lire("Bearer abc")).as(corps).isInstanceOf(ContratException.class)
                    .satisfies(e -> assertThat(((ContratException) e).code()).isEqualTo("ESSAI_INDISPONIBLE"));
            arreter();
        }
    }

    @Test
    void erreur_d_auth_refus_nomme() throws Exception {
        EssaiCabinetAuth a = servi(409, "{\"code\":\"CONFLICT\",\"message\":\"introuvable\"}");
        assertThatThrownBy(() -> a.lire("Bearer abc")).isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).statut()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void auth_injoignable_refus_nomme() throws Exception {
        // Port ferme : on ouvre puis on libere un port local.
        int port;
        try (java.net.ServerSocket s = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = s.getLocalPort();
        }
        EssaiCabinetAuth a = new EssaiCabinetAuth("http://127.0.0.1:" + port);
        assertThatThrownBy(() -> a.lire("Bearer abc")).isInstanceOf(ContratException.class)
                .satisfies(e -> assertThat(((ContratException) e).code()).isEqualTo("ESSAI_INDISPONIBLE"))
                .hasMessageContaining("essai");
    }

    @Test
    void sans_jeton_refus() {
        EssaiCabinetAuth a = new EssaiCabinetAuth("http://127.0.0.1:1");
        assertThatThrownBy(() -> a.lire(null)).isInstanceOf(ContratException.class);
    }
}
