package ma.jurika.ai.api;

import ma.jurika.ai.document.corpus.CorpusCharge;
import ma.jurika.ai.document.corpus.RapportChargement;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lot L2 : rapport de chargement du corpus (version, modeles, empreintes,
 * gabarits non rendables, avertissements, gabarits du classpath sans equivalent
 * dans le corpus). Reserve au super administrateur.
 */
@RestController
@RequestMapping("/api/v1/ai/corpus")
public class CorpusController {

    private final CorpusCharge corpus;

    public CorpusController(CorpusCharge corpus) {
        this.corpus = corpus;
    }

    @GetMapping("/rapport")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public RapportChargement rapport() {
        return corpus.rapport(gabaritsClasspathHorsCorpus());
    }

    /**
     * Codes des gabarits du classpath ({@code templates/docx/}) absents du corpus :
     * servis depuis le classpath en attendant la decision du directeur (A_DECIDER).
     */
    List<String> gabaritsClasspathHorsCorpus() {
        try {
            List<String> codes = new ArrayList<>();
            for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:templates/docx/*.docx")) {
                String nom = r.getFilename();
                if (nom != null) codes.add(nom.substring(0, nom.length() - ".docx".length()));
            }
            return horsCorpus(codes, corpus);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Codes absents du corpus, tries. */
    static List<String> horsCorpus(List<String> codesClasspath, CorpusCharge corpus) {
        return codesClasspath.stream().filter(c -> corpus.gabarit(c).isEmpty()).sorted().toList();
    }
}
