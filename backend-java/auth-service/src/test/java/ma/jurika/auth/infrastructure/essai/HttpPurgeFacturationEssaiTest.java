package ma.jurika.auth.infrastructure.essai;

import com.sun.net.httpserver.HttpServer;
import ma.jurika.auth.domain.port.PurgeFacturationEssai;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L7 (E14) : lecture du bilan rendu par billing-service ({@code DELETE /internal/essais/{ws}/paiements}),
 * au format de {@code SuppressionFacturationEssaiService.Bilan} ; une reponse sans bilan, ou un refus,
 * arrete la suppression (exception).
 */
class HttpPurgeFacturationEssaiTest {

    private HttpServer serveur;

    private String demarrer(int statut, String corps, AtomicReference<String> requete) throws Exception {
        serveur = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serveur.createContext("/", ex -> {
            requete.set(ex.getRequestMethod() + " " + ex.getRequestURI().getPath());
            byte[] b = corps.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(statut, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        serveur.start();
        return "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    @AfterEach
    void arreter() {
        if (serveur != null) serveur.stop(0);
    }

    @Test
    void bilan_de_billing_lu_tel_quel() throws Exception {
        UUID ws = UUID.randomUUID();
        AtomicReference<String> requete = new AtomicReference<>();
        String url = demarrer(200, """
                {"workspaceId":"%s","supprimees":{"payments":2,"cmi_retours":1},
                 "conservees10Ans":{"payments":1,"factures":1},
                 "conserveesDureeADecider":{"conditions_particulieres":0},
                 "echeanceConservation":"2036-10-14","dureePreuvesContrat":"A_DECIDER"}
                """.formatted(ws), requete);
        PurgeFacturationEssai.Bilan b = new HttpPurgeFacturationEssai(url).traiter(ws);
        assertThat(requete.get()).isEqualTo("DELETE /internal/essais/" + ws + "/paiements");
        assertThat(b.supprimees()).containsEntry("payments", 2L);
        assertThat(b.conservees10Ans()).containsEntry("factures", 1L);
        assertThat(b.echeanceConservation()).isEqualTo("2036-10-14");
        assertThat(b.dureePreuvesContrat()).isEqualTo("A_DECIDER");
    }

    @Test
    void reponse_sans_bilan_ou_refus_arrete_la_suppression() throws Exception {
        UUID ws = UUID.randomUUID();
        AtomicReference<String> requete = new AtomicReference<>();
        String url = demarrer(200, "{\"workspaceId\":\"" + ws + "\"}", requete);
        assertThatThrownBy(() -> new HttpPurgeFacturationEssai(url).traiter(ws))
                .isInstanceOf(IllegalStateException.class);
        arreter();
        String url2 = demarrer(409, "{\"code\":\"ESSAI_ABONNEMENT_ACTIF\"}", requete);
        assertThatThrownBy(() -> new HttpPurgeFacturationEssai(url2).traiter(ws)).isInstanceOf(RuntimeException.class);
    }
}
