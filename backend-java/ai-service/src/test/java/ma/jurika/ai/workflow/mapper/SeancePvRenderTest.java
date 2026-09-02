package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.manifest.TemplateDefaultsApplier;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 — Rendu bout-en-bout des 4 PV de séance (incident) transverses via
 * {@link IncidentSeanceMapper} + {@link SeancePvVarsBuilder} + {@link DocxTemplateEngine}.
 *
 * <p>Assertions : couverture 100 % ({@code missingVariables()} vide), 0 marqueur résiduel
 * ({@code $NU}, {@code ◇◆▼▲}, « VALEUR MANQUANTE »), et présence des mentions clés
 * (défaut de quorum / irrégularité / associés / résolutions).
 */
class SeancePvRenderTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static DocxTemplateEngine engine;
    private final IncidentSeanceMapper mapper = new IncidentSeanceMapper();

    private static final Pattern BARE_VAR = Pattern.compile("\\$[A-Z][A-Z0-9_]{2,}");

    private static synchronized DocxTemplateEngine engine() {
        if (engine == null) {
            TemplateManifestLoader loader = new TemplateManifestLoader(OM);
            loader.load();
            engine = new DocxTemplateEngine(loader, new TemplateDefaultsApplier(loader));
        }
        return engine;
    }

    private RenderResult render(String code, Map<String, Object> payload) {
        Map<String, Object> vars = mapper.map(code, payload);
        DocumentResult res = engine().generate(code, vars);
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(res.bytes()))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                for (XWPFRun r : p.getRuns()) {
                    String t = r.text();
                    if (t != null) sb.append(t);
                }
                sb.append('\n');
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new RenderResult(res, sb.toString());
    }

    private record RenderResult(DocumentResult res, String text) {}

    private void assertClean(String code, RenderResult r) {
        // Grammaire d'assemblage (2026-08-17) — la non-vacuité ne dit rien de la SOUDURE
        // entre le texte du modèle et la valeur injectée (« à au siège social »,
        // « DE le gérant unique », « de droit Allemagne »). On la vérifie ici aussi.
        java.util.List<String> assemblage__ =
                ma.jurika.ai.document.format.AssemblageFautif.dans(r.text());
        assertTrue(assemblage__.isEmpty(), code + " : assemblage fautif -> " + assemblage__);
        assertTrue(r.res().missingVariables().isEmpty(),
                code + " : variables NON résolues = " + r.res().missingVariables());
        assertFalse(BARE_VAR.matcher(r.text()).find(), code + " : marqueur $VAR résiduel.");
        for (String marker : new String[]{"◇", "◆", "▼", "▲", "VALEUR MANQUANTE"}) {
            assertFalse(r.text().contains(marker), code + " : marqueur résiduel « " + marker + " ».");
        }
    }

    @SafeVarargs
    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    private static Map<String, Object> societe() {
        return m("denomination", "PARACOSME", "capitalChiffres", 100000L, "adresseSiege",
                "101 bd Zerktouni, Casablanca", "nombreParts", 1000L, "rcNumero", "123456",
                "villeGreffe", "Casablanca");
    }

    private static Map<String, Object> seance() {
        return m("type", "ordinaire", "date", "2026-03-15", "heure", "10 heures",
                "lieu", "au siège social", "heureCloture", "11 heures",
                "presidentNom", "M. Yassine BENANI", "presidentQualite", "gérant",
                "secretairePresent", "oui", "secretaireNom", "Mme Salma IDRISSI");
    }

    private static Map<String, Object> convocation(boolean irreg) {
        Map<String, Object> c = m("auteur", "la gérance", "date", "2026-03-01",
                "mode", "lettre recommandée avec accusé de réception");
        if (irreg) c.put("irregulariteNature", "convocation adressée hors délai légal de 15 jours");
        return c;
    }

    private static Map<String, Object> secondeAssemblee() {
        return m("date", "2026-03-30", "heure", "10 heures", "lieu", "au siège social");
    }

    private static List<Map<String, Object>> resolutions() {
        return List.of(m("intitule", "Approbation des comptes", "texte",
                "L'assemblée approuve les comptes de l'exercice.", "voixPour", "1000",
                "voixContre", "0", "abstentions", "0", "resultat", "adoptée"));
    }

    private Map<String, Object> basePayload(boolean irreg) {
        Map<String, Object> p = m(
                "societe", societe(), "seance", seance(), "convocation", convocation(irreg),
                "secondeAssemblee", secondeAssemblee(), "suiteAssemblee", "renvoi",
                "ordreDuJour", List.of("Approbation des comptes annuels", "Affectation du résultat"),
                "resolutions", resolutions());
        return p;
    }

    @Test
    void pv_defaut_quorum_sarl() {
        Map<String, Object> payload = basePayload(false);
        payload.put("associes", List.of(
                m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom", "Yassine", "nom", "BENANI",
                        "nombreParts", 600L, "presence", "présent"),
                m("typePersonne", "PHYSIQUE", "civilite", "Mme", "prenom", "Salma", "nom", "IDRISSI",
                        "nombreParts", 400L, "presence", "représenté", "mandataireNom", "M. BENANI")));
        RenderResult r = render("PV_DEFAUT_QUORUM_SARL", payload);
        assertClean("PV_DEFAUT_QUORUM_SARL", r);
        assertTrue(r.text().contains("PARACOSME"), "dénomination absente");
        assertTrue(r.text().contains("BENANI") && r.text().contains("IDRISSI"), "associés absents");
    }

    @Test
    void pv_defaut_quorum_sarl_au() {
        Map<String, Object> payload = basePayload(false);
        payload.put("associeUnique", m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom", "Omar",
                "nom", "CHERKAOUI", "nationalite", "marocaine", "adresse", "12 rue X",
                "pieceType", "CIN", "pieceNumero", "BK12345"));
        payload.put("gerants", List.of(m("civilite", "M", "prenom", "Omar", "nom", "CHERKAOUI")));
        RenderResult r = render("PV_DEFAUT_QUORUM_SARL_AU", payload);
        assertClean("PV_DEFAUT_QUORUM_SARL_AU", r);
        assertTrue(r.text().contains("CHERKAOUI"), "associé unique absent");
    }

    @Test
    void pv_irregularite_convocation_sarl() {
        Map<String, Object> payload = basePayload(true);
        payload.put("associes", List.of(
                m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom", "Yassine", "nom", "BENANI",
                        "nombreParts", 600L, "presence", "présent"),
                m("typePersonne", "PHYSIQUE", "civilite", "Mme", "prenom", "Salma", "nom", "IDRISSI",
                        "nombreParts", 400L, "presence", "absent")));
        RenderResult r = render("PV_IRREGULARITE_CONVOCATION_SARL", payload);
        assertClean("PV_IRREGULARITE_CONVOCATION_SARL", r);
        assertTrue(r.text().toLowerCase().contains("irrégularité")
                || r.text().toLowerCase().contains("irregularite")
                || r.text().contains("délai"), "mention irrégularité absente");
    }

    @Test
    void pv_irregularite_convocation_sarl_au() {
        Map<String, Object> payload = basePayload(true);
        payload.put("associeUnique", m("typePersonne", "MORALE", "denomination", "GROUPE MAGHREB",
                "forme", "SA", "capital", "1 000 000 DH", "siege", "Rabat", "rcVille", "Rabat",
                "rcNumero", "98765", "representantNom", "M. Alaoui", "representantQualite", "Président"));
        payload.put("gerants", List.of(m("civilite", "Mme", "prenom", "Nadia", "nom", "ALAMI")));
        RenderResult r = render("PV_IRREGULARITE_CONVOCATION_SARL_AU", payload);
        assertClean("PV_IRREGULARITE_CONVOCATION_SARL_AU", r);
        assertTrue(r.text().contains("GROUPE MAGHREB"), "associé unique PM absent");
    }

    @Test
    void genererEchantillons() {
        java.nio.file.Path out = java.nio.file.Path.of("target", "echantillons-pv-seance");
        try {
            java.nio.file.Files.createDirectories(out);
            Map<String, Object> sarl = basePayload(false);
            sarl.put("associes", List.of(
                    m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom", "Yassine", "nom", "BENANI",
                            "nombreParts", 600L, "presence", "présent"),
                    m("typePersonne", "PHYSIQUE", "civilite", "Mme", "prenom", "Salma", "nom", "IDRISSI",
                            "nombreParts", 400L, "presence", "absent")));
            for (String code : new String[]{"PV_DEFAUT_QUORUM_SARL", "PV_IRREGULARITE_CONVOCATION_SARL"}) {
                DocumentResult res = engine().generate(code, mapper.map(code, sarl));
                java.nio.file.Files.write(out.resolve(code + ".docx"), res.bytes());
            }
        } catch (Exception ex) {
            System.out.println("[echantillons PV] ignoré : " + ex.getMessage());
        }
    }
}
