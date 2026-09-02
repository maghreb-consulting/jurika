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
 * Phase B — Rendu bout-en-bout des 2 PV d'approbation des comptes via
 * {@link ApprobationComptesMapper} + {@link SeancePvVarsBuilder} + {@link DocxTemplateEngine}.
 *
 * <p>Assertions : couverture 100 % ({@code missingVariables()} vide), 0 marqueur résiduel
 * ({@code $VAR}, {@code ◇◆▼▲}, « VALEUR MANQUANTE »), et présence des mentions clés
 * (dénomination, résultat, affectation en liste, dividende, quitus…). Trois cas :
 * SARL bénéfice (avec affectation réserve + report + dividende) ; SARL perte ; SARL AU.
 */
class ApprobationComptesRenderTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static DocxTemplateEngine engine;
    private final ApprobationComptesMapper mapper =
            new ApprobationComptesMapper(new AnnualReportMapper());

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
        // Grammaire d'assemblage (2026-08-17) — vérifie la SOUDURE entre le texte du
        // modèle et la valeur injectée (« à au siège social », « DE le gérant unique »,
        // « de droit Allemagne »), que la non-vacuité ne voit pas.
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
        return m("denomination", "PARACOSME", "capitalChiffres", 100000L, "siegeSocial",
                "101 bd Zerktouni, Casablanca", "nombreParts", 1000L, "rcNumero", "123456",
                "villeGreffe", "Casablanca");
    }

    private static Map<String, Object> seance() {
        return m("type", "ordinaire", "date", "2026-06-15", "heure", "10 heures",
                "lieu", "au siège social", "heureCloture", "12 heures",
                "presidentNom", "M. Yassine BENANI", "presidentQualite", "gérant",
                "secretairePresent", "oui", "secretaireNom", "Mme Salma IDRISSI");
    }

    private static Map<String, Object> convocation() {
        return m("auteur", "la gérance", "date", "2026-05-30",
                "mode", "lettre recommandée avec accusé de réception");
    }

    private static List<Map<String, Object>> resolutions() {
        return List.of(
                m("intitule", "Approbation des comptes", "texte",
                        "L'assemblée approuve le rapport de gestion et les comptes de l'exercice.",
                        "voixPour", "1000", "voixContre", "0", "abstentions", "0", "resultat", "adoptée"),
                m("intitule", "Quitus à la gérance", "texte",
                        "L'assemblée donne quitus entier et sans réserve à la gérance.",
                        "voixPour", "1000", "voixContre", "0", "abstentions", "0", "resultat", "adoptée"));
    }

    private Map<String, Object> baseSarl() {
        Map<String, Object> p = m(
                "societe", societe(), "seance", seance(), "convocation", convocation(),
                "ordreDuJour", List.of("Approbation des comptes annuels", "Affectation du résultat",
                        "Quitus à la gérance"),
                "resolutions", resolutions());
        p.put("associes", List.of(
                m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom", "Yassine", "nom", "BENANI",
                        "adresse", "5 rue A", "nombreParts", 600L, "presence", "présent"),
                m("typePersonne", "PHYSIQUE", "civilite", "Mme", "prenom", "Salma", "nom", "IDRISSI",
                        "adresse", "8 rue B", "nombreParts", 400L, "presence", "représenté",
                        "mandataireNom", "M. BENANI")));
        return p;
    }

    @Test
    void sarl_benefice_avec_affectation_et_dividende() {
        Map<String, Object> p = baseSarl();
        p.put("approbation", m(
                "exerciceClosDate", "2025-12-31",
                "commissairePresent", "non",
                "resultatType", "bénéfice", "resultatNet", 250000L,
                "affectations", List.of(
                        m("libelle", "Réserve légale", "montant", 12500L),
                        m("libelle", "Report à nouveau", "montant", 87500L),
                        m("libelle", "Dividendes", "montant", 150000L)),
                "dividendeDistribue", "oui", "dividendeParPart", 150L,
                "dividendeMiseEnPaiementDate", "2026-07-01"));
        RenderResult r = render("PV_APPROBATION_COMPTES_SARL", p);
        assertClean("PV_APPROBATION_COMPTES_SARL", r);
        assertTrue(r.text().contains("PARACOSME"), "dénomination absente");
        assertTrue(r.text().contains("bénéficiaire"), "résultat bénéficiaire absent");
        assertTrue(r.text().contains("Réserve légale") && r.text().contains("Report à nouveau")
                && r.text().contains("Dividendes"), "affectation en liste absente");
        assertTrue(r.text().contains("dividende") || r.text().contains("mis en paiement"),
                "mention dividende absente");
    }

    @Test
    void sarl_perte() {
        Map<String, Object> p = baseSarl();
        p.put("approbation", m(
                "exerciceClosDate", "2025-12-31",
                "commissairePresent", "oui", "commissaireNom", "Cabinet AUDIT & CO",
                "resultatType", "perte", "resultatNet", -80000L,
                "affectations", List.of(
                        m("libelle", "Report à nouveau déficitaire", "montant", 80000L)),
                "dividendeDistribue", "non"));
        RenderResult r = render("PV_APPROBATION_COMPTES_SARL", p);
        assertClean("PV_APPROBATION_COMPTES_SARL", r);
        assertTrue(r.text().contains("déficitaire"), "résultat déficitaire absent");
        assertTrue(r.text().contains("Cabinet AUDIT & CO"), "commissaire aux comptes absent");
    }

    @Test
    void sarl_au_benefice() {
        Map<String, Object> p = m(
                "societe", societe(), "seance", seance(), "convocation", convocation(),
                "associeUnique", true,
                "ordreDuJour", List.of("Approbation des comptes", "Affectation du résultat"),
                "resolutions", List.of(m("intitule", "Approbation des comptes",
                        "texte", "L'associé unique approuve les comptes.", "resultat", "adoptée")),
                "gerants", List.of(m("civilite", "M", "prenom", "Omar", "nom", "CHERKAOUI")));
        p.put("associes", List.of(m("typePersonne", "PHYSIQUE", "civilite", "M", "prenom", "Omar",
                "nom", "CHERKAOUI", "adresse", "12 rue X", "nombreParts", 1000L, "presence", "présent",
                "nationalite", "marocaine", "pieceType", "CIN", "pieceNumero", "BK12345")));
        p.put("approbation", m(
                "exerciceClosDate", "2025-12-31",
                "commissairePresent", "non",
                "resultatType", "bénéfice", "resultatNet", 180000L,
                "affectations", List.of(
                        m("libelle", "Réserve légale", "montant", 9000L),
                        m("libelle", "Report à nouveau", "montant", 171000L)),
                "dividendeDistribue", "non"));
        RenderResult r = render("PV_APPROBATION_COMPTES_SARL_AU", p);
        assertClean("PV_APPROBATION_COMPTES_SARL_AU", r);
        assertTrue(r.text().contains("CHERKAOUI"), "associé unique absent");
        assertTrue(r.text().contains("bénéficiaire"), "résultat bénéficiaire absent");
    }

    // ------------------------------------------------------------------
    // Lot DIVERS §E (2026-08-13) — ordre du jour et résolutions DÉRIVÉS
    // ------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void resolutions_derivees_des_donnees_structurees() {
        // Le payload fournit des résolutions en texte libre : elles DOIVENT être
        // ignorées au profit des résolutions dérivées, sinon deux sources concurrentes
        // décriraient le même acte.
        Map<String, Object> p = baseSarl();
        p.put("approbation", m(
                "exerciceClosDate", "2025-12-31",
                "commissairePresent", "non",
                "resultatType", "bénéfice", "resultatNet", 250000L,
                "affectations", List.of(
                        m("libelle", "Réserve légale", "montant", 12500L),
                        m("libelle", "Dividendes", "montant", 237500L)),
                "dividendeDistribue", "oui",
                "dividendeMontantTotal", 237500L,
                "dividendeParPart", 237L,
                "dividendeMiseEnPaiementDate", "2026-07-01",
                "quitusGerance", "oui",
                "conventionsReglementees", "non"));

        Map<String, Object> vars = mapper.map("PV_APPROBATION_COMPTES_SARL", p);
        List<Map<String, Object>> res = (List<Map<String, Object>>) vars.get("RESOLUTIONS");

        // approbation + affectation + dividendes + conventions + quitus + formalités
        assertTrue(res.size() == 6, "6 résolutions attendues, obtenu " + res.size());
        assertTrue(String.valueOf(res.get(0).get("RESOLUTION_NUMERO")).startsWith("PREMIÈRE"));
        assertTrue(String.valueOf(res.get(0).get("RESOLUTION_TEXTE")).contains("31/12/2025"));
        assertTrue(String.valueOf(res.get(1).get("RESOLUTION_TEXTE")).contains("Réserve légale"));
        // Le séparateur de milliers de la locale FR est une espace insécable étroite :
        // on compare sur les chiffres, pas sur la forme typographique.
        String dividendes = String.valueOf(res.get(2).get("RESOLUTION_TEXTE"))
                .replaceAll("[\\s\\u00A0\\u202F]", "");
        assertTrue(dividendes.contains("237500"),
                "montant TOTAL des dividendes publié dans la résolution : " + res.get(2));
        assertTrue(String.valueOf(res.get(2).get("RESOLUTION_TEXTE")).contains("01/07/2026"),
                "date de mise en paiement publiée");
        assertTrue(String.valueOf(res.get(4).get("RESOLUTION_INTITULE")).contains("Quitus"));
        // Le texte libre du payload ne doit plus apparaître.
        for (Map<String, Object> r : res) {
            assertFalse(String.valueOf(r.get("RESOLUTION_TEXTE"))
                            .contains("L'assemblée approuve le rapport de gestion et les comptes"),
                    "une résolution saisie librement a survécu à la dérivation");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void quitus_refuse_et_conventions_approuvees_changent_les_resolutions() {
        Map<String, Object> p = baseSarl();
        p.remove("ordreDuJour");
        p.put("approbation", m(
                "exerciceClosDate", "2025-12-31",
                "commissairePresent", "oui", "commissaireNom", "Cabinet AUDIT & CO",
                "resultatType", "bénéfice", "resultatNet", 100000L,
                "affectations", List.of(m("libelle", "Report à nouveau", "montant", 100000L)),
                "dividendeDistribue", "non",
                "quitusGerance", "non",
                "conventionsReglementees", "oui",
                "conventionsDetail", "convention de compte courant d'associé du 12/03/2025"));

        Map<String, Object> vars = mapper.map("PV_APPROBATION_COMPTES_SARL", p);
        List<Map<String, Object>> res = (List<Map<String, Object>>) vars.get("RESOLUTIONS");
        String joined = res.stream()
                .map(r -> String.valueOf(r.get("RESOLUTION_INTITULE")) + ' '
                        + String.valueOf(r.get("RESOLUTION_TEXTE")))
                .reduce("", (a, b) -> a + '\n' + b);

        assertFalse(joined.contains("Quitus"), "quitus refusé : la résolution ne doit pas exister");
        assertTrue(joined.contains("compte courant d'associé"), "conventions détaillées absentes");

        // Ordre du jour dérivé quand il n'est pas fourni : le CAC y figure.
        List<Map<String, Object>> odj = (List<Map<String, Object>>) vars.get("ORDRE_DU_JOUR");
        String odjText = odj.stream()
                .map(o -> String.valueOf(o.get("POINT_ORDRE_DU_JOUR")))
                .reduce("", (a, b) -> a + '\n' + b);
        assertTrue(odjText.contains("commissaire aux comptes"), "point CAC absent de l'ordre du jour");
        assertTrue(odjText.contains("Conventions réglementées"), "point conventions absent");
        assertFalse(odjText.contains("Quitus"), "quitus refusé : le point ne doit pas figurer");
    }

    @Test
    void genererEchantillons() {
        java.nio.file.Path out = java.nio.file.Path.of("target", "echantillons-approbation");
        try {
            java.nio.file.Files.createDirectories(out);
            Map<String, Object> sarl = baseSarl();
            sarl.put("approbation", m(
                    "exerciceClosDate", "2025-12-31", "commissairePresent", "non",
                    "resultatType", "bénéfice", "resultatNet", 250000L,
                    "affectations", List.of(
                            m("libelle", "Réserve légale", "montant", 12500L),
                            m("libelle", "Report à nouveau", "montant", 87500L),
                            m("libelle", "Dividendes", "montant", 150000L)),
                    "dividendeDistribue", "oui", "dividendeParPart", 150L,
                    "dividendeMiseEnPaiementDate", "2026-07-01"));
            DocumentResult res = engine().generate("PV_APPROBATION_COMPTES_SARL",
                    mapper.map("PV_APPROBATION_COMPTES_SARL", sarl));
            java.nio.file.Files.write(out.resolve("PV_APPROBATION_COMPTES_SARL.docx"), res.bytes());
        } catch (Exception ex) {
            System.out.println("[echantillons approbation] ignoré : " + ex.getMessage());
        }
    }
}
