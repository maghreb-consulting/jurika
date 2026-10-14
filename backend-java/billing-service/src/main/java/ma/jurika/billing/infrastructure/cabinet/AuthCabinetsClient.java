package ma.jurika.billing.infrastructure.cabinet;

import ma.jurika.billing.application.cabinet.CabinetsPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lot L7 (E5) : {@link CabinetsPort} par appel HTTP direct aux routes internes d'auth-service
 * ({@code jurika.auth.internal-url}). Un echec leve
 * {@link CabinetsPort.CabinetsIndisponiblesException} : jamais avale.
 */
@Component
public class AuthCabinetsClient implements CabinetsPort {

    private final RestClient http;

    public AuthCabinetsClient(@Value("${jurika.auth.internal-url}") String authInternalUrl) {
        this.http = RestClient.builder().baseUrl(authInternalUrl).build();
    }

    @Override
    public void activer(UUID workspaceId, String forfait, LocalDate abonnementDebut, String rythme, LocalDate payeJusquAu) {
        String uri = UriComponentsBuilder.fromPath("/internal/workspaces/{id}/status")
                .queryParam("transition", "ACTIVATED")
                .queryParam("planCode", forfait)
                .queryParam("abonnementDebut", abonnementDebut)
                .queryParam("rythme", rythme)
                .queryParam("accessUntil", payeJusquAu.atStartOfDay(ma.jurika.common.billing.PeriodeAbonnement.CASABLANCA).toInstant())
                .buildAndExpand(workspaceId).toUriString();
        appeler("activation", () -> http.put().uri(uri).retrieve().toBodilessEntity());
    }

    @Override
    public void archiver(UUID workspaceId, String motif) {
        String uri = UriComponentsBuilder.fromPath("/internal/workspaces/{id}/status")
                .queryParam("transition", "ARCHIVED")
                .queryParam("motif", motif)
                .buildAndExpand(workspaceId).toUriString();
        appeler("archivage", () -> http.put().uri(uri).retrieve().toBodilessEntity());
    }

    @Override
    public void suspendreImpaye(UUID workspaceId) {
        String uri = UriComponentsBuilder.fromPath("/internal/workspaces/{id}/status")
                .queryParam("transition", "SUSPENDED_IMPAYE")
                .buildAndExpand(workspaceId).toUriString();
        appeler("suspension pour impaye", () -> http.put().uri(uri).retrieve().toBodilessEntity());
    }

    @Override
    public java.util.List<UUID> superviseurs(UUID workspaceId) {
        UUID[] l = appeler("superviseurs du cabinet", () -> http.get()
                .uri("/internal/workspaces/{id}/superviseurs", workspaceId).retrieve().body(UUID[].class));
        return l == null ? java.util.List.of() : java.util.List.of(l);
    }

    @Override
    public void emettreIdentifiants(UUID workspaceId, String raison) {
        String uri = UriComponentsBuilder.fromPath("/internal/workspaces/{id}/issue-credentials")
                .queryParam("reason", raison)
                .buildAndExpand(workspaceId).toUriString();
        appeler("emission des identifiants", () -> http.post().uri(uri).retrieve().toBodilessEntity());
    }

    @Override
    public IdentiteCabinet identite(UUID workspaceId) {
        return appeler("identite du cabinet", () -> http.get()
                .uri("/internal/workspaces/{id}/identite-facturation", workspaceId)
                .retrieve().body(IdentiteCabinet.class));
    }

    @Override
    public UsageCabinet usage(UUID workspaceId) {
        UsageCabinet u = appeler("usage du forfait", () -> http.get()
                .uri("/internal/workspaces/{id}/usage", workspaceId)
                .retrieve().body(UsageCabinet.class));
        if (u == null) {
            throw new CabinetsIndisponiblesException("Usage du forfait non mesure (controle des quotas coupe sur auth-service)", null);
        }
        return u;
    }

    @Override
    public java.util.List<SuperAdmin> superAdmins() {
        SuperAdmin[] l = appeler("super-admins", () -> http.get().uri("/internal/workspaces/super-admins")
                .retrieve().body(SuperAdmin[].class));
        return l == null ? java.util.List.of() : java.util.List.of(l);
    }

    private static <T> T appeler(String quoi, java.util.function.Supplier<T> appel) {
        try {
            return appel.get();
        } catch (RestClientException e) {
            throw new CabinetsIndisponiblesException("auth-service injoignable (" + quoi + ") : " + e.getMessage(), e);
        }
    }
}
