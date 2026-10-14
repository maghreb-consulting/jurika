package ma.jurika.ai.document.apercu;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGridCol;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) : mise en page d'un acte .docx pour l'APERCU,
 * sans LibreOffice.
 *
 * <p>Le contenu de l'acte (paragraphes, gras, italique, souligne, tailles, alignements,
 * retraits, listes numerotees, tableaux, images, en-tetes et pieds de page, sauts de page) est
 * compose en pages PDF par PDFBox avec la police embarquee Inter, puis rendu en images par
 * {@code RenduApercu} : le PDF intermediaire ne quitte jamais le serveur. La mise en page est
 * une APPROXIMATION fidele au texte, pas une reproduction au pixel de Word (police unique,
 * taquets de tabulation simplifies) : l'apercu sert a relire l'acte, le .docx reste la source.
 *
 * <p>Deterministe : memes octets d'entree, memes pages (aucune date, aucune police du systeme).
 */
public final class MiseEnPageDocx {

    private static final float TWIP = 1f / 20f;
    private static final float CORPS_DEFAUT = 11f;
    private static final float TABULATION = 24f;
    private static final float MARGE_CELLULE = 4f;
    private static final float RETRAIT_LISTE = 18f;
    private static final Pattern GRAS = Pattern.compile("<w:b(\\s+w:val=\"(?:1|true|on)\")?\\s*/>");
    private static final Pattern ITALIQUE = Pattern.compile("<w:i(\\s+w:val=\"(?:1|true|on)\")?\\s*/>");
    private static final Pattern TAILLE = Pattern.compile("<w:sz\\s+w:val=\"(\\d+)\"");
    private static final Pattern JUSTIFICATION = Pattern.compile("<w:jc\\s+w:val=\"(\\w+)\"");
    private static final Pattern AVANT = Pattern.compile("<w:spacing[^>]*\\sw:before=\"(\\d+)\"");
    private static final Pattern APRES = Pattern.compile("<w:spacing[^>]*\\sw:after=\"(\\d+)\"");
    private static final Pattern RETRAIT_G = Pattern.compile("<w:ind[^>]*\\sw:(?:left|start)=\"(-?\\d+)\"");

    /** Fragment d'une ligne : un mot, une espace, ou une image. */
    private record Fragment(String texte, boolean gras, boolean italique, boolean souligne, float corps,
                            Color couleur, float largeur, float hauteur, PDImageXObject image, boolean espace) {}

    private record Ligne(List<Fragment> fragments, float largeur, float hauteur, boolean finParagraphe,
                         boolean sautDePageApres) {}

    private record Paragraphe(List<Ligne> lignes, float avant, float apres, ParagraphAlignment alignement,
                              float retraitGauche, float retraitDroit, boolean sautDePageAvant) {
        float hauteur() {
            float h = avant + apres;
            for (Ligne l : lignes) h += l.hauteur();
            return h;
        }
    }

    /** Jeton du texte avant le decoupage en lignes. */
    private record Jeton(String texte, boolean gras, boolean italique, boolean souligne, float corps, Color couleur,
                         XWPFPicture image, int type) {
        static final int MOT = 0;
        static final int ESPACE = 1;
        static final int TAB = 2;
        static final int LIGNE = 3;
        static final int PAGE = 4;
        static final int IMAGE = 5;
    }

    private final XWPFDocument docx;
    private final PDDocument pdf;
    private final PDType0Font regulier;
    private final PDType0Font gras;
    private final Map<String, String> stylesXml = new HashMap<>();
    private final Map<String, int[]> compteurs = new HashMap<>();
    private final Map<Integer, Boolean> glyphes = new HashMap<>();
    private final float corpsDefaut;
    private final float apresDefaut;

    private float pageL;
    private float pageH;
    private float margeG;
    private float margeD;
    private float margeHaut;
    private float margeBas;
    private float hautCorps;
    private float basCorps;
    private List<Paragraphe> entete = List.of();
    private List<Paragraphe> pied = List.of();
    private PDPageContentStream cs;
    private float y;
    private boolean pageVierge;

