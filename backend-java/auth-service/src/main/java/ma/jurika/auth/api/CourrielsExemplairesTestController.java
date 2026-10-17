package ma.jurika.auth.api;

import ma.jurika.auth.application.ExemplairesCourriels;
import ma.jurika.auth.domain.port.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Recette R3 : envoi d'UN exemplaire de chaque courriel d'auth-service soumis a la recette
 * (bienvenue, identifiants d'un employe, identifiants d'un client, reinitialisation du mot de
 * passe, notification au client), avec des donnees fictives explicites
 * ({@link ExemplairesCourriels}), a l'adresse donnee, par le relais configure (Brevo).
 *
 * <p>{@code POST /api/v1/test/courriels/auth} corps {@code {"destinataire": "adresse"}}.
 * Reservee a l'environnement de test, double verrou comme le seed de dataroom : interrupteur
 * {@code jurika.test.seed.enabled=true} (JURIKA_TEST_SEED_ENABLED) ET profil autre que prod.
 * La reponse dit, pour chaque exemplaire, s'il est parti ou pourquoi il ne l'est pas.
 */
@RestController
@RequestMapping("/api/v1/test/courriels")
@Profile("!prod")
@ConditionalOnProperty(name = "jurika.test.seed.enabled", havingValue = "true", matchIfMissing = false)
public class CourrielsExemplairesTestController {

    private static final Logger log = LoggerFactory.getLogger(CourrielsExemplairesTestController.class);
    private static final Pattern ADRESSE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final EmailSender emailSender;

    public CourrielsExemplairesTestController(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> envoyer(@RequestBody Map<String, String> corps) {
        String destinataire = corps == null ? null : corps.get("destinataire");
        if (destinataire == null || !ADRESSE.matcher(destinataire.trim()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("erreur", "Adresse du destinataire absente ou invalide"));
        }
        String a = destinataire.trim();
        List<Map<String, Object>> envois = new ArrayList<>();
        for (ExemplairesCourriels.Exemplaire e : ExemplairesCourriels.tous()) {
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("exemplaire", e.code());
            ligne.put("objet", e.objet());
            ligne.put("gabarit", e.gabarit());
            try {
                emailSender.sendTemplated(a, e.objet(), e.gabarit(), e.variables());
                ligne.put("envoye", true);
            } catch (RuntimeException ex) {
                ligne.put("envoye", false);
                ligne.put("erreur", String.valueOf(ex.getMessage()));
            }
            envois.add(ligne);
        }
        log.info("Recette R3 : exemplaires des courriels d'auth envoyes a {} ({} envois)", a, envois.size());
        return ResponseEntity.ok(Map.of("destinataire", a, "envois", envois));
    }
}
