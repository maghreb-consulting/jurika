package ma.jurika.auth.infrastructure.donneesdetest;

import ma.jurika.common.exception.ValidationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recette R1 : lecture, par les parcours de bout en bout, des messages envoyes a une identite de
 * test ({@code GET /api/v1/test/messages?adresse=...@jurika.test}).
 *
 * <p>Rend les courriels adresses a cette adresse (gabarit, sujet, variables : lien de
 * confirmation, code du cabinet, identifiant, mot de passe provisoire...) et les SMS envoyes aux
 * telephones saisis dans une demande d'essai faite avec cette adresse (code de verification).
 * Toute autre adresse est refusee : aucun message d'une personne reelle n'est lisible.
 *
 * <p>Double verrou (comme TestSeedController de dataroom) : absent en profil {@code prod}, et
 * seulement si {@code jurika.test.seed.enabled=true}. La passerelle ne route
 * {@code /api/v1/test/messages/**} vers auth-service que sous les memes conditions
 * (RouteDonneesDeTestConfig).
 */
@RestController
@RequestMapping("/api/v1/test/messages")
@Profile("!prod")
@ConditionalOnProperty(name = "jurika.test.seed.enabled", havingValue = "true", matchIfMissing = false)
public class MessagesDeTestController {

    private final CaptureMessagesDeTest capture;
    private final JdbcTemplate jdbc;

    public MessagesDeTestController(CaptureMessagesDeTest capture, JdbcTemplate jdbc) {
        this.capture = capture;
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> messages(@RequestParam String adresse) {
        if (!CaptureMessagesDeTest.adresseDeTest(adresse)) {
            throw new ValidationException("Seules les adresses du domaine de test ("
                    + CaptureMessagesDeTest.DOMAINE_DE_TEST + ") sont lisibles.");
        }
        String cle = adresse.trim().toLowerCase(Locale.ROOT);
        List<String> telephones = jdbc.queryForList(
                "SELECT DISTINCT telephone FROM essai_demandes WHERE lower(email) = ?", String.class, cle);
        List<CaptureMessagesDeTest.Sms> sms = new ArrayList<>();
        for (String t : telephones) sms.addAll(capture.smsPour(t));
        sms.sort((a, b) -> a.envoyeLe().compareTo(b.envoyeLe()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("adresse", cle);
        r.put("courriels", capture.courrielsPour(cle));
        r.put("sms", sms);
        return r;
    }
}
