package ma.jurika.ai.document;

import ma.jurika.ai.document.corpus.CorpusException;
import ma.jurika.ai.document.corpus.DictionnaireUnique;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lot L3 : regle des variables d'un acte (CLAUDE.md, 2026-10-09).
 *
 * <p>Chaque variable est INTERNE ou EXTERNE. Externe : donnee attendue d'un organisme
 * (numero RC, ICE, IF, TP, CNSS, date d'immatriculation, certificat negatif, depot au
 * greffe...). Interne : tout le reste.
 * <ul>
 *   <li>une variable interne manquante bloque la generation et la donnee est nommee ;</li>
 *   <li>seule une variable externe peut manquer : l'acte sort avec un marqueur visible,
 *       la plateforme reclame la donnee et l'acte se regenere quand elle arrive.</li>
 * </ul>
 *
 * <p>La liste des externes est explicite et versionnee
 * ({@code templates/v2/variables-externes.txt}, produite par
 * {@code scripts/l3/variables_externes.py}) : proposition a valider par le directeur.
 * Elle n'est jamais deduite a l'execution d'une ressemblance de noms. Section
 * {@code [corpus]} : noms du dictionnaire unique, controles au chargement du corpus
 * ({@link #verifierContre}) ; section {@code [hors_corpus]} : noms des gabarits du
 * classpath encore servis hors corpus.
 */
public final class ClassementVariables {

    public static final String RESSOURCE = "templates/v2/variables-externes.txt";

    private final Set<String> externesCorpus;
    private final Set<String> externesHorsCorpus;

    ClassementVariables(Set<String> externesCorpus, Set<String> externesHorsCorpus) {
        this.externesCorpus = Collections.unmodifiableSet(new LinkedHashSet<>(externesCorpus));
        this.externesHorsCorpus = Collections.unmodifiableSet(new LinkedHashSet<>(externesHorsCorpus));
    }

    /** Charge la liste versionnee du classpath. Ressource absente ou vide : erreur. */
    public static ClassementVariables charger() {
        try (InputStream in = ClassementVariables.class.getClassLoader().getResourceAsStream(RESSOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Liste des variables externes introuvable : " + RESSOURCE);
            }
            return lire(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Liste des variables externes illisible : " + RESSOURCE, e);
        }
    }

    static ClassementVariables lire(String texte) {
        Set<String> corpus = new TreeSet<>();
        Set<String> horsCorpus = new TreeSet<>();
        Set<String> courante = null;
        try (BufferedReader r = new BufferedReader(new java.io.StringReader(texte))) {
            String ligne;
            while ((ligne = r.readLine()) != null) {
                String l = ligne.trim();
                if (l.isEmpty() || l.startsWith("#")) {
                    continue;
                }
                if (l.equals("[corpus]")) {
                    courante = corpus;
                } else if (l.equals("[hors_corpus]")) {
                    courante = horsCorpus;
                } else if (courante == null) {
                    throw new IllegalStateException("Variable externe hors section : " + l);
                } else {
                    courante.add(normaliser(l));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (corpus.isEmpty()) {
            throw new IllegalStateException("Aucune variable externe dans la section [corpus] de " + RESSOURCE);
        }
        return new ClassementVariables(corpus, horsCorpus);
    }

    /** Nom sans {@code $}, en majuscules. */
    static String normaliser(String nom) {
        String n = nom.trim();
        if (n.startsWith("$")) {
            n = n.substring(1);
        }
        return n.toUpperCase(Locale.ROOT);
    }

    /** Variable externe (sinon interne). Le nom peut porter ou non le {@code $}. */
    public boolean estExterne(String nom) {
        String n = normaliser(nom);
        return externesCorpus.contains(n) || externesHorsCorpus.contains(n);
    }

    /**
     * Variable externe, alias du dictionnaire resolu : un modele qui emploie un alias
     * d'une variable externe la traite comme externe.
     */
    public boolean estExterne(String nom, DictionnaireUnique dictionnaire) {
        if (estExterne(nom)) {
            return true;
        }
        return dictionnaire != null && estExterne(dictionnaire.canonique(normaliser(nom)));
    }

    public Set<String> externesCorpus() {
        return externesCorpus;
    }

    public Set<String> externesHorsCorpus() {
        return externesHorsCorpus;
    }

    /**
     * Chaque nom de la section {@code [corpus]} doit etre connu du dictionnaire unique :
     * un nom inconnu (faute de frappe, variable retiree d'une nouvelle version du corpus)
     * serait une regle sans effet. Bloquant, comme les autres controles du corpus.
     */
    public void verifierContre(DictionnaireUnique dictionnaire) {
        List<String> inconnues = externesCorpus.stream()
                .filter(n -> !dictionnaire.variables().contains("$" + n))
                .toList();
        if (!inconnues.isEmpty()) {
            throw new CorpusException(RESSOURCE + " : variables externes inconnues du dictionnaire unique : "
                    + String.join(", ", inconnues));
        }
    }
}
