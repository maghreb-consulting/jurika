package ma.jurika.ticket.infrastructure.corpus;

import ma.jurika.ticket.application.referentiel.ReferentielCorpus;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** Lot L4b (E2) : le corpus n'est monte que dans ai-service, seul lecteur des matrices. */
@FeignClient(name = "ai-service", contextId = "corpusReferentiel")
public interface CorpusReferentielClient {

    @GetMapping("/internal/corpus/demarches")
    ReferentielCorpus demarches();
}
