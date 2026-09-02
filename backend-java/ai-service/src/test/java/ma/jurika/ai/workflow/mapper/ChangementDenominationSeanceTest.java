package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>M4 — règle figée</b> : quelle dénomination porte quel document, lors d'une AG qui
 * change précisément la dénomination (décision produit du 2026-08-17).
 *
 * <h2>La règle</h2>
 * <ul>
 *   <li><b>Documents de SÉANCE</b> (PV, convocation, feuille de présence) → en-tête à
 *       l'<b>ANCIENNE</b> dénomination : c'est sous ce nom que la société délibère
 *       encore, la nouvelle n'existant qu'à l'issue du vote.</li>
 *   <li><b>RÉSOLUTIONS du PV</b>, <b>STATUTS REFONDUS</b> et <b>ANNONCE LÉGALE</b> →
 *       la <b>NOUVELLE</b> dénomination : ces textes décrivent la société telle qu'elle
 *       est APRÈS la décision.</li>
 * </ul>
 *
 * <h2>Pourquoi un test</h2>
 * La cohérence repose sur le fait que les trois panneaux de séance lisent la même source
 * (l'état persisté du dossier, cf. {@code ModificationWorkflowPage}). Rien dans le code
 * n'empêcherait un futur contributeur de « corriger » l'en-tête du PV vers la nouvelle
 * dénomination — ce qui paraîtrait logique et serait juridiquement faux. Ce test rend la
 * règle exécutable plutôt que seulement documentée.
 */
class ChangementDenominationSeanceTest {

    private static final String ANCIENNE = "PARACOSME";
    private static final String NOUVELLE = "PARACOSME CONSULTING";

    private DocxTemplateEngine engine;
    private final ModificationMapper modification = new ModificationMapper();

    @BeforeEach
    void setUp() throws Exception {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        engine = new DocxTemplateEngine(loader);
    }

    /**
     * Payload d'une AG changeant la dénomination. La société y est décrite avec son nom
     * ACTUEL — c'est bien l'état d'avant le vote qui est fourni au moteur.
     */
    private static Map<String, Object> payloadChangementDenomination() {
        Map<String, Object> societe = new LinkedHashMap<>();
        societe.put("denomination", ANCIENNE);
        societe.put("formeJuridique", "SARL");
        societe.put("capitalChiffres", 100_000);
        societe.put("siegeSocial", "12 RUE DES FOULES, CASABLANCA");
        societe.put("nombreParts", 1_000);
        societe.put("rcNumero", "123456");
        societe.put("villeGreffe", "CASABLANCA");

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", societe);
        p.put("seance", Map.of("type", "extraordinaire", "date", "2026-06-01",
                "heure", "10H00", "lieu", "SIEGE SOCIAL",
                "presidentNom", "M. Ahmed ALAOUI", "presidentQualite", "gérant"));
        p.put("convocation", Map.of("auteur", "la gérance", "date", "2026-05-10",
                "mode", "lettre recommandée"));
        p.put("associes", List.of(
                Map.of("typePersonne", "PHYSIQUE", "civilite", "M.", "prenom", "Ahmed",
                        "nom", "ALAOUI", "nombreParts", 600, "presence", "présent",
                        "adresse", "45 BD ZERKTOUNI", "pieceNumero", "BE111111"),
                Map.of("typePersonne", "PHYSIQUE", "civilite", "Mme", "prenom", "Fatima",
                        "nom", "BENNANI", "nombreParts", 400, "presence", "présent",
                        "adresse", "45 BD ZERKTOUNI", "pieceNumero", "BE222222")));
        p.put("gerants", List.of(Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "ALAOUI")));
        p.put("ordreDuJour", List.of("Changement de dénomination"));
        p.put("resolutions", List.of(Map.of(
                "type", "modification_denomination",
                "objet", "Changement de dénomination sociale",
                "nouvelleDenomination", NOUVELLE,
                "dateEffet", "2026-06-01",
                "articlesModifies", "2")));
        return p;
    }

    @Test
    @DisplayName("M4 — le PV porte l'ANCIENNE dénomination en en-tête, la NOUVELLE dans ses résolutions")
    void pvDeSeance() throws Exception {
        String texte = rendre("PV_MODIFICATION_SARL",
                modification.map("PV_MODIFICATION_SARL", payloadChangementDenomination()));

        // L'en-tête décrit la société qui délibère : elle s'appelle encore PARACOSME.
        String enTete = texte.lines().limit(12).reduce("", (a, b) -> a + "\n" + b);
        assertThat(enTete)
                .describedAs("En-tête du PV : la société délibère encore sous son ancien nom")
                .contains(ANCIENNE)
                .doesNotContain(NOUVELLE);

        // La nouvelle dénomination existe bien — dans la résolution qui la crée.
        assertThat(texte)
                .describedAs("La résolution doit énoncer la nouvelle dénomination")
                .contains(NOUVELLE);
    }

    @Test
    @DisplayName("M4 — l'ANNONCE LÉGALE publie la NOUVELLE dénomination (état après décision)")
    void annonceLegale() throws Exception {
        String texte = rendre("ANNONCE_LEGALE_MODIFICATION_SARL",
                modification.map("ANNONCE_LEGALE_MODIFICATION_SARL",
                        payloadChangementDenomination()));

        assertThat(texte)
                .describedAs("L'avis publie le changement : la nouvelle dénomination doit y figurer")
                .contains(NOUVELLE);
    }

    @Test
    @DisplayName("M4 — convocation et feuille de présence : ANCIENNE dénomination (même séance)")
    void documentsDeSeanceAnnexes() throws Exception {
        Map<String, Object> payload = payloadChangementDenomination();
        for (String code : new String[]{"CONVOCATION_AG", "FEUILLE_PRESENCE_AG"}) {
            String texte = rendre(code, SeancePvVarsBuilder.build(code, payload));
            assertThat(texte)
                    .describedAs("%s : convoquer / faire émarger sous le nom en vigueur ce jour-là", code)
                    .contains(ANCIENNE)
                    .doesNotContain(NOUVELLE);
        }
    }

    private String rendre(String templateCode, Map<String, Object> vars) throws Exception {
        DocxTemplateEngine.DocumentResult res = engine.generate(templateCode, vars);
        assertThat(res.templateFound()).describedAs("modèle %s introuvable", templateCode).isTrue();
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(res.bytes()))) {
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
        }
        return sb.toString();
    }
}
