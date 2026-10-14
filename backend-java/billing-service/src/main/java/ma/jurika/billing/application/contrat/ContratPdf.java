package ma.jurika.billing.application.contrat;

import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.common.pdf.JurikaPdfDocument;
import ma.jurika.common.pdf.JurikaPdfTheme;
import ma.jurika.common.pdf.PdfTable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lot L7 (E6, RG-CTR-04) : exemplaire PDF du contrat a la charte JURIKA (logo Moucharabieh,
 * polices embarquees). Le contenu est le texte accepte, paragraphe d'acceptation rempli ; le
 * rendu ne lit jamais l'horloge (date d'edition = date de l'acceptation) : memes donnees, memes
 * octets.
 *
 * <p>Markdown reconnu (celui du modele valide) : titres {@code #} et {@code ##}, tableaux,
 * paragraphes ; les marques de gras et d'italique sont retirees.
 */
public final class ContratPdf {

    private static final Pattern PIECE = Pattern.compile("^# ([A-D])\\. (.+)$");

    private ContratPdf() {}

    public record Entete(String denominationCabinet, String reference, String versionCode, LocalDate accepteLe,
                         List<String> manquants) {}

    public static byte[] generer(String texte, Entete e) {
        JurikaPdfDocument doc = JurikaPdfTheme.newDocument();
        // En-tete : l'Editeur tel que le nomme le texte valide (pas de donnee de repli).
        doc.header(new CabinetIdentity("Maghreb Consulting", null, null,
                        "\u00c9diteur de la plateforme JURIKA", null, null, null, null, null, null),
                "Contrat d\u2019abonnement JURIKA",
                "Exemplaire du Cabinet \u2014 " + e.denominationCabinet(),
                "R\u00e9f\u00e9rence " + e.reference() + "   \u00b7   Conditions g\u00e9n\u00e9rales, version "
                        + e.versionCode() + "   \u00b7   Accept\u00e9 en ligne le " + ContratRedaction.date(e.accepteLe()))
                .dateEdition(e.accepteLe());
        if (!e.manquants().isEmpty()) {
            doc.note("Mentions non renseign\u00e9es au moment de l\u2019acceptation : "
                    + String.join(", ", e.manquants()) + ". Elles figurent dans le texte sous la forme "
                    + "\u00ab [non renseign\u00e9 : \u2026] \u00bb.");
        }
        List<String> lignes = List.of(texte.split("\n", -1));
        int i = 0;
        StringBuilder paragraphe = new StringBuilder();
        while (i < lignes.size()) {
            String l = lignes.get(i);
            if (l.isBlank()) {
                vider(doc, paragraphe);
                i++;
                continue;
            }
            if (l.startsWith("|")) {
                vider(doc, paragraphe);
                List<String> table = new ArrayList<>();
                while (i < lignes.size() && lignes.get(i).startsWith("|")) {
                    table.add(lignes.get(i));
                    i++;
                }
                tableau(doc, table);
                continue;
            }
            if (l.startsWith("## ")) {
                vider(doc, paragraphe);
                doc.subheading(propre(l.substring(3)));
            } else if (l.startsWith("# ")) {
                vider(doc, paragraphe);
                Matcher m = PIECE.matcher(l);
                if (m.matches()) {
                    doc.sectionTitle(m.group(1), propre(m.group(2)));
                } else {
                    doc.sectionTitle(propre(l.substring(2)));
                }
            } else {
                if (paragraphe.length() > 0) paragraphe.append(' ');
                paragraphe.append(l.trim());
            }
            i++;
        }
        vider(doc, paragraphe);
        return doc.finish();
    }

    private static void vider(JurikaPdfDocument doc, StringBuilder paragraphe) {
        if (paragraphe.length() > 0) {
            doc.paragraph(propre(paragraphe.toString()));
            paragraphe.setLength(0);
        }
    }

    private static void tableau(JurikaPdfDocument doc, List<String> lignes) {
        List<List<String>> rangees = new ArrayList<>();
        for (String l : lignes) {
            String t = l.trim();
            if (t.matches("^\\|[\\s:|-]+\\|$")) continue; // ligne de separation
            String coeur = t.substring(1, t.endsWith("|") ? t.length() - 1 : t.length());
            List<String> cellules = new ArrayList<>();
            for (String c : coeur.split("\\|", -1)) cellules.add(propre(c.trim()));
            rangees.add(cellules);
        }
        if (rangees.isEmpty()) return;
        doc.espaceDisponible(90f); // en-tete et premieres lignes ensemble
        List<String> entete = rangees.get(0);
        PdfTable table = new PdfTable();
        for (int c = 0; c < entete.size(); c++) {
            table.column(entete.get(c), c == entete.size() - 1 && entete.size() == 2 ? 2f : 1f, PdfTable.Align.LEFT);
        }
        for (List<String> r : rangees.subList(1, rangees.size())) {
            PdfTable.Cell[] cells = new PdfTable.Cell[entete.size()];
            for (int c = 0; c < entete.size(); c++) {
                cells[c] = PdfTable.Cell.of(c < r.size() ? r.get(c) : "");
            }
            table.row(cells);
        }
        doc.table(table);
    }

    /** Retire les marques Markdown de gras et d'italique. */
    static String propre(String s) {
        return s.replace("**", "").replaceAll("(?<![\\w*])\\*([^*]+)\\*(?![\\w*])", "$1");
    }
}
