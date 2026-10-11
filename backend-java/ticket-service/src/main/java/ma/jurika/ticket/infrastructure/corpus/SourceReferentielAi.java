package ma.jurika.ticket.infrastructure.corpus;

import ma.jurika.ticket.application.referentiel.ReferentielCorpus;
import ma.jurika.ticket.application.referentiel.SourceReferentiel;
import org.springframework.stereotype.Component;

/** Lot L4b (E2) : referentiel lu par ai-service dans le corpus monte. */
@Component
public class SourceReferentielAi implements SourceReferentiel {

    private final CorpusReferentielClient client;

    public SourceReferentielAi(CorpusReferentielClient client) {
        this.client = client;
    }

    @Override
    public ReferentielCorpus lire() {
        return client.demarches();
    }
}
