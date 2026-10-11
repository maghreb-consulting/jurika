package ma.jurika.ai.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2-A — GARDE-FOU « contenu intouchable » : la mise en page professionnelle
 * (styles, polices, alignements, pagination) ne doit RIEN changer au contenu du
 * directeur. Ce test compare, paragraphe par paragraphe, le texte des 4 templates
 * directeur à une <b>référence figée</b> (capturée avant le restyle) et vérifie que
 * les marqueurs {@code $ / ◇ / ▼} sont préservés.
 *
 * <p>Toute divergence = régression de contenu (reformulation, ajout/suppression de
 * phrase, altération d'un {@code $VAR} ou d'une balise) → échec.
 */
class DirecteurTemplateContentUnchangedTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // Lot L4d (E11) : les statuts refondus sont rendus par les statuts du corpus ; les
            // deux gabarits deterministes du directeur et leurs baselines sont retires.
            // Phase 4 — PV de séance (incident) transverses.
            "PV_DEFAUT_QUORUM_SARL.docx",
            "PV_DEFAUT_QUORUM_SARL_AU.docx",
            "PV_IRREGULARITE_CONVOCATION_SARL.docx",
            "PV_IRREGULARITE_CONVOCATION_SARL_AU.docx",
            // Lot L4d (E11b) : convocation et feuille de presence rendues par les modeles du
            // corpus ; les gabarits du classpath et leurs baselines sont retires.
            // Phase B — PV d'approbation des comptes (AGO annuelle).
            "PV_APPROBATION_COMPTES_SARL.docx",
            "PV_APPROBATION_COMPTES_SARL_AU.docx",
            // Phase C - Dissolution / Liquidation : lot L4d (E11d), PV unifie et rapport du
            // liquidateur retires (modeles du corpus).
            // Phase D — Succursales (création MA / étrangère + fermeture, SARL & SARL AU).
            "PV_CREATION_SUCCURSALE_MAROC_SARL.docx",
            "PV_CREATION_SUCCURSALE_MAROC_SARL_AU.docx",
            "PV_CREATION_SUCCURSALE_ETRANGERE_SARL.docx",
            "PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU.docx",
            "PV_FERMETURE_SUCCURSALE_SARL.docx",
            "PV_FERMETURE_SUCCURSALE_SARL_AU.docx",
            // Lot L4d (E11c) : PV_MODIFICATION_SARL(_AU) retires, rendus par PV_AG_SARL et
            // PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU du corpus.
            // Phase 2 — Annonce légale de modification (avis JAL, boucle DECISIONS), SARL & SARL AU.
            "ANNONCE_LEGALE_MODIFICATION_SARL.docx",
            "ANNONCE_LEGALE_MODIFICATION_SARL_AU.docx",
            // 2026-08-12 — Annonce légale de dissolution (avis simple, sans condition
            // ni boucle : le modèle directeur ne comporte que des $VAR).
            "ANNONCE_LEGALE_DISSOLUTION_SARL.docx",
            "ANNONCE_LEGALE_DISSOLUTION_SARL_AU.docx",
            // 2026-08-13 — Annonce légale de CLÔTURE DE LIQUIDATION (second avis du
            // cycle) : avis à condition unique ◇ SI boni / ◇ SINON mali, sans boucle.
            "ANNONCE_LEGALE_LIQUIDATION_SARL.docx",
            "ANNONCE_LEGALE_LIQUIDATION_SARL_AU.docx",
            // 2026-08-13 (lot DIVERS) — Annonces légales de SUCCURSALE : ouverture (deux
            // blocs ◇ SI : dotation, responsable) et fermeture (avis linéaire).
            "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL.docx",
            "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU.docx",
            "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL.docx",
            "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL_AU.docx",
            // Lot L4d (E11d) : rapport du liquidateur, toujours rendu par le classpath (bascule
            // sur RAPPORT_LIQUIDATEUR_* non resolue).
            "RAPPORT_LIQUIDATION_DIRECTEUR.docx"})
            // Lot L4d (E11) : la variante etrangere de l'avis d'ouverture (derive-jurika) est
            // retiree, l'avis est rendu par le modele du corpus.
    void contenu_identique_a_la_reference(String fn) throws Exception {
        List<String> baseline = loadBaseline(fn);
        List<String> current = paragraphTexts("templates/docx/" + fn);
        assertEquals(baseline, current,
                fn + " : le contenu a changé (la Phase 2 ne doit agir QUE sur la forme).");

        String joined = String.join("\n", current);
        assertTrue(joined.contains("$"), fn + " : marqueurs $VAR disparus.");
        // Conditions / boucles : exigées uniquement pour les modèles qui en portent
        // (l'annonce de dissolution est un avis linéaire, sans ◇ ni ▼).
        String baselineJoined = String.join("\n", baseline);
        if (baselineJoined.contains("◇") || baselineJoined.contains("▼")) {
            assertTrue(joined.contains("◇") || joined.contains("▼"),
                    fn + " : marqueurs ◇/▼ disparus.");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> loadBaseline(String fn) throws Exception {
        try (InputStream in = DirecteurTemplateContentUnchangedTest.class
                .getResourceAsStream("/directeur-baseline/" + fn + ".json")) {
            assertNotNull(in, "Référence baseline introuvable pour " + fn);
            return OM.readValue(in, List.class);
        }
    }

    private static List<String> paragraphTexts(String resourcePath) throws Exception {
        // Lot A (2026-09-10) — un gabarit retiré du disque mais oublié dans la liste
        // ci-dessus échouait par un NullPointerException sur `stream.close()`, au
        // fond de POI : illisible, et rien ne nommait le fichier manquant. Le cas
        // s'est produit — deux entrées avaient survécu à la suppression du corpus de
        // création. On le dit maintenant en clair.
        InputStream flux = DirecteurTemplateContentUnchangedTest.class.getClassLoader()
                .getResourceAsStream(resourcePath);
        assertNotNull(flux, "Gabarit introuvable sur le classpath : " + resourcePath
                + " — s'il a été supprimé, retirer son entrée de la liste et sa baseline.");
        try (InputStream in = flux;
             XWPFDocument doc = new XWPFDocument(in)) {
            List<String> lines = new ArrayList<>();
            for (XWPFParagraph p : doc.getParagraphs()) {
                StringBuilder sb = new StringBuilder();
                for (XWPFRun r : p.getRuns()) {
                    String t = r.text();
                    if (t != null) sb.append(t);
                }
                lines.add(sb.toString());
            }
            return lines;
        }
    }
}
