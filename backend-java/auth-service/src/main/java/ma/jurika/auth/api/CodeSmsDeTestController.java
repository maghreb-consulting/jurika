package ma.jurika.auth.api;

import ma.jurika.auth.infrastructure.sms.BoiteSmsDeTest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recette R2 (2026-10-17) : route reservee a l'environnement de test,
 * {@code GET /api/v1/test/auth-2fa-sms/dernier-code?telephone=+212...}, qui rend le dernier code
 * SMS SIMULE de la double authentification envoye a ce numero (parcours de bout en bout).
 * Double verrou : jurika.sms.simulation=true ET hors profil prod (la passerelle ne route
 * /api/v1/test/** que hors prod avec jurika.test.seed.enabled=true). Absente du fichier de
 * production (ProductionSansSimulationSmsTest). Le code n'est jamais renvoye ailleurs.
 */
@RestController
@RequestMapping("/api/v1/test/auth-2fa-sms")
@Profile("!prod")
@ConditionalOnProperty(name = "jurika.sms.simulation", havingValue = "true", matchIfMissing = false)
public class CodeSmsDeTestController {

    private final BoiteSmsDeTest boite;

    public CodeSmsDeTestController(BoiteSmsDeTest boite) {
        this.boite = boite;
    }

    @GetMapping(value = "/dernier-code", produces = "application/json")
    public ResponseEntity<BoiteSmsDeTest.CodeDepose> dernierCode(@RequestParam("telephone") String telephone) {
        // Un "+" non encode dans l'adresse arrive en espace : il est retabli.
        String numero = telephone.trim().replace(" ", "");
        if (!numero.startsWith("+")) numero = "+" + numero;
        return boite.dernier(numero)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
