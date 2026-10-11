package ma.jurika.ai.document.corpus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lot L4b (E2, RG-DEM-01) : le referentiel des demarches du corpus charge, servi a
 * ticket-service qui l'importe (route interne, bloquee a la passerelle). Le corpus n'est
 * monte que dans ai-service : c'est le seul lecteur des classeurs.
 */
@RestController
@RequestMapping("/internal/corpus")
public class InternalReferentielDemarchesController {

    private final CorpusCharge corpus;
    private volatile ReferentielDemarches lu;

    public InternalReferentielDemarchesController(CorpusCharge corpus) {
        this.corpus = corpus;
    }

    @GetMapping("/demarches")
    public ReferentielDemarches demarches() {
        ReferentielDemarches r = lu;
        if (r == null) {
            synchronized (this) {
                if (lu == null) lu = LecteurDemarches.lire(corpus.racine());
                r = lu;
            }
        }
        return r;
    }
}
