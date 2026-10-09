package ma.jurika.ai.document.corpus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Lot L2 : corpus charge et controle. Les gabarits sont relus a chaque rendu, et
 * leur empreinte comparee a celle du chargement (P8 : modeles intouchables) ; un
 * gabarit modifie depuis le chargement, ou de structure defaillante, est refuse.
 */
public final class CorpusCharge {

    private final String version;
    private final Path racine;
    private final Instant chargeLe;
    private final DictionnaireUnique dictionnaire;
    private final Map<String, GabaritCorpus> gabarits;

    CorpusCharge(String version, Path racine, Instant chargeLe, DictionnaireUnique dictionnaire,
                 Map<String, GabaritCorpus> gabarits) {
        this.version = version;
        this.racine = racine;
        this.chargeLe = chargeLe;
        this.dictionnaire = dictionnaire;
        this.gabarits = Collections.unmodifiableMap(new LinkedHashMap<>(gabarits));
    }

    public String version() {
        return version;
    }

    public DictionnaireUnique dictionnaire() {
        return dictionnaire;
    }

    public Map<String, GabaritCorpus> gabarits() {
        return gabarits;
    }

    public Optional<GabaritCorpus> gabarit(String code) {
        return Optional.ofNullable(gabarits.get(code));
    }

    /**
     * Contenu du gabarit, apres verification de sa structure et de son empreinte.
     *
     * @throws CorpusException gabarit inconnu, non rendable, illisible ou modifie
     */
    public byte[] lireVerifie(String code) {
        GabaritCorpus g = gabarits.get(code);
        if (g == null) {
            throw new CorpusException("Modele absent du corpus " + version + " : " + code);
        }
        if (!g.rendable()) {
            throw new CorpusException("Modele " + code + " non rendable (structure) : "
                    + String.join(" ; ", g.erreursStructure()));
        }
        byte[] contenu;
        try {
            contenu = Files.readAllBytes(g.fichier());
        } catch (IOException ex) {
            throw new CorpusException("Gabarit illisible : " + g.fichier(), ex);
        }
        String empreinte = ChargeurCorpus.sha256(contenu);
        if (!empreinte.equals(g.empreinte())) {
            throw new CorpusException("Gabarit modifie depuis le chargement du corpus (P8) : " + code
                    + " (empreinte attendue " + g.empreinte() + ", lue " + empreinte + ")");
        }
        return contenu;
    }

    public RapportChargement rapport(List<String> horsCorpusClasspath) {
        Map<String, List<String>> nonRendables = new TreeMap<>();
        Map<String, List<String>> avertissements = new TreeMap<>();
        Map<String, String> empreintes = new TreeMap<>();
        for (GabaritCorpus g : gabarits.values()) {
            String code = g.modele().code();
            empreintes.put(code, g.empreinte());
            if (!g.rendable()) nonRendables.put(code, g.erreursStructure());
            if (!g.avertissements().isEmpty()) avertissements.put(code, g.avertissements());
        }
        return new RapportChargement(version, racine.toString(), chargeLe, gabarits.size(),
                dictionnaire.variables().size(), dictionnaire.alias().size(), nonRendables,
                avertissements, empreintes, new ArrayList<>(horsCorpusClasspath));
    }
}
