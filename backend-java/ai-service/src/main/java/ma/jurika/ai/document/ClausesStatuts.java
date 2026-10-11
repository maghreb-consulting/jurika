package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lot L4d (E10), decision du directeur du 11 octobre 2026 : clauses particulieres des statuts.
 * L'employe choisit l'emplacement (apres l'article n), le titre et le contenu ; la clause
 * devient un article du document genere, la numerotation des articles suivants et les renvois
 * internes ("l'article 14 ci-dessus", "l'article 15 des presents statuts") sont mis a jour.
 * Quand la mise a jour n'est pas sure, la generation est refusee avec la raison :
 * <ul>
 *   <li>articles non numerotes "ARTICLE n -- titre", ou numerotation irreguliere ;</li>
 *   <li>emplacement hors des articles du document ;</li>
 *   <li>renvoi a un article decale qui ne dit pas s'il vise les statuts ou un texte de loi ;</li>
 *   <li>numero de renvoi coupe entre deux mises en forme (non modifiable sans risque).</li>
 * </ul>
 * Le gabarit n'est jamais modifie : seul le document rendu l'est, de facon deterministe
 * (meme document, memes clauses : memes octets).
 */
public final class ClausesStatuts {

    public static final String PREFIXE = "APRES_ARTICLE:";

    public record Article(int numero, String titre) {}

    static final Pattern TITRE = Pattern.compile("^ARTICLE\\s+(\\d+)\\s*[\u2014\u2013-]\\s*(.+)$");
    private static final Pattern RENVOI = Pattern.compile(
            "\\b(?:l'|l\u2019)?articles?\\s+(\\d+)((?:\\s*(?:,|et|\u00e0|au)\\s*\\d+)*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOMBRE = Pattern.compile("\\d+");
    private static final Pattern INTERNE = Pattern.compile(
            "^[^.;]{0,40}?(ci-dessus|ci-dessous|ci-apr\u00e8s|des pr\u00e9sents statuts|des statuts)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTERNE = Pattern.compile(
            "^[^.;]{0,60}?(de la loi|du code|du dahir|de la loi n)", Pattern.CASE_INSENSITIVE);

    private ClausesStatuts() {
    }

    /** Articles numerotes d'un document (gabarit ou document rendu), dans l'ordre. */
    public static List<Article> articles(byte[] docx) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            List<Article> out = new ArrayList<>();
            for (XWPFParagraph p : doc.getParagraphs()) {
                Matcher m = TITRE.matcher(p.getText().trim());
                if (m.matches()) out.add(new Article(Integer.parseInt(m.group(1)), m.group(2).trim()));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Document illisible", e);
        }
    }

    /** Insere les clauses (emplacement "APRES_ARTICLE:n") dans le document rendu. */
    public static byte[] inserer(String templateCode, byte[] docx, List<Map<String, Object>> clauses) {
        if (clauses == null || clauses.isEmpty()) return docx;
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<XWPFParagraph> ps = doc.getParagraphs();
            List<Integer> titres = new ArrayList<>();
            for (int i = 0; i < ps.size(); i++) {
                Matcher m = TITRE.matcher(ps.get(i).getText().trim());
                if (m.matches()) {
                    int attendu = titres.size() + 1;
                    int lu = Integer.parseInt(m.group(1));
                    if (lu != attendu) {
                        throw refus(templateCode, "la num\u00e9rotation des articles du document est irr\u00e9guli\u00e8re "
                                + "(article " + lu + " \u00e0 la place de l'article " + attendu + ") : la clause ne peut pas "
                                + "\u00eatre plac\u00e9e sans risque pour les renvois.");
                    }
                    titres.add(i);
                }
            }
            int n = titres.size();
            if (n == 0) {
                throw refus(templateCode, "le document ne comporte pas d'articles num\u00e9rot\u00e9s \u00ab ARTICLE n \u2014 titre \u00bb : "
                        + "la clause ne peut pas \u00eatre plac\u00e9e.");
            }
            // Emplacements choisis, controles.
            List<int[]> places = new ArrayList<>(); // {apres, rangDansLaListe}
            for (int j = 0; j < clauses.size(); j++) {
                Map<String, Object> c = clauses.get(j);
                int apres = apres(c.get("emplacement"));
                if (apres < 1 || apres > n) {
                    throw refus(templateCode, "la clause \u00ab " + c.get("titre") + " \u00bb doit se placer apr\u00e8s un article "
                            + "du document (1 \u00e0 " + n + ").");
                }
                places.add(new int[]{apres, j});
            }
            int[] decalage = new int[n + 2];
            for (int k = 1; k <= n + 1; k++) {
                int d = 0;
                for (int[] p : places) if (p[0] < k) d++;
                decalage[k] = d;
            }

            // 1. Renvois internes : verifies avant toute modification, puis mis a jour.
            List<XWPFParagraph> tous = tousLesParagraphes(doc);
            List<Remplacement> remplacements = new ArrayList<>();
            for (XWPFParagraph p : tous) {
                String texte = p.getText();
                if (TITRE.matcher(texte.trim()).matches()) continue;
                Matcher m = RENVOI.matcher(texte);
                while (m.find()) {
                    String suite = texte.substring(m.end());
                    boolean interne = INTERNE.matcher(suite).find();
                    boolean externe = !interne && EXTERNE.matcher(suite).find();
                    Matcher nb = NOMBRE.matcher(m.group());
                    while (nb.find()) {
                        int vise = Integer.parseInt(nb.group());
                        int nouveau = vise <= n ? vise + decalage[vise] : vise;
                        if (externe || nouveau == vise) continue;
                        if (!interne) {
                            throw refus(templateCode, "le renvoi \u00ab \u2026" + extrait(texte, m.start()) + "\u2026 \u00bb ne dit pas s'il "
                                    + "vise un article des statuts ou un texte de loi : sa num\u00e9rotation ne peut pas \u00eatre mise "
                                    + "\u00e0 jour automatiquement. Choisissez un autre emplacement pour la clause.");
                        }
                        remplacements.add(new Remplacement(p, m.start() + nb.start(), nb.group().length(), String.valueOf(nouveau)));
                    }
                }
            }
            for (int k = 1; k <= n; k++) {
                if (decalage[k] == 0) continue;
                XWPFParagraph titre = ps.get(titres.get(k - 1));
                Matcher m = NOMBRE.matcher(titre.getText());
                if (m.find()) remplacements.add(new Remplacement(titre, m.start(), m.group().length(), String.valueOf(k + decalage[k])));
            }
            for (Remplacement r : remplacements) r.verifier(templateCode);
            // Appliques paragraphe par paragraphe, de la fin vers le debut (positions stables).
            java.util.Map<XWPFParagraph, List<Remplacement>> parParagraphe = new java.util.IdentityHashMap<>();
            for (Remplacement r : remplacements) parParagraphe.computeIfAbsent(r.paragraphe, x -> new ArrayList<>()).add(r);
            for (List<Remplacement> l : parParagraphe.values()) {
                l.sort((a, b) -> Integer.compare(b.debut, a.debut));
                for (Remplacement r : l) r.appliquer();
            }

            // 2. Insertion des clauses, chacune apres son article (ordre de saisie conserve).
            for (int k = 1; k <= n; k++) {
                List<Map<String, Object>> ici = new ArrayList<>();
                for (int[] p : places) if (p[0] == k) ici.add(clauses.get(p[1]));
                if (ici.isEmpty()) continue;
                XWPFParagraph titreK = ps.get(titres.get(k - 1));
                XWPFParagraph corpsK = titres.get(k - 1) + 1 < ps.size() ? ps.get(titres.get(k - 1) + 1) : titreK;
                XWPFParagraph borne = k < n ? ps.get(titres.get(k)) : finDuDernierArticle(templateCode, ps, titres.get(n - 1), titreK);
                int numero = k + decalage[k] + 1;
                for (Map<String, Object> c : ici) {
                    XWPFParagraph t = inserAvant(doc, borne, titreK);
                    ecrire(t, "ARTICLE " + numero + " \u2014 " + String.valueOf(c.get("titre")).trim().toUpperCase(Locale.FRENCH));
                    for (String ligne : String.valueOf(c.get("texte")).split("\\r?\\n")) {
                        if (ligne.isBlank()) continue;
                        ecrire(inserAvant(doc, borne, corpsK), ligne.trim());
                    }
                    numero++;
                }
            }
            doc.write(out);
            return ZipHorodatage.figer(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Document illisible", e);
        }
    }

    static int apres(Object emplacement) {
        String e = emplacement == null ? "" : String.valueOf(emplacement).trim();
        if (!e.startsWith(PREFIXE)) return -1;
        try {
            return Integer.parseInt(e.substring(PREFIXE.length()).trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Fin du dernier article : avant le prochain titre de meme style ; sinon refus (pas de supposition). */
    private static XWPFParagraph finDuDernierArticle(String templateCode, List<XWPFParagraph> ps, int indexTitre,
                                                     XWPFParagraph titre) {
        String style = titre.getStyleID();
        for (int i = indexTitre + 1; i < ps.size(); i++) {
            XWPFParagraph p = ps.get(i);
            String s = p.getStyleID();
            boolean titreDeSection = s != null && (s.equals(style) || s.toLowerCase(Locale.ROOT).contains("titre")
                    || s.toLowerCase(Locale.ROOT).contains("heading"));
            if (titreDeSection && !p.getText().isBlank()) return p;
        }
        throw refus(templateCode, "la fin du dernier article n'est pas rep\u00e9rable dans le document : placez la clause "
                + "apr\u00e8s un article qui n'est pas le dernier.");
    }

    private static XWPFParagraph inserAvant(XWPFDocument doc, XWPFParagraph borne, XWPFParagraph modele) {
        XmlCursor curseur = borne.getCTP().newCursor();
        XWPFParagraph p = doc.insertNewParagraph(curseur);
        curseur.dispose();
        p.getCTP().set(modele.getCTP().copy());
        // Relu sur le XML copie : l'objet insere ne connait pas les runs copies.
        return new XWPFParagraph(p.getCTP(), doc);
    }

    /** Remplace le texte d'un paragraphe copie : premier run garde (mise en forme), les autres retires. */
    private static void ecrire(XWPFParagraph p, String texte) {
        List<XWPFRun> runs = p.getRuns();
        for (int i = runs.size() - 1; i >= 1; i--) p.removeRun(i);
        if (p.getRuns().isEmpty()) p.createRun();
        p.getRuns().get(0).setText(texte, 0);
        // Autres textes du run (sauts) neutralises.
        XWPFRun r = p.getRuns().get(0);
        while (r.getCTR().sizeOfTArray() > 1) r.getCTR().removeT(1);
    }

    private static List<XWPFParagraph> tousLesParagraphes(XWPFDocument doc) {
        List<XWPFParagraph> out = new ArrayList<>(doc.getParagraphs());
        for (XWPFTable t : doc.getTables()) {
            for (XWPFTableRow row : t.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) out.addAll(cell.getParagraphs());
            }
        }
        return out;
    }

    private static String extrait(String texte, int debut) {
        int a = Math.max(0, debut - 20);
        int b = Math.min(texte.length(), debut + 60);
        return texte.substring(a, b).trim();
    }

    private static ClauseLibreImpossibleException refus(String templateCode, String raison) {
        return new ClauseLibreImpossibleException(templateCode, "Clause particuli\u00e8re impossible \u00e0 placer : " + raison);
    }

    /** Un nombre a remplacer, situe par sa position dans le texte du paragraphe. */
    private static final class Remplacement {
        final XWPFParagraph paragraphe;
        final int debut;
        final int longueur;
        final String nouveau;
        XWPFRun run;
        int debutDansRun;

        Remplacement(XWPFParagraph paragraphe, int debut, int longueur, String nouveau) {
            this.paragraphe = paragraphe;
            this.debut = debut;
            this.longueur = longueur;
            this.nouveau = nouveau;
        }

        /** Le nombre doit tenir dans un seul run, sinon la mise a jour n'est pas sure. */
        void verifier(String templateCode) {
            int pos = 0;
            for (XWPFRun r : paragraphe.getRuns()) {
                String t = r.text();
                if (debut >= pos && debut + longueur <= pos + t.length()) {
                    run = r;
                    debutDansRun = debut - pos;
                    return;
                }
                pos += t.length();
            }
            throw refus(templateCode, "le num\u00e9ro \u00ab " + paragraphe.getText().substring(debut, debut + longueur)
                    + " \u00bb du passage \u00ab \u2026" + extrait(paragraphe.getText(), debut) + "\u2026 \u00bb est coup\u00e9 entre deux "
                    + "mises en forme : il ne peut pas \u00eatre mis \u00e0 jour sans risque.");
        }

        void appliquer() {
            String t = run.text();
            String nouveauTexte = t.substring(0, debutDansRun) + nouveau + t.substring(debutDansRun + longueur);
            while (run.getCTR().sizeOfTArray() > 1) run.getCTR().removeT(1);
            run.setText(nouveauTexte, 0);
        }
    }
}
