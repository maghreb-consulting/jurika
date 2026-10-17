package ma.jurika.billing.api;

import ma.jurika.billing.application.email.BillingEmailService;
import ma.jurika.billing.application.email.ExemplairesCourrielsFacturation;
import ma.jurika.common.courriel.CourrielJurika;
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
 * Recette R3 : envoi d'UN exemplaire du courriel de facture, rendu avec des donnees fictives
 * explicites et son PDF de facture fictif en piece jointe ({@link ExemplairesCourrielsFacturation}),
 * a l'adresse donnee, par le relais configure (Brevo).
 *
 * <p>{@code POST /api/v1/test/courriels/facturation} corps {@code {"destinataire": "adresse"}}.
 * Reservee a l'environnement de test, double verrou comme le seed de dataroom : interrupteur
 * {@code jurika.test.seed.enabled=true} (JURIKA_TEST_SEED_ENABLED) ET profil autre que prod.
 */
@RestController
@RequestMapping("/api/v1/test/courriels")
@Profile("!prod")
@ConditionalOnProperty(name = "jurika.test.seed.enabled", havingValue = "true", matchIfMissing = false)
public class CourrielsExemplairesFacturationTestController {

    private static final Logger log = LoggerFactory.getLogger(CourrielsExemplairesFacturationTestController.class);
    private static final Pattern ADRESSE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final BillingEmailService courriels;

    public CourrielsExemplairesFacturationTestController(BillingEmailService courriels) {
        this.courriels = courriels;
    }

    @PostMapping("/facturation")
    public ResponseEntity<Map<String, Object>> envoyer(@RequestBody Map<String, String> corps) {
        String destinataire = corps == null ? null : corps.get("destinataire");
        if (destinataire == null || !ADRESSE.matcher(destinataire.trim()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("erreur", "Adresse du destinataire absente ou invalide"));
        }
        String a = destinataire.trim();
        List<Map<String, Object>> envois = new ArrayList<>();
        for (ExemplairesCourrielsFacturation.Exemplaire e : ExemplairesCourrielsFacturation.tous()) {
            String erreur = courriels.envoyer(a, e.objet(), e.gabarit(), e.variables(), e.pieces());
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("exemplaire", e.code());
            ligne.put("objet", e.objet());
            ligne.put("gabarit", e.gabarit());
            ligne.put("piecesJointes", e.pieces().stream().map(CourrielJurika.PieceJointe::nom).toList());
            ligne.put("envoye", erreur == null);
            if (erreur != null) ligne.put("erreur", erreur);
            envois.add(ligne);
        }
        log.info("Recette R3 : exemplaire du courriel de facture envoye a {}", a);
        return ResponseEntity.ok(Map.of("destinataire", a, "envois", envois));
    }
}