    private MiseEnPageDocx(XWPFDocument docx, PDDocument pdf) throws IOException {
        this.docx = docx;
        this.pdf = pdf;
        this.regulier = police(pdf, "/fonts/Inter-Regular.ttf");
        this.gras = police(pdf, "/fonts/Inter-Bold.ttf");
        XWPFStyles styles = docx.getStyles();
        Double d = null;
        int a = -1;
        if (styles != null) {
            try {
                if (styles.getDefaultRunStyle() != null) d = styles.getDefaultRunStyle().getFontSizeAsDouble();
                if (styles.getDefaultParagraphStyle() != null) a = styles.getDefaultParagraphStyle().getSpacingAfter();
            } catch (RuntimeException ignoree) {
                // valeurs par defaut du document absentes : celles de la mise en page s'appliquent
            }
        }
        this.corpsDefaut = d == null || d <= 0 ? CORPS_DEFAUT : d.floatValue();
        this.apresDefaut = a > 0 ? a * TWIP : 0f;
    }

    /** Compose le .docx en PDF (intermediaire de l'apercu, jamais transmis au navigateur). */
    public static byte[] enPdf(byte[] octetsDocx) {
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(octetsDocx));
             PDDocument pdf = new PDDocument()) {
            MiseEnPageDocx m = new MiseEnPageDocx(docx, pdf);
            m.composer();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            throw new ma.jurika.common.trial.RenduApercu.ApercuImpossibleException(
                    "Le document Word n\u2019a pas pu \u00eatre mis en page pour l\u2019aper\u00e7u.", e);
        }
    }

    private static PDType0Font police(PDDocument pdf, String ressource) throws IOException {
        try (InputStream in = MiseEnPageDocx.class.getResourceAsStream(ressource)) {
            if (in == null) throw new IOException("Police absente : " + ressource);
            return PDType0Font.load(pdf, in, true);
        }
    }

    // =====================================================================
    //  Composition
    // =====================================================================

    private void composer() throws IOException {
        geometrie();
        XWPFHeaderFooterPolicy politique = docx.getHeaderFooterPolicy();
        float largeur = pageL - margeG - margeD;
        if (politique != null) {
            XWPFHeaderFooter h = politique.getDefaultHeader() != null ? politique.getDefaultHeader()
                    : politique.getFirstPageHeader();
            XWPFHeaderFooter f = politique.getDefaultFooter() != null ? politique.getDefaultFooter()
                    : politique.getFirstPageFooter();
            if (h != null) entete = paragraphes(h.getBodyElements(), largeur);
            if (f != null) pied = paragraphes(f.getBodyElements(), largeur);
        }
        float hEntete = 0;
        for (Paragraphe p : entete) hEntete += p.hauteur();
        float hPied = 0;
        for (Paragraphe p : pied) hPied += p.hauteur();
        hautCorps = pageH - Math.max(margeHaut, 28f + hEntete + 6f);
        basCorps = Math.max(margeBas, 28f + hPied + 6f);

        nouvellePage();
        for (IBodyElement e : docx.getBodyElements()) {
            if (e instanceof XWPFParagraph p) {
                dessiner(paragraphe(p, largeur), margeG);
            } else if (e instanceof XWPFTable t) {
                tableau(t, margeG, largeur);
            } else if (e instanceof XWPFSDT sdt) {
                dessiner(paragrapheTexte(sdt.getContent().getText(), largeur), margeG);
            }
        }
        if (cs != null) cs.close();
    }

    private void geometrie() {
        pageL = PDRectangle.A4.getWidth();
        pageH = PDRectangle.A4.getHeight();
        margeG = margeD = margeHaut = margeBas = 1417 * TWIP;
        CTSectPr s = docx.getDocument().getBody().getSectPr();
        if (s == null) return;
        if (s.getPgSz() != null) {
            float l = twips(s.getPgSz().getW());
            float h = twips(s.getPgSz().getH());
            if (l > 100 && h > 100) {
                pageL = l;
                pageH = h;
            }
        }
        if (s.getPgMar() != null) {
            margeG = garde(twips(s.getPgMar().getLeft()), margeG);
            margeD = garde(twips(s.getPgMar().getRight()), margeD);
            margeHaut = garde(Math.abs(twips(s.getPgMar().getTop())), margeHaut);
            margeBas = garde(Math.abs(twips(s.getPgMar().getBottom())), margeBas);
        }
    }

    private static float garde(float v, float defaut) {
        return v > 0 && v < 400 ? v : defaut;
    }

    private static float twips(Object v) {
        if (v == null) return -1;
        try {
            return new BigInteger(v.toString()).floatValue() * TWIP;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void nouvellePage() throws IOException {
        if (cs != null) cs.close();
        PDPage page = new PDPage(new PDRectangle(pageL, pageH));
        pdf.addPage(page);
        cs = new PDPageContentStream(pdf, page);
        float yh = pageH - 28f;
        for (Paragraphe p : entete) yh = dessinerA(p, margeG, yh);
        float hPied = 0;
        for (Paragraphe p : pied) hPied += p.hauteur();
        float yp = 28f + hPied;
        for (Paragraphe p : pied) yp = dessinerA(p, margeG, yp);
        y = hautCorps;
        pageVierge = true;
    }

    private List<Paragraphe> paragraphes(List<IBodyElement> elements, float largeur) {
        List<Paragraphe> r = new ArrayList<>();
        for (IBodyElement e : elements) {
            if (e instanceof XWPFParagraph p) {
                r.add(paragraphe(p, largeur));
            } else if (e instanceof XWPFTable t) {
                // Tableau d'en-tete ou de pied : son texte, cellule par cellule.
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell c : row.getTableCells()) {
                        r.addAll(paragraphes(c.getBodyElements(), largeur));
                    }
                }
            } else if (e instanceof XWPFSDT sdt) {
                r.add(paragrapheTexte(sdt.getContent().getText(), largeur));
            }
        }
        return r;
    }

    // =====================================================================
    //  Paragraphes
    // =====================================================================

    private Paragraphe paragrapheTexte(String texte, float largeur) {
        List<Jeton> jetons = new ArrayList<>();
        decouper(texte == null ? "" : texte, false, false, false, corpsDefaut, Color.BLACK, jetons);
        return new Paragraphe(lignes(jetons, largeur, 0, 1f, -1f, -1f), 0, apresDefaut, ParagraphAlignment.LEFT,
                0, 0, false);
    }

    private Paragraphe paragraphe(XWPFParagraph p, float largeurDispo) {
        String style = styleXml(p.getStyleID());
        boolean grasStyle = GRAS.matcher(style).find();
        boolean italiqueStyle = ITALIQUE.matcher(style).find();
        float corpsStyle = valeur(TAILLE, style) > 0 ? valeur(TAILLE, style) / 2f : corpsDefaut;

        ParagraphAlignment alignement = ParagraphAlignment.LEFT;
        boolean jcLocal = p.getCTP().getPPr() != null && p.getCTP().getPPr().isSetJc();
        if (jcLocal) {
            alignement = p.getAlignment();
        } else {
            Matcher m = JUSTIFICATION.matcher(style);
            if (m.find()) alignement = alignementDe(m.group(1));
        }

        float retraitG = p.getIndentationLeft() >= 0 ? p.getIndentationLeft() * TWIP
                : Math.max(0, valeur(RETRAIT_G, style) * TWIP);
        float retraitD = Math.max(0, p.getIndentationRight()) * TWIP;
        float premiere = 0;
        if (p.getIndentationFirstLine() > 0) premiere = p.getIndentationFirstLine() * TWIP;
        if (p.getIndentationHanging() > 0) premiere = -p.getIndentationHanging() * TWIP;

        float avant = p.getSpacingBefore() >= 0 ? p.getSpacingBefore() * TWIP : Math.max(0, valeur(AVANT, style) * TWIP);
        float apres = p.getSpacingAfter() >= 0 ? p.getSpacingAfter() * TWIP
                : valeur(APRES, style) >= 0 ? valeur(APRES, style) * TWIP : apresDefaut;

        float multiple = 1f;
        float exacte = -1f;
        float auMoins = -1f;
        double entre = p.getSpacingBetween();
        if (entre > 0) {
            LineSpacingRule regle = p.getSpacingLineRule();
            if (regle == LineSpacingRule.EXACT) exacte = (float) entre;
            else if (regle == LineSpacingRule.AT_LEAST) auMoins = (float) entre;
            else multiple = (float) Math.max(0.8, Math.min(3.0, entre));
        }

        List<Jeton> jetons = new ArrayList<>();
        String puce = numerotation(p);
        if (puce != null) {
            if (p.getIndentationLeft() < 0) {
                int niveau = p.getNumIlvl() == null ? 0 : p.getNumIlvl().intValue();
                retraitG = RETRAIT_LISTE * (niveau + 1);
                premiere = -RETRAIT_LISTE;
            }
            decouper(puce, grasStyle, false, false, corpsStyle, Color.BLACK, jetons);
            jetons.add(new Jeton(" ", false, false, false, corpsStyle, Color.BLACK, null, Jeton.TAB));
        }
        for (IRunElement ir : p.getIRuns()) {
            if (ir instanceof XWPFRun r) {
                run(r, grasStyle, italiqueStyle, corpsStyle, jetons);
            } else if (ir instanceof XWPFSDT sdt) {
                decouper(sdt.getContent().getText(), grasStyle, italiqueStyle, false, corpsStyle, Color.BLACK, jetons);
            }
        }
        float largeur = Math.max(40f, largeurDispo - retraitG - retraitD);
        List<Ligne> lignes = lignes(jetons, largeur, premiere, multiple, exacte, auMoins);
        if (lignes.isEmpty()) {
            float h = corpsStyle * 1.2f * multiple;
            lignes = List.of(new Ligne(List.of(), 0, exacte > 0 ? exacte : h, true, false));
        }
        return new Paragraphe(lignes, avant, apres, alignement, retraitG, retraitD, p.isPageBreak());
    }

    private static ParagraphAlignment alignementDe(String jc) {
        return switch (jc.toLowerCase(Locale.ROOT)) {
            case "center" -> ParagraphAlignment.CENTER;
            case "right", "end" -> ParagraphAlignment.RIGHT;
            case "both", "distribute" -> ParagraphAlignment.BOTH;
            default -> ParagraphAlignment.LEFT;
        };
    }

    private static int valeur(Pattern motif, String xml) {
        Matcher m = motif.matcher(xml);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** XML du style de paragraphe et de ses styles de base (le style "Normal" sans identifiant). */
    private String styleXml(String id) {
        String cle = id == null ? "" : id;
        return stylesXml.computeIfAbsent(cle, k -> {
            XWPFStyles styles = docx.getStyles();
            if (styles == null) return "";
            StringBuilder sb = new StringBuilder();
            XWPFStyle s = k.isEmpty() ? null : styles.getStyle(k);
            if (s == null) s = styles.getStyle("Normal");
            int garde = 0;
            while (s != null && garde++ < 10) {
                sb.append(s.getCTStyle().xmlText());
                String base = s.getBasisStyleID();
                s = base == null ? null : styles.getStyle(base);
            }
            return sb.toString();
        });
    }

    private String numerotation(XWPFParagraph p) {
        BigInteger numId = p.getNumID();
        if (numId == null) return null;
        String fmt;
        String texte;
        try {
            fmt = p.getNumFmt();
            texte = p.getNumLevelText();
        } catch (RuntimeException e) {
            return null;
        }
        int niveau = p.getNumIlvl() == null ? 0 : Math.min(8, p.getNumIlvl().intValue());
        int[] c = compteurs.computeIfAbsent(numId.toString(), k -> new int[9]);
        c[niveau]++;
        for (int i = niveau + 1; i < 9; i++) c[i] = 0;
        if (fmt == null || "bullet".equals(fmt)) return "\u2022";
        if ("none".equals(fmt)) return null;
        if (texte == null) texte = "%" + (niveau + 1) + ".";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texte.length(); i++) {
            char ch = texte.charAt(i);
            if (ch == '%' && i + 1 < texte.length() && Character.isDigit(texte.charAt(i + 1))) {
                int n = texte.charAt(i + 1) - '1';
                int v = n >= 0 && n < 9 ? Math.max(1, c[n]) : 1;
                sb.append(n == niveau ? formater(v, fmt) : String.valueOf(v));
                i++;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String formater(int v, String fmt) {
        return switch (fmt) {
            case "lowerLetter" -> String.valueOf((char) ('a' + (v - 1) % 26));
            case "upperLetter" -> String.valueOf((char) ('A' + (v - 1) % 26));
            case "lowerRoman" -> romain(v).toLowerCase(Locale.ROOT);
            case "upperRoman" -> romain(v);
            default -> String.valueOf(v);
        };
    }

    private static String romain(int v) {
        int[] n = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] s = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length; i++) {
            while (v >= n[i]) {
                sb.append(s[i]);
                v -= n[i];
            }
        }
        return sb.toString();
    }

    private void run(XWPFRun r, boolean grasStyle, boolean italiqueStyle, float corpsStyle, List<Jeton> jetons) {
        boolean g = r.isBold() || grasStyle;
        boolean i = r.isItalic() || italiqueStyle;
        boolean s = r.getUnderline() != null && r.getUnderline() != UnderlinePatterns.NONE;
        Double taille = r.getFontSizeAsDouble();
        float corps = taille != null && taille > 0 ? taille.floatValue() : corpsStyle;
        try {
            if (r.getVerticalAlignment() != null && !"baseline".equals(r.getVerticalAlignment().toString())) {
                corps = corps * 0.7f;
            }
        } catch (RuntimeException sansAlignement) {
            // pas d'alignement vertical declare : ligne de base
        }
        Color couleur = couleur(r.getColor());
        Iterator<XWPFPicture> images = r.getEmbeddedPictures().iterator();
        try (XmlCursor c = r.getCTR().newCursor()) {
            if (!c.toFirstChild()) return;
            do {
                String nom = c.getName().getLocalPart();
                switch (nom) {
                    case "t" -> decouper(c.getTextValue(), g, i, s, corps, couleur, jetons);
                    case "tab", "ptab" -> jetons.add(new Jeton("", g, i, s, corps, couleur, null, Jeton.TAB));
                    case "br" -> {
                        String type = c.getAttributeText(new javax.xml.namespace.QName(
                                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "type"));
                        jetons.add(new Jeton("", g, i, s, corps, couleur, null,
                                "page".equals(type) ? Jeton.PAGE : Jeton.LIGNE));
                    }
                    case "cr" -> jetons.add(new Jeton("", g, i, s, corps, couleur, null, Jeton.LIGNE));
                    case "noBreakHyphen" -> decouper("-", g, i, s, corps, couleur, jetons);
                    case "drawing", "pict" -> {
                        if (images.hasNext()) {
                            jetons.add(new Jeton("", g, i, s, corps, couleur, images.next(), Jeton.IMAGE));
                        }
                    }
                    default -> {
                        // rPr, fldChar, instrText (code de champ), delText... : rien a afficher
                    }
                }
            } while (c.toNextSibling());
        }
    }

    private static Color couleur(String hex) {
        if (hex == null || hex.length() != 6 || "auto".equalsIgnoreCase(hex)) return Color.BLACK;
        try {
            return new Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return Color.BLACK;
        }
    }

    private void decouper(String texte, boolean g, boolean i, boolean s, float corps, Color couleur, List<Jeton> jetons) {
        if (texte == null || texte.isEmpty()) return;
        StringBuilder mot = new StringBuilder();
        for (int k = 0; k < texte.length(); k++) {
            char ch = texte.charAt(k);
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                if (mot.length() > 0) {
                    jetons.add(new Jeton(mot.toString(), g, i, s, corps, couleur, null, Jeton.MOT));
                    mot.setLength(0);
                }
                int type = ch == ' ' ? Jeton.ESPACE : ch == '\t' ? Jeton.TAB : Jeton.LIGNE;
                if (ch == '\r' && k + 1 < texte.length() && texte.charAt(k + 1) == '\n') continue;
                jetons.add(new Jeton(" ", g, i, s, corps, couleur, null, type));
            } else {
                mot.append(ch);
            }
        }
        if (mot.length() > 0) jetons.add(new Jeton(mot.toString(), g, i, s, corps, couleur, null, Jeton.MOT));
    }

    // =====================================================================
    //  Lignes
    // =====================================================================

    private List<Ligne> lignes(List<Jeton> jetons, float largeur, float premiere, float multiple, float exacte,
                               float auMoins) {
        List<Ligne> lignes = new ArrayList<>();
        List<Fragment> courante = new ArrayList<>();
        float[] l = {0f};
        float limite = largeur - Math.max(0, premiere);
        boolean premiereLigne = true;
        for (Jeton j : jetons) {
            switch (j.type()) {
                case Jeton.LIGNE, Jeton.PAGE -> {
                    // Comme Word : une ligne terminee par un saut de ligne n'est pas justifiee.
                    lignes.add(fermer(courante, l[0], multiple, exacte, auMoins, j.corps(), true,
                            j.type() == Jeton.PAGE));
                    courante = new ArrayList<>();
                    l[0] = 0;
                    if (premiereLigne) {
                        premiereLigne = false;
                        limite = largeur;
                    }
                }
                case Jeton.ESPACE -> {
                    if (courante.isEmpty()) continue;
                    Fragment f = fragment(j, " ", true);
                    courante.add(f);
                    l[0] += f.largeur();
                }
                case Jeton.TAB -> {
                    float w = TABULATION - (l[0] % TABULATION);
                    if (w < 4f) w += TABULATION;
                    Fragment f = new Fragment(" ", j.gras(), j.italique(), false, j.corps(), j.couleur(), w,
                            j.corps(), null, false);
                    courante.add(f);
                    l[0] += w;
                }
                case Jeton.IMAGE -> {
                    Fragment f = image(j, largeur);
                    if (f == null) continue;
                    if (!courante.isEmpty() && l[0] + f.largeur() > limite) {
                        lignes.add(fermer(courante, l[0], multiple, exacte, auMoins, j.corps(), false, false));
                        courante = new ArrayList<>();
                        l[0] = 0;
                        premiereLigne = false;
                        limite = largeur;
                    }
                    courante.add(f);
                    l[0] += f.largeur();
                }
                default -> {
                    Fragment f = fragment(j, j.texte(), false);
                    if (l[0] + f.largeur() > limite && !courante.isEmpty()) {
                        retirerEspacesFinales(courante, l);
                        lignes.add(fermer(courante, l[0], multiple, exacte, auMoins, j.corps(), false, false));
                        courante = new ArrayList<>();
                        l[0] = 0;
                        if (premiereLigne) {
                            premiereLigne = false;
                            limite = largeur;
                        }
                    }
                    // Mot plus long que la ligne : coupe par caracteres.
                    while (f.largeur() > limite && f.texte().length() > 1) {
                        int n = coupe(f, limite - l[0]);
                        Fragment debut = fragment(j, f.texte().substring(0, n), false);
                        courante.add(debut);
                        lignes.add(fermer(courante, l[0] + debut.largeur(), multiple, exacte, auMoins, j.corps(), false,
                                false));
                        courante = new ArrayList<>();
                        l[0] = 0;
                        premiereLigne = false;
                        limite = largeur;
                        f = fragment(j, f.texte().substring(n), false);
                    }
                    courante.add(f);
                    l[0] += f.largeur();
                }
            }
        }
        if (!courante.isEmpty() || !lignes.isEmpty()) {
            retirerEspacesFinales(courante, l);
            Jeton dernier = jetons.isEmpty() ? null : jetons.get(jetons.size() - 1);
            lignes.add(fermer(courante, l[0], multiple, exacte, auMoins,
                    dernier == null ? corpsDefaut : dernier.corps(), true, false));
        }
        // La premiere ligne porte le retrait de premiere ligne (positif ou negatif).
        if (!lignes.isEmpty() && premiere != 0) {
            Ligne p = lignes.get(0);
            List<Fragment> f = new ArrayList<>();
            f.add(new Fragment("", false, false, false, 0, Color.BLACK, premiere, 0, null, false));
            f.addAll(p.fragments());
            lignes.set(0, new Ligne(f, p.largeur() + premiere, p.hauteur(), p.finParagraphe(), p.sautDePageApres()));
        }
        return lignes;
    }

    private static void retirerEspacesFinales(List<Fragment> fragments, float[] l) {
        while (!fragments.isEmpty() && fragments.get(fragments.size() - 1).espace()) {
            l[0] -= fragments.remove(fragments.size() - 1).largeur();
        }
    }

    private int coupe(Fragment f, float largeur) {
        PDType0Font police = f.gras() ? gras : regulier;
        int n = 1;
        while (n < f.texte().length() && mesure(police, f.texte().substring(0, n + 1), f.corps()) <= largeur) n++;
        return n;
    }

    private Ligne fermer(List<Fragment> fragments, float largeur, float multiple, float exacte, float auMoins,
                         float corpsVide, boolean fin, boolean saut) {
        float h = 0;
        for (Fragment f : fragments) h = Math.max(h, f.image() != null ? f.hauteur() : f.corps() * 1.2f * multiple);
        if (h == 0) h = corpsVide * 1.2f * multiple;
        if (exacte > 0) h = exacte;
        if (auMoins > 0) h = Math.max(h, auMoins);
        return new Ligne(List.copyOf(fragments), largeur, h, fin, saut);
    }

    private Fragment fragment(Jeton j, String texte, boolean espace) {
        String propre = propre(texte, j.gras() ? gras : regulier);
        float w = mesure(j.gras() ? gras : regulier, propre, j.corps());
        return new Fragment(propre, j.gras(), j.italique(), j.souligne(), j.corps(), j.couleur(), w, j.corps(), null,
                espace);
    }

    private Fragment image(Jeton j, float largeurMax) {
        try {
            byte[] octets = j.image().getPictureData().getData();
            PDImageXObject img = PDImageXObject.createFromByteArray(pdf, octets, "image");
            float l = (float) j.image().getWidth();
            float h = (float) j.image().getDepth();
            if (l <= 0 || h <= 0) {
                l = img.getWidth() * 0.75f;
                h = img.getHeight() * 0.75f;
            }
            if (l > largeurMax) {
                h = h * largeurMax / l;
                l = largeurMax;
            }
            return new Fragment("", false, false, false, j.corps(), Color.BLACK, l, h, img, false);
        } catch (IOException | RuntimeException e) {
            // Image illisible (format non pris en charge) : absente de l'apercu, le texte reste.
            return null;
        }
    }

    private float mesure(PDType0Font police, String texte, float corps) {
        try {
            return police.getStringWidth(texte) / 1000f * corps;
        } catch (IOException | IllegalArgumentException e) {
            return texte.length() * corps * 0.5f;
        }
    }

    /** Caracteres que la police ne sait pas dessiner remplaces (jamais d'echec du rendu). */
    private String propre(String texte, PDType0Font police) {
        StringBuilder sb = new StringBuilder(texte.length());
        texte.codePoints().forEach(cp -> {
            if (cp < 0x20) return;
            boolean ok = glyphes.computeIfAbsent(cp, k -> {
                try {
                    police.encode(new String(Character.toChars(k)));
                    return true;
                } catch (IOException | IllegalArgumentException e) {
                    return false;
                }
            });
            if (ok) {
                sb.appendCodePoint(cp);
            } else if (cp == 0x202F || cp == 0x2009) {
                sb.append(' ');
            } else {
                sb.append('?');
            }
        });
        return sb.toString();
    }

    // =====================================================================
    //  Dessin
    // =====================================================================

    private void dessiner(Paragraphe p, float x0) throws IOException {
        if (p.sautDePageAvant() && !pageVierge) nouvellePage();
        if (!pageVierge) y -= p.avant();
        for (Ligne l : p.lignes()) {
            if (y - l.hauteur() < basCorps && !pageVierge) nouvellePage();
            ligne(l, p, x0, y, pageL - margeG - margeD);
            y -= l.hauteur();
            pageVierge = false;
            if (l.sautDePageApres()) nouvellePage();
        }
        y -= p.apres();
    }

    /** Dessine un paragraphe d'en-tete ou de pied a partir de {@code yHaut} ; rend le y suivant. */
    private float dessinerA(Paragraphe p, float x0, float yHaut) throws IOException {
        float yy = yHaut - p.avant();
        for (Ligne l : p.lignes()) {
            ligne(l, p, x0, yy, pageL - margeG - margeD);
            yy -= l.hauteur();
        }
        return yy - p.apres();
    }

    private void ligne(Ligne l, Paragraphe p, float x0, float yHaut, float largeurZone) throws IOException {
        float largeur = largeurZone - p.retraitGauche() - p.retraitDroit();
        float x = x0 + p.retraitGauche();
        float extra = 0;
        int espaces = 0;
        switch (p.alignement()) {
            case CENTER -> x += Math.max(0, (largeur - l.largeur()) / 2f);
            case RIGHT, END -> x += Math.max(0, largeur - l.largeur());
            case BOTH, DISTRIBUTE -> {
                if (!l.finParagraphe() && !l.sautDePageApres()) {
                    for (Fragment f : l.fragments()) if (f.espace()) espaces++;
                    if (espaces > 0) extra = Math.max(0, (largeur - l.largeur()) / espaces);
                }
            }
            default -> { }
        }
        float base = yHaut - l.hauteur() * 0.78f;
        for (Fragment f : l.fragments()) {
            if (f.image() != null) {
                cs.drawImage(f.image(), x, yHaut - f.hauteur(), f.largeur(), f.hauteur());
            } else if (!f.texte().isBlank()) {
                cs.beginText();
                cs.setFont(f.gras() ? gras : regulier, f.corps());
                cs.setNonStrokingColor(f.couleur());
                cs.setTextMatrix(f.italique() ? new Matrix(1, 0, 0.2f, 1, x, base) : Matrix.getTranslateInstance(x, base));
                cs.showText(f.texte());
                cs.endText();
            }
            float w = f.largeur() + (f.espace() ? extra : 0);
            if (f.souligne() && f.image() == null) {
                cs.setStrokingColor(f.couleur());
                cs.setLineWidth(0.5f);
                cs.moveTo(x, base - 1.5f);
                cs.lineTo(x + w, base - 1.5f);
                cs.stroke();
            }
            x += w;
        }
    }

    // =====================================================================
    //  Tableaux
    // =====================================================================

    private void tableau(XWPFTable t, float x0, float largeur) throws IOException {
        List<Float> colonnes = colonnes(t, largeur);
        boolean bordures = bordures(t);
        for (XWPFTableRow row : t.getRows()) {
            List<XWPFTableCell> cellules = row.getTableCells();
            List<float[]> positions = new ArrayList<>();
            List<List<Paragraphe>> contenus = new ArrayList<>();
            int col = 0;
            float hauteur = 0;
            for (XWPFTableCell c : cellules) {
                int span = 1;
                CTTcPr pr = c.getCTTc().getTcPr();
                if (pr != null && pr.getGridSpan() != null && pr.getGridSpan().getVal() != null) {
                    span = Math.max(1, pr.getGridSpan().getVal().intValue());
                }
                boolean suite = pr != null && pr.getVMerge() != null
                        && (pr.getVMerge().getVal() == null || !"restart".equals(pr.getVMerge().getVal().toString()));
                float cx = x0;
                for (int k = 0; k < col && k < colonnes.size(); k++) cx += colonnes.get(k);
                float cw = 0;
                for (int k = col; k < col + span && k < colonnes.size(); k++) cw += colonnes.get(k);
                if (cw <= 0) cw = largeur / Math.max(1, cellules.size());
                col += span;
                List<Paragraphe> contenu = suite ? List.of() : paragraphes(c.getBodyElements(), cw - 2 * MARGE_CELLULE);
                float h = 2 * MARGE_CELLULE;
                for (Paragraphe p : contenu) h += p.hauteur();
                hauteur = Math.max(hauteur, h);
                positions.add(new float[]{cx, cw});
                contenus.add(contenu);
            }
            if (hauteur > hautCorps - basCorps) {
                // Ligne plus haute qu'une page : son contenu suit le fil du texte (rien n'est perdu).
                for (List<Paragraphe> contenu : contenus) for (Paragraphe p : contenu) dessiner(p, x0);
                continue;
            }
            if (y - hauteur < basCorps && !pageVierge) nouvellePage();
            for (int k = 0; k < positions.size(); k++) {
                float[] pos = positions.get(k);
                float yy = y - MARGE_CELLULE;
                for (Paragraphe p : contenus.get(k)) {
                    yy -= p.avant();
                    for (Ligne l : p.lignes()) {
                        ligne(l, p, pos[0] + MARGE_CELLULE, yy, pos[1] - 2 * MARGE_CELLULE);
                        yy -= l.hauteur();
                    }
                    yy -= p.apres();
                }
                if (bordures) {
                    cs.setStrokingColor(Color.GRAY);
                    cs.setLineWidth(0.5f);
                    cs.addRect(pos[0], y - hauteur, pos[1], hauteur);
                    cs.stroke();
                }
            }
            y -= hauteur;
            pageVierge = false;
        }
        y -= 4f;
    }

    private List<Float> colonnes(XWPFTable t, float largeur) {
        List<Float> r = new ArrayList<>();
        if (t.getCTTbl().getTblGrid() != null) {
            for (CTTblGridCol g : t.getCTTbl().getTblGrid().getGridColList()) {
                r.add(Math.max(0f, twips(g.getW())));
            }
        }
        float somme = 0;
        for (float f : r) somme += f;
        if (r.isEmpty() || somme <= 0) {
            int n = 1;
            for (XWPFTableRow row : t.getRows()) n = Math.max(n, row.getTableCells().size());
            r.clear();
            for (int i = 0; i < n; i++) r.add(largeur / n);
            return r;
        }
        if (somme > largeur) {
            float k = largeur / somme;
            r.replaceAll(f -> f * k);
        }
        return r;
    }

    private static boolean bordures(XWPFTable t) {
        String style = t.getStyleID() == null ? "" : t.getStyleID().toLowerCase(Locale.ROOT);
        if (style.contains("grid") || style.contains("grille")) return true;
        XWPFTable.XWPFBorderType[] types = {t.getTopBorderType(), t.getInsideHBorderType(), t.getLeftBorderType()};
        for (XWPFTable.XWPFBorderType b : types) {
            if (b != null && b != XWPFTable.XWPFBorderType.NONE && b != XWPFTable.XWPFBorderType.NIL) return true;
        }
        return false;
    }
}
