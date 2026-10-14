package ma.jurika.auth.infrastructure.essai;

import ma.jurika.auth.domain.port.PurgeObjetsEssai;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Lot L7 (E4) : appel de {@code DELETE /internal/essais/{workspaceId}/objets}
 * de dataroom-service. Toute erreur (service injoignable, refus) remonte :
 * la suppression definitive s'arrete avant la base.
 */
@Component
public class HttpPurgeObjetsEssai implements PurgeObjetsEssai {

    private final RestClient client;

    public HttpPurgeObjetsEssai(@Value("${jurika.dataroom.internal-url:http://dataroom-service:8084}") String url) {
        this.client = RestClient.builder().baseUrl(url).build();
    }

    @Override
    public int purger(UUID workspaceId) {
        Map<?, ?> r = client.delete()
                .uri("/internal/essais/{ws}/objets", workspaceId)
                .retrieve()
                .body(Map.class);
        Object n = r == null ? null : r.get("objetsSupprimes");
        if (!(n instanceof Number nombre)) {
            throw new IllegalStateException("Reponse de dataroom-service sans nombre d'objets supprimes");
        }
        return nombre.intValue();
    }
}
