package ma.jurika.dataroom.infrastructure.word;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lot L5 (E7) : remplit un gabarit Word des fiches (ressources {@code gabarits/fiches},
 * construits par {@code scripts/l5/gabarits_fiches.py}).
 *
 * <ul>
 *   <li>{@code $NOM} : remplace par la valeur fournie ;</li>
 *   <li>une ligne de tableau qui porte des variables d'un prefixe repete ({@code $VTP_},
 *       {@code $OP_}) est une ligne modele : elle est repetee pour chaque element, et
 *       remplacee par une ligne "vide" nommee quand la liste est vide.</li>
 * </ul>
 * Une variable sans valeur est une erreur : aucun document ne sort avec un {@code $NOM}
 * imprime. Rendu deterministe : memes donnees, memes octets (horodatage fige).
 */
public final class GabaritWord {

    private static final Pattern VARIABLE = Pattern.compile("\\$[A-Z][A-Z0-9_]*");

    private GabaritWord() {}

    /**
     * @param gabarit      chemin du gabarit dans le classpath
     * @param valeurs      variables simples (nom sans {@code $})
     * @param repetitions  prefixe (ex. {@code VTP_}) vers ses lignes (variables sans {@code $})
     * @param siVide       prefixe vers le texte de la premiere cellule quand la liste est vide
     */
    public static byte[] remplir(String gabarit, Map<String, String> valeurs,
                                 Map<String, List<Map<String, String>>> repetitions,
                                 Map<String, String> siVide) {
        try (InputStream in = GabaritWord.class.getClassLoader().getResourceAsStream(gabarit)) {
            if (in == null) throw new IllegalStateException("Gabarit introuvable : " + gabarit);
            try (XWPFDocument doc = new XWPFDocument(in)) {
                for (IBodyElement e : new ArrayList<>(doc.getBodyElements())) {
                    if (e instanceof XWPFParagraph p) {
                        remplacer(p, valeurs);
                    } else if (e instanceof XWPFTable t) {
                        remplirTableau(t, valeurs, repetitions, siVide);
                    }
                }
                verifierAucuneVariable(doc);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                doc.write(out);
                return ZipHorodatage.figer(out.toByteArray());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Gabarit illisible : " + gabarit, ex);
        }
    }

    private static void remplirTableau(XWPFTable t, Map<String, String> valeurs,
                                       Map<String, List<Map<String, String>>> repetitions,
                                       Map<String, String> siVide) {
        for (int i = 0; i < t.getRows().size(); i++) {
            XWPFTableRow row = t.getRow(i);
            String prefixe = prefixeRepete(texte(row), repetitions);
            if (prefixe == null) {
                for (XWPFTableCell c : row.getTableCells()) {
                    for (XWPFParagraph p : c.getParagraphs()) remplacer(p, valeurs);
                }
                continue;
            }
            List<Map<String, String>> lignes = repetitions.get(prefixe);
            if (lignes.isEmpty()) {
                List<XWPFTableCell> cells = row.getTableCells();
                for (int c = 0; c < cells.size(); c++) {
                    String texte = c == 0 ? siVide.getOrDefault(prefixe, "") : "";
                    for (XWPFParagraph p : cells.get(c).getParagraphs()) remplacerTout(p, texte);
                }
                continue;
            }
            CTRow modele = (CTRow) row.getCtRow().copy();
            int pos = i;
            t.removeRow(i);
            for (Map<String, String> ligne : lignes) {
                XWPFTableRow nouvelle = new XWPFTableRow((CTRow) modele.copy(), t);
                for (XWPFTableCell c : nouvelle.getTableCells()) {
                    for (XWPFParagraph p : c.getParagraphs()) remplacer(p, ligne);
                }
                t.addRow(nouvelle, pos++);
            }
            i = pos - 1;
        }
    }

    private static String prefixeRepete(String texte, Map<String, List<Map<String, String>>> repetitions) {
        for (String prefixe : repetitions.keySet()) {
            if (texte.contains("$" + prefixe)) return prefixe;
        }
        return null;
    }

    private static String texte(XWPFTableRow row) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableCell c : row.getTableCells()) sb.append(c.getText()).append('\n');
        return sb.toString();
    }

    private static void remplacer(XWPFParagraph p, Map<String, String> valeurs) {
        for (XWPFRun r : p.getRuns()) {
            String t = r.getText(0);
            if (t == null || t.indexOf('$') < 0) continue;
            Matcher m = VARIABLE.matcher(t);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String nom = m.group().substring(1);
                String v = valeurs.get(nom);
                if (v == null) throw new IllegalStateException("Variable sans valeur dans la fiche : " + m.group());
                m.appendReplacement(sb, Matcher.quoteReplacement(v));
            }
            m.appendTail(sb);
            r.setText(sb.toString(), 0);
        }
    }

    private static void remplacerTout(XWPFParagraph p, String texte) {
        List<XWPFRun> runs = p.getRuns();
        for (int i = 0; i < runs.size(); i++) runs.get(i).setText(i == 0 ? texte : "", 0);
    }

    private static void verifierAucuneVariable(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append('\n');
        // XWPFTable#getText est fige a la lecture du gabarit : on relit chaque cellule.
        for (XWPFTable t : doc.getTables()) {
            for (XWPFTableRow row : t.getRows()) sb.append(texte(row));
        }
        Matcher m = VARIABLE.matcher(sb);
        if (m.find()) throw new IllegalStateException("Variable non remplie dans la fiche : " + m.group());
    }
}
