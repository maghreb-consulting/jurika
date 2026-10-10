package ma.jurika.ai.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.corpus.ChargeurCorpus;
import ma.jurika.ai.document.corpus.CorpusCharge;
import ma.jurika.ai.document.corpus.GabaritCorpus;
import ma.jurika.ai.document.corpus.RapportChargement;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L2, etape E7 : chargement du VRAI corpus (present sur le serveur, absent de
 * la CI et du depot public) et generation temoin avant / apres.
 *
 * <p>Racine : variable d'environnement {@code JURIKA_CORPUS_REEL}, sinon le corpus
 * de reference du Z440. Absent : le test est IGNORE (et le rapport de verification
 * le dit), jamais vert par defaut.
 *
 * <p>Temoin : pour chaque gabarit produit par les mappers (memes donnees que
 * DocumentVerificationIT), rendu depuis le classpath (moteur sans corpus) puis
 * avec le corpus ; texte compare, variables apparues / disparues, documents ecrits
 * dans {@code target/corpus-reel/temoin/} pour relecture. Le corpus n'est jamais
 * modifie (lecture seule).
 */
@EnabledIf("corpusReelPresent")
class CorpusReelIT {

    static final Path RACINE = Path.of(System.getenv().getOrDefault("JURIKA_CORPUS_REEL",
            "/home/jurika/corpus/CORPUS_2026-10-03"));
    static final Path SORTIE = Path.of("target/corpus-reel");
    static final Pattern VARIABLE = Pattern.compile("\\$[A-Z][A-Z0-9_]*");
    static final Pattern RESIDU_RENDU = Pattern.compile(
            "DICTIONNAIRE DES VARIABLES|[\\u25bc\\u25b2\\u25c7\\u25c6\\u25c8\\u21b3]|\\$[A-Z][A-Z0-9_]+|\\$\\{|\\{\\{");
    static final Pattern ACCOLADES = Pattern.compile("\\$\\{\\s*([^}\\n]*?)\\s*\\}");
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);

    static boolean corpusReelPresent() {
        return Files.isRegularFile(RACINE.resolve("INDEX_CORPUS.xlsx"));
    }

    @Test
    void charge_le_corpus_reel_et_compare_au_classpath() throws Exception {
        Files.createDirectories(SORTIE.resolve("temoin/classpath"));
        Files.createDirectories(SORTIE.resolve("temoin/corpus"));

        // 1. Chargement complet.
        CorpusCharge corpus = ChargeurCorpus.charger(RACINE);
        // Lot L3 : chaque variable externe de la liste versionnee existe dans le vrai dictionnaire.
        ma.jurika.ai.document.ClassementVariables.charger().verifierContre(corpus.dictionnaire());
        List<String> classpath = codesClasspath();
        List<String> horsCorpus = classpath.stream().filter(c -> corpus.gabarit(c).isEmpty()).sorted().toList();
        RapportChargement rapport = corpus.rapport(horsCorpus);
        JSON.writeValue(SORTIE.resolve("rapport-chargement.json").toFile(), rapport);

        // 2. Temoin : memes donnees, classpath contre corpus.
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        DocumentVerificationIT dv = new DocumentVerificationIT();
        dv.invokeLoad(loader);
        DocxTemplateEngine avant = new DocxTemplateEngine(loader, null, null);
        DocxTemplateEngine apres = new DocxTemplateEngine(loader, null, corpus);

        List<Map<String, Object>> temoin = new ArrayList<>();
        Set<String> vus = new TreeSet<>();
        for (DocumentVerificationIT.MapperBinding b : dv.buildBindings()) {
            Map<String, Object> racineFixture = dv.loadFixture(b.fixtureFile());
            for (String code : new TreeSet<>(b.mapper().supportedTemplates())) {
                if (!vus.add(code)) continue;
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("code", code);
                e.put("mapper", b.name());
                Map<String, Object> payload = racineFixture;
                if (b.fixtureKeyedByTemplate()) {
                    Object brut = DocumentVerificationIT.resoudreFixture(racineFixture, code);
                    if (!(brut instanceof Map<?, ?>)) {
                        e.put("statut", "SANS_DONNEES");
                        temoin.add(e);
                        continue;
                    }
                    @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) brut;
                    payload = m;
                }
                Map<String, Object> variables = b.mapper().map(code, payload);
                e.put("dans_corpus", corpus.gabarit(code).isPresent());
                DocumentResult ra = avant.generate(code, variables);
                Files.write(SORTIE.resolve("temoin/classpath/" + code + ".docx"), ra.bytes());
                Set<String> horsAvant = apres.codesServisHorsCorpus();
                DocumentResult rb;
                try {
                    rb = apres.generate(code, variables);
                } catch (RuntimeException ex) {
                    e.put("statut", "ECHEC_CORPUS");
                    e.put("erreur", ex.getClass().getSimpleName() + " : " + ex.getMessage());
                    temoin.add(e);
                    continue;
                }
                boolean servieParCorpus = apres.codesServisHorsCorpus().equals(horsAvant)
                        && !apres.codesServisHorsCorpus().contains(code);
                e.put("source_apres", servieParCorpus ? "corpus" : "classpath");
                Files.write(SORTIE.resolve("temoin/corpus/" + code + ".docx"), rb.bytes());
                List<String> la = lignes(ra.bytes());
                List<String> lb = lignes(rb.bytes());
                e.put("texte_identique", la.equals(lb));
                e.put("lignes_avant", la.size());
                e.put("lignes_apres", lb.size());
                e.put("lignes_retirees", difference(la, lb));
                e.put("lignes_ajoutees", difference(lb, la));
                Set<String> va = variablesGabarit(code, loader);
                Set<String> vb = corpus.gabarit(code).map(GabaritCorpus::variables).orElse(Set.of());
                e.put("variables_disparues", new TreeSet<>(difference(new ArrayList<>(va), new ArrayList<>(vb))));
                e.put("variables_apparues", new TreeSet<>(difference(new ArrayList<>(vb), new ArrayList<>(va))));
                e.put("manquantes_avant", ra.missingVariables());
                e.put("manquantes_apres", rb.missingVariables());
                List<String> residus = new ArrayList<>();
                for (String l : lb) {
                    Matcher m = RESIDU_RENDU.matcher(l);
                    if (m.find()) residus.add(m.group());
                }
                e.put("residus_apres", residus);
                e.put("statut", "OK");
                temoin.add(e);
            }
        }
        Map<String, Object> sortie = new LinkedHashMap<>();
        sortie.put("corpus", rapport.version());
        sortie.put("codes_rendus", temoin.size());
        sortie.put("servis_hors_corpus", new TreeSet<>(apres.codesServisHorsCorpus()));
        sortie.put("temoin", temoin);
        JSON.writeValue(SORTIE.resolve("temoin.json").toFile(), sortie);

        // 3. Criteres du lot : 222 modeles, aucune erreur bloquante (sinon exception
        //    ci-dessus), aucun gabarit non rendable, chaque gabarit du corpus produit.
        assertThat(rapport.modeles()).isEqualTo(222);
        assertThat(rapport.nonRendables()).isEmpty();
        assertThat(temoin).isNotEmpty();
        assertThat(temoin).filteredOn(t -> "ECHEC_CORPUS".equals(t.get("statut"))).isEmpty();
        // Aucun document produit ne garde un marqueur du moteur, une variable brute ou
        // la section de documentation des gabarits.
        assertThat(temoin).filteredOn(t -> t.get("residus_apres") instanceof List<?> l && !l.isEmpty())
                .as("residus dans les documents produits").isEmpty();
        for (String code : corpus.gabarits().keySet()) {
            assertThat(corpus.lireVerifie(code)).as(code).isNotEmpty();
        }
    }

    /** Variables $NOM du gabarit classpath (texte brut du modele, avant rendu). */
    private static Set<String> variablesGabarit(String code, TemplateManifestLoader loader) throws Exception {
        Resource res = classpathDe(code, loader);
        if (res == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        try (InputStream in = res.getInputStream();
             XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor x = new XWPFWordExtractor(doc)) {
            Matcher m = VARIABLE.matcher(x.getText());
            while (m.find()) out.add(m.group());
            // Gabarits classpath anciens : syntaxe ${NOM ...}, ramenee a $NOM.
            Matcher a = ACCOLADES.matcher(x.getText());
            while (a.find()) {
                out.add("$" + a.group(1).toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_")
                        .replaceAll("_+", "_").replaceAll("^_|_$", ""));
            }
        }
        return out;
    }

    private static Resource classpathDe(String code, TemplateManifestLoader loader) {
        var entree = loader.resolve(code);
        if (entree.isPresent() && entree.get().file() != null) {
            Resource r = new org.springframework.core.io.ClassPathResource("templates/docx/" + entree.get().file());
            if (r.exists()) return r;
        }
        Resource r = new org.springframework.core.io.ClassPathResource("templates/docx/" + code + ".docx");
        return r.exists() ? r : null;
    }

    private static List<String> codesClasspath() throws Exception {
        List<String> codes = new ArrayList<>();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:templates/docx/*.docx")) {
            String nom = r.getFilename();
            if (nom != null) codes.add(nom.substring(0, nom.length() - ".docx".length()));
        }
        return codes;
    }

    private static List<String> lignes(byte[] docx) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor x = new XWPFWordExtractor(doc)) {
            List<String> out = new ArrayList<>();
            for (String l : x.getText().split("\n")) {
                String t = l.strip().replaceAll("\\s+", " ");
                if (!t.isEmpty()) out.add(t);
            }
            return out;
        }
    }

    /** Elements de a absents de b (multiensemble, ordre de a). */
    private static List<String> difference(List<String> a, List<String> b) {
        Map<String, Integer> compte = new TreeMap<>();
        for (String s : b) compte.merge(s, 1, Integer::sum);
        List<String> out = new ArrayList<>();
        for (String s : a) {
            Integer n = compte.get(s);
            if (n == null || n == 0) out.add(s);
            else compte.put(s, n - 1);
        }
        return out;
    }
}
