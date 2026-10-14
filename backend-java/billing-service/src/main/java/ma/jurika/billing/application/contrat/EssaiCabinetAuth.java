package ma.jurika.billing.application.contrat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lot L7 (E13) : lit chez auth-service ({@code GET /api/v1/workspace/essai/acceptation}, en
 * direct sur le reseau interne, avec le jeton du superviseur) la version et l'instant
 * d'acceptation des conditions de l'essai gratuit du cabinet. Reponse attendue :
 * {@code {"essai": false}} ou {@code {"essai": true, "version": "...", "accepteesLe": "<ISO-8601>"}}.
 *
 * <p>Auth injoignable, reponse en erreur, illisible ou incomplete : refus nomme (503), jamais
 * "pas d'essai" par defaut (motif 9) : le contrat n'est ni presente ni accepte sans savoir.
 *
 * <p>Adresse : {@code jurika.contrat.auth-url} (variable {@code JURIKA_CONTRAT_AUTH_URL}), la
 * meme que pour l'identite du cabinet ({@link IdentiteCabinetAuth}).
 */
@Component
public class EssaiCabinetAuth implements EssaiCabinetSource {

    private static final ParameterizedTypeReference<Map<String, Object>> CARTE = new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public EssaiCabinetAuth(@Value("${jurika.contrat.auth-url:http://auth-service:8081}") String authUrl) {
        this.http = RestClient.builder().baseUrl(authUrl).build();
    }

    static ContratException indisponible() {
        return new ContratException(HttpStatus.SERVICE_UNAVAILABLE, "ESSAI_INDISPONIBLE",
                "L\u2019acceptation des conditions de l\u2019essai gratuit du cabinet n\u2019a pas pu \u00eatre lue "
                        + "(service des comptes injoignable ou r\u00e9ponse incompl\u00e8te) : le contrat, qui la mentionne, "
                        + "ne peut pas \u00eatre pr\u00e9sent\u00e9 ni accept\u00e9 sans elle. R\u00e9essayez dans quelques instants.",
                List.of("acceptation des conditions de l\u2019essai gratuit"));
    }

    @Override
    public Optional<AcceptationEssai> lire(String jetonPorteur) {
        if (jetonPorteur == null || jetonPorteur.isBlank()) {
            throw ContratException.invalide("JETON_ABSENT", "Session absente : reconnectez-vous.");
        }
        Map<String, Object> r;
        try {
            r = http.get().uri("/api/v1/workspace/essai/acceptation")
                    .header(HttpHeaders.AUTHORIZATION, jetonPorteur).retrieve().body(CARTE);
        } catch (RestClientException ex) {
            throw indisponible();
        }
        if (r == null || !(r.get("essai") instanceof Boolean essai)) {
            throw indisponible();
        }
        if (!essai) {
            return Optional.empty();
        }
        Object version = r.get("version");
        Object le = r.get("accepteesLe");
        if (!(version instanceof String v) || v.isBlank() || !(le instanceof String l)) {
            throw indisponible();
        }
        try {
            return Optional.of(new AcceptationEssai(v.trim(), Instant.parse(l)));
        } catch (DateTimeParseException ex) {
            throw indisponible();
        }
    }
}
