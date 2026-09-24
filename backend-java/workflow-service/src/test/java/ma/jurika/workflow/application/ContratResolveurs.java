package ma.jurika.workflow.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LE CONTRAT DE LA CHARGE UTILE, RELEVÉ SUR LES RÉSOLVEURS EUX-MÊMES.
 *
 * <p>Cette classe ne contient <b>aucune liste de clés</b>. Elle lit les trois
 * résolveurs d'{@code ai-service} — les seuls consommateurs de la charge utile —
 * et en extrait ce qu'ils lisent réellement.
 *
 * <p><b>Pourquoi pas une liste écrite à la main.</b> Le lot C corrige un défaut
 * dont la cause est exactement là : un constructeur avait perdu 29 clés, et rien
 * ne s'en était aperçu parce que rien ne confrontait ce qu'il produisait à ce que
 * le résolveur attendait. Une liste recopiée ici se désynchroniserait au premier
 * ajout de variable, et le contrôle redeviendrait décoratif.
 *
 * <p>Le relevé suit trois motifs, tous vérifiés sur le code en vigueur :
 *
 * <pre>
 *   Map&lt;String, Object&gt; societe = unwrap(asMap(safe.get("societe")));   -- liaison bloc
 *   List&lt;Map&lt;String, Object&gt;&gt; associes = asListOfMaps(safe.get("associes"));
 *   for (Map&lt;String, Object&gt; a : associes) { ... a.get("nom") ... }       -- liaison occurrence
 *   societe.get("denomination")                                            -- lecture
 * </pre>
 */
final class ContratResolveurs {

    /** Les trois résolveurs. Aucun autre ne consomme la charge utile de création. */
    private static final List<String> RESOLVEURS = List.of(
            "CreationDirecteurVarsBuilder.java",
            "CreationFormulairesVarsBuilder.java",
            "CreationCorpusVarsBuilder.java");

    /** {@code nom = asMap(safe.get("bloc"))} — y compris enveloppé d'{@code unwrap(…)}. */
    private static final Pattern LIAISON_BLOC = Pattern.compile(
            "(\\w+)\\s*=\\s*(?:unwrap\\(\\s*)?asMap\\(\\s*(?:safe|payload)\\.get\\(\\s*\"(\\w+)\"");

    /** {@code nom = asListOfMaps(safe.get("bloc"))}. */
    private static final Pattern LIAISON_LISTE = Pattern.compile(
            "(\\w+)\\s*=\\s*asListOfMaps\\(\\s*(?:safe|payload)\\.get\\(\\s*\"(\\w+)\"");

    /** {@code for (Map<String, Object> a : associes)} — l'occurrence hérite du bloc. */
    private static final Pattern LIAISON_OCCURRENCE = Pattern.compile(
            "for\\s*\\(\\s*Map<String,\\s*Object>\\s+(\\w+)\\s*:\\s*(\\w+)\\s*\\)");

    /** {@code societe.get("denomination")}. */
    private static final Pattern LECTURE = Pattern.compile(
            "(\\w+)\\.get\\(\\s*\"(\\w+)\"\\s*\\)");

    /** {@code safe.get("valeurNominalePart")} — valeur scalaire à la racine. */
    private static final Pattern RACINE = Pattern.compile(
            "(?:safe|payload)\\.get\\(\\s*\"(\\w+)\"\\s*\\)");

    /** blocs-objet : {@code societe} → {denomination, sigle, …}. */
    private final Map<String, Set<String>> parBloc = new TreeMap<>();
    /** blocs-liste : {@code associes} → {nom, prenom, …}. */
    private final Map<String, Set<String>> parListe = new TreeMap<>();
    /** valeurs scalaires lues directement à la racine. */
    private final Set<String> racines = new TreeSet<>();

    private ContratResolveurs() {
    }

