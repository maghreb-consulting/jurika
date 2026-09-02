package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passe d'assignation des styles Word pour les Statuts SARL / SARL_AU
 * (constitutifs + modifies).
 *
 * <p><b>Architecture HYBRIDE styles Word.</b> L'apparence (police, tailles, gras)
 * est definie cote gabarit dans {@code word/styles.xml} :
 * <ul>
 *   <li>{@code JurikaTitreArticle} — titre d'article (ex. {@code "ARTICLE 6 - APPORTS"}).</li>
 *   <li>{@code JurikaSousTitre} — sous-titre numerote (ex. {@code "6.1 Apports en numeraire"}).</li>
 *   <li>{@code Normal} — corps du document (justifie, inchange).</li>
 * </ul>
 *
 * <p>Cette classe se contente de <em>classer</em> les paragraphes et de leur
 * <em>assigner</em> le style correspondant via {@link XWPFParagraph#setStyle(String)}.
 * Aucune taille, police ou couleur n'est codee en dur ici (sauf la neutralisation
 * du formatage direct sur les runs des paragraphes hisses, necessaire pour que le
 * style du gabarit s'applique reellement).
 *
 * <p><b>Regles de classement.</b>
 * <ol>
 *   <li>{@code ARTICLE n ...} (regex {@code ^\s*ARTICLE\s+\d+}) → style
 *       {@link #STYLE_TITRE_ARTICLE}.</li>
 *   <li>{@code X.Y ...} : si la ligne contient un corps inline (separateur
 *       {@code " — "} / {@code " - "}), le paragraphe est <em>scinde</em> en deux —
 *       un sous-titre (style {@link #STYLE_SOUS_TITRE}) suivi d'un paragraphe de
 *       corps (style {@code Normal} justifie). Sinon, sous-titre seul →
 *       {@link #STYLE_SOUS_TITRE}.</li>
 *   <li>Tout autre paragraphe n'est <b>pas</b> modifie. En particulier, les
 *       paragraphes <b>centres</b> (page de titre : denomination + mention
 *       {@code STATUTS}) sont preserves tels quels — c'etait le bug de l'ancienne
 *       passe {@code StatutsTypographyApplier} qui aplatissait la page de titre.</li>
 * </ol>
 *
 * <p>Pure fonction des octets passes ; idempotent.
 */
public final class StatutsHierarchyApplier {

    /** Style du gabarit pour un titre d'article (defini dans word/styles.xml). */
    public static final String STYLE_TITRE_ARTICLE = "JurikaTitreArticle";

    /** Style du gabarit pour un sous-titre numerote (defini dans word/styles.xml). */
    public static final String STYLE_SOUS_TITRE = "JurikaSousTitre";

    /** Style corps standard du gabarit. */
    public static final String STYLE_NORMAL = "Normal";

    /** {@code ARTICLE 12} ou {@code ARTICLE 6 - APPORTS}. */
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "^\\s*ARTICLE\\s+\\d+\\b.*$",
            Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Sous-titre {@code "6.1 Apports en numeraire - corps..."}. Le groupe 1
     * capture le bloc << numero + nom du sous-titre >> a hisser en sous-titre,
     * le groupe 2 capture le corps qui doit rester en paragraphe Normal.
     *
     * <p>Heuristique : on coupe sur le premier {@code " — "} (em-dash entoure
     * d'espaces, separateur typographique utilise par le modele directeur) ou
     * sur {@code " - "} (tiret simple). Le prefixe doit contenir au plus
     * quelques mots — sinon on a affaire a un sous-titre sec sans corps inline.
     */
    private static final Pattern SUBSECTION_INLINE_PATTERN = Pattern.compile(
            "^(\\d+\\.\\d+\\s+[^—–\\-\\n]{1,80}?)\\s+[—–\\-]\\s+(.+)$",
            Pattern.UNICODE_CHARACTER_CLASS);

    /** Sous-titre seul (sans corps inline) : {@code "6.1 Apports en numeraire"}. */
    private static final Pattern SUBSECTION_BARE_PATTERN = Pattern.compile(
            "^\\s*\\d+\\.\\d+\\s+\\S.+$",
            Pattern.UNICODE_CHARACTER_CLASS);

    private StatutsHierarchyApplier() {}

    /**
     * Applique la passe complete sur le document.
     *
     * @param doc document Word charge en memoire (post-substitution scalaire).
     */
    public static void apply(XWPFDocument doc) {
        // 1. Body : eclate les sous-titres "X.Y NOM — corps" en deux paragraphes,
        //    puis classe et assigne les styles.
        splitInlineSubsectionsInBody(doc);
        for (XWPFParagraph p : new ArrayList<>(doc.getParagraphs())) {
            applyStyleToParagraph(p);
        }
        // 2. Tableaux : memes regles dans chaque cellule.
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : new ArrayList<>(cell.getParagraphs())) {
                        applyStyleToParagraph(p);
                    }
                }
            }
        }
    }

    /**
     * Detecte les paragraphes dont le texte commence par
     * {@code "X.Y NomSous-titre - corps..."} et les separe en deux paragraphes
     * successifs : un sous-titre sec et un paragraphe Normal pour le corps.
     */
    private static void splitInlineSubsectionsInBody(XWPFDocument doc) {
        List<XWPFParagraph> paragraphs = new ArrayList<>(doc.getParagraphs());
        for (XWPFParagraph p : paragraphs) {
            if (isProtectedFromHierarchy(p)) continue;
            String text = paragraphText(p);
            Matcher m = SUBSECTION_INLINE_PATTERN.matcher(text);
            if (!m.matches()) continue;
            String headingText = m.group(1).trim();
            String bodyText = m.group(2).trim();
            if (headingText.isEmpty() || bodyText.isEmpty()) continue;
            // Le numero doit etre coherent : on ne traite que ce qui commence
            // par un schema "N.M ..." (deja garanti par la regex).

            // 1) Le paragraphe courant devient le corps (style Normal).
            rewriteSingleRun(p, bodyText);
            p.setStyle(STYLE_NORMAL);

            // 2) On insere un paragraphe AVANT lui, qui portera le sous-titre.
            XmlCursor cursor = p.getCTP().newCursor();
            XWPFParagraph heading = doc.insertNewParagraph(cursor);
            cursor.dispose();
            rewriteSingleRun(heading, headingText);
            heading.setStyle(STYLE_SOUS_TITRE);
        }
    }

    /**
     * Classe et assigne le style du gabarit au paragraphe. Ne touche jamais aux
     * paragraphes centres (page de titre) ni a ceux deja styles JurikaTitreArticle
     * ou JurikaSousTitre (idempotence).
     */
    private static void applyStyleToParagraph(XWPFParagraph p) {
        if (isProtectedFromHierarchy(p)) return;
        String text = paragraphText(p);
        if (text.isBlank()) return;

        String trimmed = text.strip();
        if (ARTICLE_PATTERN.matcher(trimmed).matches()) {
            p.setStyle(STYLE_TITRE_ARTICLE);
            neutralizeDirectRunFormatting(p);
            return;
        }
        if (SUBSECTION_BARE_PATTERN.matcher(trimmed).matches()) {
            p.setStyle(STYLE_SOUS_TITRE);
            neutralizeDirectRunFormatting(p);
            return;
        }
        // Corps : ne rien forcer. Le style Normal du gabarit reste en place.
    }

    /**
     * Vrai si le paragraphe ne doit jamais etre reclasse par la hierarchie :
     * paragraphes centres (page de titre = denomination + STATUTS) ou deja
     * portant un style Jurika (idempotence).
     */
    private static boolean isProtectedFromHierarchy(XWPFParagraph p) {
        ParagraphAlignment a = p.getAlignment();
        if (a == ParagraphAlignment.CENTER) return true;
        String style = p.getStyle();
        return STYLE_TITRE_ARTICLE.equals(style) || STYLE_SOUS_TITRE.equals(style);
    }

    /**
     * Neutralise le formatage direct (gras, taille, police) porte par les runs
     * d'un paragraphe afin que le style du gabarit s'applique reellement.
     * <em>Ne reintroduit aucune taille / police codee en dur.</em>
     */
    private static void neutralizeDirectRunFormatting(XWPFParagraph p) {
        for (XWPFRun r : p.getRuns()) {
            // ATTENTION : ne PAS appeler r.setBold(false) / r.setItalic(false) ici.
            // Ces appels ecrivent un <w:b val="false"/> EXPLICITE sur le run, qui
            // EMPORTE sur le gras du style de paragraphe (formatage direct > style).
            // Resultat : titres a la bonne taille mais NON gras.
            //
            // On supprime donc l'eventuel <w:b>/<w:bCs>/<w:i>/<w:iCs> direct via
            // XML bas niveau (meme approche que clearDirectSize/clearDirectFontFamily) :
            // ainsi le gras et l'italique viennent uniquement du style JurikaTitreArticle /
            // JurikaSousTitre du gabarit.
            clearDirectBold(r);
            clearDirectItalic(r);
            clearDirectSize(r);
            clearDirectFontFamily(r);
        }
    }

    /** Supprime l'eventuel {@code <w:b>} / {@code <w:bCs>} direct du run. */
    private static void clearDirectBold(XWPFRun r) {
        var rPr = r.getCTR().getRPr();
        if (rPr == null) return;
        while (rPr.sizeOfBArray() > 0) rPr.removeB(0);
        while (rPr.sizeOfBCsArray() > 0) rPr.removeBCs(0);
    }

    /** Supprime l'eventuel {@code <w:i>} / {@code <w:iCs>} direct du run. */
    private static void clearDirectItalic(XWPFRun r) {
        var rPr = r.getCTR().getRPr();
        if (rPr == null) return;
        while (rPr.sizeOfIArray() > 0) rPr.removeI(0);
        while (rPr.sizeOfICsArray() > 0) rPr.removeICs(0);
    }

    /** Supprime l'eventuel {@code <w:sz>} / {@code <w:szCs>} direct du run. */
    private static void clearDirectSize(XWPFRun r) {
        var rPr = r.getCTR().getRPr();
        if (rPr == null) return;
        while (rPr.sizeOfSzArray() > 0) rPr.removeSz(0);
        while (rPr.sizeOfSzCsArray() > 0) rPr.removeSzCs(0);
    }

    /** Supprime l'eventuel {@code <w:rFonts>} direct du run. */
    private static void clearDirectFontFamily(XWPFRun r) {
        var rPr = r.getCTR().getRPr();
        if (rPr == null) return;
        while (rPr.sizeOfRFontsArray() > 0) rPr.removeRFonts(0);
    }

    /**
     * Remplace tous les runs d'un paragraphe par un unique run portant le
     * texte donne (utilise lors de la separation sous-titre / corps).
     */
    private static void rewriteSingleRun(XWPFParagraph p, String text) {
        List<XWPFRun> runs = p.getRuns();
        for (int i = runs.size() - 1; i >= 0; i--) {
            p.removeRun(i);
        }
        XWPFRun r = p.createRun();
        r.setText(text, 0);
    }

    private static String paragraphText(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun r : p.getRuns()) {
            String t = r.text();
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }
}
