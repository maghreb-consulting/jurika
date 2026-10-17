package ma.jurika.billing.api.editeur;

import ma.jurika.billing.application.editeur.EditeurParametresService;
import ma.jurika.billing.application.editeur.EditeurParametresService.MentionsLegales;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Lot L6b (E2) : mentions legales PUBLIQUES de l'editeur de JURIKA (pied de page de la vitrine),
 * lues dans {@code editeur_parametres} (saisies par le super-admin). Route anonyme, lecture seule,
 * cache court (5 minutes) : une correction du super-admin apparait vite.
 *
 * <p>Corps : {@code raisonSociale, formeJuridique, capitalSocial, siegeSocial, ville, rcNumero,
 * rcTribunal, ice, identifiantFiscal, taxeProfessionnelle, representantNom, representantQualite},
 * chacun nul tant qu'il n'est pas renseigne. Rien d'autre (ni TVA, ni historique, ni auteur).
 */
@RestController
@RequestMapping("/api/v1/public/editeur/mentions-legales")
public class MentionsLegalesPubliquesController {

    static final Duration CACHE = Duration.ofMinutes(5);

    private final EditeurParametresService service;

    public MentionsLegalesPubliquesController(EditeurParametresService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<MentionsLegales> lire() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE).cachePublic())
                .body(service.mentionsLegales());
    }
}
