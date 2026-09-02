package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests PARTIE B — hierarchie de styles Word pour les Statuts.
 *
 * <p>Verifie que la passe :
 * <ul>
 *   <li>assigne {@code JurikaTitreArticle} aux paragraphes "ARTICLE n ...";</li>
 *   <li>scinde "X.Y NOM - corps..." en sous-titre {@code JurikaSousTitre} + corps
 *       {@code Normal} ;</li>
 *   <li>n'altere pas la page de titre (paragraphes centres : denomination + STATUTS).</li>
 * </ul>
 */
class StatutsHierarchyApplierTest {

    private byte[] buildDocxFromParagraphs(List<ParagraphSpec> specs) throws Exception {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (ParagraphSpec spec : specs) {
                XWPFParagraph p = doc.createParagraph();
                if (spec.alignment != null) p.setAlignment(spec.alignment);
                XWPFRun r = p.createRun();
                if (spec.bold) r.setBold(true);
                if (spec.sizePt > 0) r.setFontSize(spec.sizePt);
                r.setText(spec.text);
            }
            doc.write(out);
            return out.toByteArray();
        }
    }

    private List<XWPFParagraph> openParagraphs(byte[] docxBytes) throws Exception {
        XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes));
        return new ArrayList<>(doc.getParagraphs());
    }

    private byte[] applyHierarchy(byte[] docxBytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            StatutsHierarchyApplier.apply(doc);
            doc.write(out);
            return out.toByteArray();
        }
    }

    private XWPFParagraph findParagraphContaining(byte[] docxBytes, String needle) throws Exception {
        for (XWPFParagraph p : openParagraphs(docxBytes)) {
            String txt = paragraphText(p);
            if (txt.contains(needle)) return p;
        }
        return null;
    }

    private static String paragraphText(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun r : p.getRuns()) {
            String t = r.text();
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // 1. ARTICLE n -> style JurikaTitreArticle
    // ---------------------------------------------------------------------

    @Test
    void article_recoit_style_JurikaTitreArticle() throws Exception {
        byte[] docx = buildDocxFromParagraphs(List.of(
                new ParagraphSpec("ARTICLE 6 - APPORTS", null, false, 0),
                new ParagraphSpec("Texte de corps.", null, false, 0)
        ));
        byte[] applied = applyHierarchy(docx);

        XWPFParagraph article = findParagraphContaining(applied, "ARTICLE 6");
        assertNotNull(article);
        assertEquals(StatutsHierarchyApplier.STYLE_TITRE_ARTICLE, article.getStyle(),
                "ARTICLE n attend style JurikaTitreArticle");
    }

    @Test
    void article_avec_em_dash_et_titre_recoit_style_JurikaTitreArticle() throws Exception {
        byte[] docx = buildDocxFromParagraphs(List.of(
                new ParagraphSpec("ARTICLE 11 — REPARTITION DES PARTS", null, false, 0)
        ));
        byte[] applied = applyHierarchy(docx);
        XWPFParagraph p = openParagraphs(applied).get(0);
        assertEquals(StatutsHierarchyApplier.STYLE_TITRE_ARTICLE, p.getStyle());
    }

    // ---------------------------------------------------------------------
    // 2. Sous-titre "X.Y NOM - corps" est scinde
    // ---------------------------------------------------------------------

    @Test
    void sous_titre_inline_est_scinde_en_deux_paragraphes() throws Exception {
        byte[] docx = buildDocxFromParagraphs(List.of(
                new ParagraphSpec(
                        "6.1 Apports en numeraire — Monsieur X apporte la somme de 100 000 dirhams.",
                        null, false, 0)
        ));
        byte[] applied = applyHierarchy(docx);
        List<XWPFParagraph> paras = openParagraphs(applied);

        // Au moins 2 paragraphes apres scission.
        assertTrue(paras.size() >= 2,
                "expected >= 2 paragraphs after split, got " + paras.size());

        XWPFParagraph heading = findParagraphContaining(applied, "6.1 Apports en numeraire");
        XWPFParagraph body = findParagraphContaining(applied, "Monsieur X apporte");
        assertNotNull(heading, "sous-titre introuvable");
        assertNotNull(body, "corps introuvable");

        assertEquals(StatutsHierarchyApplier.STYLE_SOUS_TITRE, heading.getStyle(),
                "sous-titre attend style JurikaSousTitre");
        assertEquals(StatutsHierarchyApplier.STYLE_NORMAL, body.getStyle(),
                "corps attend style Normal");
    }

    @Test
    void sous_titre_seul_recoit_style_JurikaSousTitre() throws Exception {
        byte[] docx = buildDocxFromParagraphs(List.of(
                new ParagraphSpec("11.3 Cession entre vifs", null, false, 0)
        ));
        byte[] applied = applyHierarchy(docx);
        XWPFParagraph p = openParagraphs(applied).get(0);
        assertEquals(StatutsHierarchyApplier.STYLE_SOUS_TITRE, p.getStyle());
    }

    // ---------------------------------------------------------------------
    // 3. NON-REGRESSION page de titre : paragraphe centre preserve
    // ---------------------------------------------------------------------

    @Test
    void page_de_titre_paragraphe_centre_16pt_gras_reste_intact() throws Exception {
        // Reproduit la page de titre : denomination centree 16 pt gras.
        byte[] docx = buildDocxFromParagraphs(List.of(
                new ParagraphSpec("ACME SARL", ParagraphAlignment.CENTER, true, 16),
                new ParagraphSpec("STATUTS", ParagraphAlignment.CENTER, true, 13)
        ));
        byte[] applied = applyHierarchy(docx);
        List<XWPFParagraph> paras = openParagraphs(applied);

        XWPFParagraph titreSociete = paras.get(0);
        XWPFParagraph titreDoc = paras.get(1);

        // 1. Alignement preserve.
        assertEquals(ParagraphAlignment.CENTER, titreSociete.getAlignment(),
                "denomination doit rester centree");
        assertEquals(ParagraphAlignment.CENTER, titreDoc.getAlignment(),
                "mention STATUTS doit rester centree");
        // 2. Style NON reclasse (la passe ne doit RIEN faire sur les paragraphes centres).
        assertNotEquals(StatutsHierarchyApplier.STYLE_TITRE_ARTICLE, titreSociete.getStyle(),
                "denomination ne doit pas etre reclassee en JurikaTitreArticle");
        assertNotEquals(StatutsHierarchyApplier.STYLE_TITRE_ARTICLE, titreDoc.getStyle(),
                "STATUTS ne doit pas etre reclasse");
        // 3. Run gras 16 pt preserve.
        XWPFRun r = titreSociete.getRuns().get(0);
        assertEquals(Boolean.TRUE, r.isBold(),
                "gras de la denomination doit etre preserve");
        assertEquals(16, r.getFontSize(),
                "taille 16 pt de la denomination doit etre preservee");
    }

    // ---------------------------------------------------------------------
    // 4. Corps : aucune modification de style
    // ---------------------------------------------------------------------

    @Test
    void paragraphe_corps_n_est_pas_reclasse() throws Exception {
        byte[] docx = buildDocxFromParagraphs(List.of(
                new ParagraphSpec("Ceci est un paragraphe de corps justifie normal.", null, false, 0)
        ));
        byte[] applied = applyHierarchy(docx);
        XWPFParagraph p = openParagraphs(applied).get(0);
        // Le style retourne peut etre null ou Normal selon POI ; surtout, NE DOIT PAS etre Jurika*.
        String style = p.getStyle();
        assertTrue(style == null || StatutsHierarchyApplier.STYLE_NORMAL.equals(style),
                "corps ne doit pas etre reclasse, vu : " + style);
    }

    // ---------------------------------------------------------------------
    // 5. Non-statuts : aucun style Jurika applique meme si "ARTICLE n" present
    //    (la passe B ne doit etre invoquee QUE pour les codes/fichiers STATUTS_*)
    // ---------------------------------------------------------------------

    @Test
    void doc_non_statuts_ne_recoit_pas_la_hierarchie_mais_marque_les_manquants() throws Exception {
        DocxTemplateEngine engine = new DocxTemplateEngine();
        byte[] docx = buildDocxFromParagraphs(List.of(
                new ParagraphSpec("ARTICLE 1 — OBJET", null, false, 0),
                new ParagraphSpec("Inconnu :${ABSENTE}", null, false, 0)
        ));
        // Filename JAL -> path non-statuts dans le moteur.
        var res = engine.render(docx, "ANNONCE_JAL_SARL.docx", java.util.Map.of());

        // (1) Marquage rouge applique (PARTIE A universelle).
        String text;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(res.bytes()))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
            text = sb.toString();
        }
        assertTrue(text.contains("VALEUR MANQUANTE : ABSENTE"),
                "marquage universel attendu meme hors statuts : " + text);
        assertTrue(res.missingVariables().contains("ABSENTE"));

        // (2) Hierarchie NON appliquee : ARTICLE n n'a PAS le style Jurika.
        XWPFParagraph article = findParagraphContaining(res.bytes(), "ARTICLE 1");
        assertNotNull(article);
        assertNotEquals(StatutsHierarchyApplier.STYLE_TITRE_ARTICLE, article.getStyle(),
                "hierarchie ne doit PAS s'appliquer hors STATUTS_*");
    }

    // ---------------------------------------------------------------------
    // 5bis. Neutralisation rPr direct : un titre avec gras/taille en dur dans le
    //       gabarit doit PERDRE son <w:b>/<w:sz> direct pour laisser le style
    //       JurikaTitreArticle imposer sa propre typographie. NE PAS forcer
    //       <w:b val="false"/> (ce qui ecraserait le gras du style).
    // ---------------------------------------------------------------------

    @Test
    void titre_article_perd_son_rpr_direct_pour_laisser_le_style_appliquer_son_gras() throws Exception {
        // Simule le cas reel des gabarits Statuts : runs avec gras / taille / police
        // explicitement positionnes -> formatage direct qui ecraserait le style si on
        // ne le nettoie pas, OU qui ecrirait b=false si on appelait setBold(false).
        byte[] docx = buildDocxFromParagraphs(List.of(
                new ParagraphSpec("ARTICLE 6 - APPORTS", null, true, 12)
        ));
        byte[] applied = applyHierarchy(docx);

        XWPFParagraph article = findParagraphContaining(applied, "ARTICLE 6");
        assertNotNull(article);
        assertEquals(StatutsHierarchyApplier.STYLE_TITRE_ARTICLE, article.getStyle(),
                "style JurikaTitreArticle doit etre assigne");

        // Verification critique : le rPr direct du run ne doit PLUS porter <w:b>
        // (ni explicitement true, ni explicitement false). Sans direct, le gras
        // vient du style JurikaTitreArticle. Avant le fix, setBold(false) ecrivait
        // <w:b val="false"/> qui masquait le gras du style.
        org.apache.poi.xwpf.usermodel.XWPFRun r = article.getRuns().get(0);
        var rPr = r.getCTR().getRPr();
        assertNotNull(rPr, "rPr attendu apres passe (au moins pour avoir contenu de la taille/police originale)");
        assertEquals(0, rPr.sizeOfBArray(),
                "aucun <w:b> direct ne doit subsister (le gras vient du style JurikaTitreArticle)");
        assertEquals(0, rPr.sizeOfBCsArray(),
                "aucun <w:bCs> direct ne doit subsister");
        assertEquals(0, rPr.sizeOfSzArray(),
                "aucune taille <w:sz> directe ne doit subsister (la taille vient du style)");
    }

    // ---------------------------------------------------------------------
    // 6. Idempotence : appliquer 2x donne le meme nombre de paragraphes
    // ---------------------------------------------------------------------

    @Test
    void passe_idempotente_sur_article_et_sous_titre() throws Exception {
        byte[] docx = buildDocxFromParagraphs(List.of(
                new ParagraphSpec("ARTICLE 6 - APPORTS", null, false, 0),
                new ParagraphSpec("6.1 Apports en numeraire — Texte de corps.", null, false, 0)
        ));
        byte[] once = applyHierarchy(docx);
        int countOnce = openParagraphs(once).size();
        byte[] twice = applyHierarchy(once);
        int countTwice = openParagraphs(twice).size();
        assertEquals(countOnce, countTwice,
                "la 2eme application ne doit pas ajouter de paragraphes ; 1=" + countOnce + " 2=" + countTwice);
    }

    // ---------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------

    private record ParagraphSpec(String text, ParagraphAlignment alignment, boolean bold, int sizePt) {}
}
