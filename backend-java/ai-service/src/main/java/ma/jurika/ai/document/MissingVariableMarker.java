package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passe universelle de mise en rouge des variables manquantes.
 *
 * <p>{@link DocxTemplateEngine#resolveUpper(String, java.util.Map, java.util.Map, String)}
 * et {@code resolveLower} emettent, lorsqu'une variable est introuvable, un sentinel
 * {@link #SENTINEL_OPEN}{@code NOM_VAR}{@link #SENTINEL_CLOSE} (caracteres Private Use
 * Area garantis absents du contenu juridique). Cette classe parcourt le document
 * post-substitution (body + tableaux + headers + footers), detecte les sentinels et
 * reconstruit les runs en remplacant chaque sentinel par un run gras+rouge
 * <code>&laquo; VALEUR MANQUANTE : NOM &raquo;</code> (couleur {@code C00000}),
 * en preservant la police/taille des segments voisins (clonage depuis le 1er run du
 * paragraphe).
 *
 * <p>Pure fonction des octets passes ; idempotent (executer la passe deux fois donne
 * le meme resultat des lors que les sentinels ont disparu). Aucune dependance externe.
 *
 * @return {@link #apply(XWPFDocument)} retourne la liste ordonnee et dedupliquee des
 *         noms de variables manquantes rencontrees.
 */
public final class MissingVariableMarker {

    /**
     * Sentinel d'ouverture (Unicode Private Use Area U+E000) injecte par
     * {@link DocxTemplateEngine} pour delimiter le nom d'une variable manquante.
     */
    public static final char SENTINEL_OPEN = '';

    /** Sentinel de fermeture (Unicode Private Use Area U+E001). */
    public static final char SENTINEL_CLOSE = '';

    /** Pattern de recuperation d'un marqueur sentinel {@code <SENTINEL_OPEN>NOM<SENTINEL_CLOSE>}. */
    private static final Pattern SENTINEL_PATTERN = Pattern.compile(
            "([^]+)");

    /** Prefixe humain affiche a la place d'une variable manquante. */
    private static final String MISSING_PREFIX = "‹ VALEUR MANQUANTE : ";

    /** Suffixe humain. */
    private static final String MISSING_SUFFIX = " ›";

    /**
     * Sprint Cowork 2026-06-21 (C3) — Libelle affiche pour les variables connues
     * POST-immatriculation (declarees dans {@code dictionary.json#fill_later}).
     * Volontairement court et neutre, mais TOUJOURS en ROUGE pour signaler que
     * l'employe doit revenir remplir la valeur reelle apres immatriculation.
     */
    private static final String FILL_LATER_LABEL = "[à compléter]";

    /** Rouge sombre lisible sur fond blanc (Word color code). */
    private static final String MISSING_COLOR = "C00000";

    private MissingVariableMarker() {}

    /**
     * Compose un sentinel {@code <SENTINEL_OPEN>NOM<SENTINEL_CLOSE>}.
     */
    public static String sentinel(String name) {
        return String.valueOf(SENTINEL_OPEN) + name + SENTINEL_CLOSE;
    }

    /**
     * Applique la passe sur le document. Doit etre appelee APRES la substitution
     * scalaire de {@link DocxTemplateEngine}.
     *
     * <p>Compat : appelle {@link #apply(XWPFDocument, Set)} avec un set fillLater
     * vide -> toutes les variables manquantes ressortent en "‹ VALEUR MANQUANTE ›".
     *
     * @return liste ordonnee et dedupliquee des noms de variables manquantes.
     */
    public static List<String> apply(XWPFDocument doc) {
        return apply(doc, Collections.emptySet());
    }

    /**
     * Sprint Cowork 2026-06-21 (C3) — Variante avec set des variables
     * "fill later" (post-immatriculation). Pour chaque sentinel rencontre :
     * <ul>
     *   <li>Si la variable est dans {@code fillLaterKeys} -> rendu rouge
     *       {@value #FILL_LATER_LABEL} (libelle court neutre).</li>
     *   <li>Sinon -> rendu rouge
     *       {@code "‹ VALEUR MANQUANTE : NOM ›"} (signal de bug / champ
     *       oublie a saisir).</li>
     * </ul>
     *
     * @param doc document a annoter.
     * @param fillLaterKeys ensemble (UPPERCASE_SNAKE) des variables
     *        post-immatriculation. Comparison casse-insensible (uppercase
     *        applique avant lookup).
     * @return liste ordonnee et dedupliquee des noms de variables manquantes
     *         (toutes, fillLater incluses).
     */
    public static List<String> apply(XWPFDocument doc, Set<String> fillLaterKeys) {
        Set<String> normalized = new LinkedHashSet<>();
        if (fillLaterKeys != null) {
            for (String k : fillLaterKeys) {
                if (k != null && !k.isBlank()) {
                    normalized.add(k.toUpperCase(java.util.Locale.ROOT));
                }
            }
        }
        Set<String> missing = new LinkedHashSet<>();
        for (XWPFParagraph p : new ArrayList<>(doc.getParagraphs())) {
            renderMissingMarkers(p, missing, normalized);
        }
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : new ArrayList<>(cell.getParagraphs())) {
                        renderMissingMarkers(p, missing, normalized);
                    }
                }
            }
        }
        for (XWPFHeader header : doc.getHeaderList()) {
            for (XWPFParagraph p : new ArrayList<>(header.getParagraphs())) {
                renderMissingMarkers(p, missing, normalized);
            }
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            for (XWPFParagraph p : new ArrayList<>(footer.getParagraphs())) {
                renderMissingMarkers(p, missing, normalized);
            }
        }
        return new ArrayList<>(missing);
    }

    /**
     * Detecte les sentinels du paragraphe et reconstruit ses runs avec des
     * segments rouges+gras a la place. Preserve la police et la taille des autres
     * segments en clonant depuis le 1er run.
     *
     * <p>Sprint Cowork 2026-06-21 (C3) — Si la variable manquante est dans
     * {@code fillLaterUpper}, le libelle affiche est {@value #FILL_LATER_LABEL}
     * (rouge, court) au lieu de "‹ VALEUR MANQUANTE : NOM ›".
     */
    private static void renderMissingMarkers(XWPFParagraph p, Set<String> missing,
                                              Set<String> fillLaterUpper) {
        List<XWPFRun> runs = p.getRuns();
        if (runs.isEmpty()) return;
        String full = paragraphText(p);
        if (full.indexOf(SENTINEL_OPEN) < 0) return;

        XWPFRun base = runs.get(0);
        String baseFamily = base.getFontFamily();
        int baseSize = base.getFontSize();
        boolean baseBold = base.isBold();
        boolean baseItalic = base.isItalic();

        for (int i = runs.size() - 1; i >= 0; i--) {
            p.removeRun(i);
        }

        Matcher m = SENTINEL_PATTERN.matcher(full);
        int cursor = 0;
        while (m.find()) {
            String prefix = full.substring(cursor, m.start());
            String varName = m.group(1);
            missing.add(varName);
            if (!prefix.isEmpty()) {
                addRun(p, prefix, baseFamily, baseSize, baseBold, baseItalic, null);
            }
            String label = fillLaterUpper.contains(varName.toUpperCase(java.util.Locale.ROOT))
                    ? FILL_LATER_LABEL
                    : MISSING_PREFIX + varName + MISSING_SUFFIX;
            addRun(p,
                    label,
                    baseFamily,
                    baseSize,
                    true,
                    false,
                    MISSING_COLOR);
            cursor = m.end();
        }
        String tail = full.substring(cursor);
        if (!tail.isEmpty()) {
            addRun(p, tail, baseFamily, baseSize, baseBold, baseItalic, null);
        }
    }

    private static void addRun(XWPFParagraph p,
                                String text,
                                String family,
                                int size,
                                boolean bold,
                                boolean italic,
                                String hexColorOrNull) {
        XWPFRun r = p.createRun();
        if (family != null && !family.isBlank()) {
            r.setFontFamily(family);
        }
        if (size > 0) r.setFontSize(size);
        r.setBold(bold);
        r.setItalic(italic);
        if (hexColorOrNull != null) {
            r.setColor(hexColorOrNull);
        }
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