    static ContratResolveurs relever() {
        Path dossier = localiserResolveurs();
        ContratResolveurs contrat = new ContratResolveurs();
        for (String fichier : RESOLVEURS) {
            Path p = dossier.resolve(fichier);
            if (!Files.isRegularFile(p)) {
                throw new IllegalStateException(
                        "Résolveur introuvable : " + p.toAbsolutePath()
                                + " — le contrat ne peut pas être relevé, et un contrôle "
                                + "de contrat qui ne trouve pas sa source ne doit JAMAIS "
                                + "passer en silence.");
            }
            contrat.analyser(lire(p));
        }
        if (contrat.parBloc.isEmpty() || contrat.parListe.isEmpty()) {
            throw new IllegalStateException(
                    "Relevé vide : les motifs d'extraction ne reconnaissent plus le code "
                            + "des résolveurs. Corriger ContratResolveurs avant de se fier "
                            + "au moindre vert.");
        }
        return contrat;
    }

    /**
     * Remonte l'arborescence depuis le répertoire courant jusqu'à trouver
     * {@code backend-java}, puis descend vers les résolveurs. Fonctionne que le
     * test soit lancé depuis le module, depuis {@code backend-java} ou depuis la
     * racine du dépôt.
     */
    private static Path localiserResolveurs() {
        Path courant = Path.of("").toAbsolutePath();
        for (Path p = courant; p != null; p = p.getParent()) {
            Path candidat = p.resolve("ai-service/src/main/java/ma/jurika/ai/workflow/mapper");
            if (Files.isDirectory(candidat)) return candidat;
            Path viaBackend = p.resolve("backend-java/ai-service/src/main/java/ma/jurika/ai/workflow/mapper");
            if (Files.isDirectory(viaBackend)) return viaBackend;
        }
        throw new IllegalStateException(
                "Répertoire des résolveurs introuvable depuis " + courant
                        + " — le contrat ne peut pas être relevé.");
    }

    private static String lire(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void analyser(String source) {
        Map<String, String> blocDuLocal = new LinkedHashMap<>();
        Map<String, String> listeDuLocal = new LinkedHashMap<>();

        Matcher m = LIAISON_BLOC.matcher(source);
        while (m.find()) blocDuLocal.put(m.group(1), m.group(2));

        m = LIAISON_LISTE.matcher(source);
        while (m.find()) listeDuLocal.put(m.group(1), m.group(2));

        // Une occurrence de boucle lit les clés de SON bloc-liste.
        m = LIAISON_OCCURRENCE.matcher(source);
        while (m.find()) {
            String liste = listeDuLocal.get(m.group(2));
            if (liste != null) listeDuLocal.put(m.group(1), liste);
        }

        m = LECTURE.matcher(source);
        while (m.find()) {
            String local = m.group(1);
            String cle = m.group(2);
            String bloc = blocDuLocal.get(local);
            if (bloc != null) {
                parBloc.computeIfAbsent(bloc, k -> new TreeSet<>()).add(cle);
                continue;
            }
            String liste = listeDuLocal.get(local);
            if (liste != null) {
                parListe.computeIfAbsent(liste, k -> new TreeSet<>()).add(cle);
            }
        }

        m = RACINE.matcher(source);
        while (m.find()) racines.add(m.group(1));
    }

    Map<String, Set<String>> blocs() {
        return parBloc;
    }

    Map<String, Set<String>> listes() {
        return parListe;
    }

    /** Racines qui ne sont ni un bloc-objet ni un bloc-liste : scalaires du payload. */
    Set<String> racinesScalaires() {
        Set<String> out = new LinkedHashSet<>(racines);
        out.removeAll(parBloc.keySet());
        out.removeAll(parListe.keySet());
        return out;
    }

    /** Tous les chemins du contrat, sous la forme {@code bloc.cle} / {@code liste[].cle}. */
    Set<String> chemins() {
        Set<String> out = new TreeSet<>();
        parBloc.forEach((bloc, cles) -> cles.forEach(c -> out.add(bloc + "." + c)));
        parListe.forEach((liste, cles) -> cles.forEach(c -> out.add(liste + "[]." + c)));
        racinesScalaires().forEach(out::add);
        return out;
    }
}
