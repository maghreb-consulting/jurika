package ma.jurika.ticket.application.referentiel;

/** Lot L4b (E2) : d'ou vient le referentiel a importer (ai-service en production). */
public interface SourceReferentiel {

    ReferentielCorpus lire();
}
