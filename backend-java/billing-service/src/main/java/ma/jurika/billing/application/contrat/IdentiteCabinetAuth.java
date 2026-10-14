package ma.jurika.billing.application.contrat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Lot L7 (E6) : lit l'identite du cabinet ({@code GET /api/v1/workspace/profile}) et du
 * signataire ({@code GET /api/v1/auth/me}) chez auth-service, en direct sur le reseau interne,
 * avec le jeton de l'utilisateur. Un echec est un refus nomme (503), jamais un contrat incomplet
 * en silence.
 *
 * <p>Adresse : {@code jurika.contrat.auth-url} (variable {@code JURIKA_CONTRAT_AUTH_URL}),
 * par defaut le nom du service sur le reseau Docker.
 */
@Component
public class IdentiteCabinetAuth implements IdentiteCabinetSource {

    private static final ParameterizedTypeReference<Map<String, Object>> CARTE = new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public IdentiteCabinetAuth(@Value("${jurika.contrat.auth-url:http://auth-service:8081}") String authUrl) {
        this.http = RestClient.builder().baseUrl(authUrl).build();
    }

    @Override
    public Identite lire(String jetonPorteur) {
        if (jetonPorteur == null || jetonPorteur.isBlank()) {
            throw ContratException.invalide("JETON_ABSENT", "Session absente : reconnectez-vous.");
        }
        try {
            Map<String, Object> profil = http.get().uri("/api/v1/workspace/profile")
                    .header(HttpHeaders.AUTHORIZATION, jetonPorteur).retrieve().body(CARTE);
            Map<String, Object> moi = http.get().uri("/api/v1/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, jetonPorteur).retrieve().body(CARTE);
            if (profil == null || moi == null) {
                throw new RestClientException("reponse vide");
            }
            String compte = texte(moi.get("loginEmail"));
            if (compte == null) compte = texte(moi.get("email"));
            return new Identite(texte(profil.get("name")), texte(profil.get("adresse")), texte(profil.get("rcNumber")),
                    texte(profil.get("ice")), texte(profil.get("contactEmail")), texte(moi.get("lastName")),
                    texte(moi.get("firstName")), compte);
        } catch (RestClientException ex) {
            throw new ContratException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "IDENTITE_INDISPONIBLE",
                    "L\u2019identit\u00e9 du cabinet n\u2019a pas pu \u00eatre lue (service des comptes injoignable). "
                            + "R\u00e9essayez dans quelques instants.", java.util.List.of());
        }
    }

    private static String texte(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
