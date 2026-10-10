package ma.jurika.ai.document.corpus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Lot L2 : charge le corpus date au demarrage. {@code jurika.corpus.root} est
 * OBLIGATOIRE, sans valeur de repli (motif 9 : un repli masquerait un corpus
 * absent) ; une erreur bloquante du corpus empeche le service de demarrer.
 */
@Configuration
public class CorpusConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CorpusConfiguration.class);

    @Bean
    public CorpusCharge corpusCharge(@Value("${jurika.corpus.root}") String racine) {
        if (racine == null || racine.isBlank()) {
            throw new CorpusException("jurika.corpus.root est vide : le corpus est obligatoire");
        }
        CorpusCharge corpus = ChargeurCorpus.charger(Path.of(racine));
        RapportChargement r = corpus.rapport(java.util.List.of());
        log.info("Corpus {} charge depuis {} : {} modeles, {} variables, {} alias, {} non rendables, "
                        + "{} modeles avec avertissements",
                r.version(), r.racine(), r.modeles(), r.variables(), r.alias(),
                r.nonRendables().size(), r.avertissements().size());
        r.nonRendables().forEach((code, erreurs) ->
                log.warn("Corpus {} : modele {} NON RENDABLE : {}", r.version(), code, erreurs));
        return corpus;
    }

    /**
     * Lot L3 : classement interne / externe des variables, controle contre le dictionnaire
     * du corpus charge (un nom inconnu empeche le demarrage, comme les autres controles).
     */
    @Bean
    public ma.jurika.ai.document.ClassementVariables classementVariables(CorpusCharge corpus) {
        ma.jurika.ai.document.ClassementVariables c = ma.jurika.ai.document.ClassementVariables.charger();
        c.verifierContre(corpus.dictionnaire());
        log.info("Classement des variables : {} externes du corpus, {} hors corpus ; toutes les autres sont internes",
                c.externesCorpus().size(), c.externesHorsCorpus().size());
        return c;
    }
}
