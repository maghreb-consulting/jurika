package ma.jurika.auth.infrastructure.essai;

import ma.jurika.auth.domain.port.PurgeFacturationEssai;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * Lot L7 (E14) : appel de {@code DELETE /internal/essais/{workspaceId}/paiements} de
 * billing-service. Toute erreur (service injoignable, refus) remonte : la suppression definitive
 * s'arrete avant MinIO et la base. Une reponse sans bilan est une erreur (jamais lue comme "rien").
 */
@Component
public class HttpPurgeFacturationEssai implements PurgeFacturationEssai {

    private final RestClient client;

    public HttpPurgeFacturationEssai(@Value("${jurika.billing.internal-url:http://billing-service:8090}") String url) {
        this.client = RestClient.builder().baseUrl(url).build();
    }

    @Override
    public Bilan traiter(UUID workspaceId) {
        Bilan b = client.delete()
                .uri("/internal/essais/{ws}/paiements", workspaceId)
                .retrieve()
                .body(Bilan.class);
        if (b == null || !workspaceId.equals(b.workspaceId()) || b.supprimees() == null || b.conservees10Ans() == null || b.conserveesDureeADecider() == null) {
            throw new IllegalStateException("Reponse de billing-service sans bilan de la suppression de l'essai");
        }
        return b;
    }
}
