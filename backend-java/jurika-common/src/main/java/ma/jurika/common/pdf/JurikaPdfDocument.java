package ma.jurika.common.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static ma.jurika.common.pdf.JurikaPdfTheme.BAND;
import static ma.jurika.common.pdf.JurikaPdfTheme.GOLD;
import static ma.jurika.common.pdf.JurikaPdfTheme.NAVY;
import static ma.jurika.common.pdf.JurikaPdfTheme.NAVY_SOFT;
import static ma.jurika.common.pdf.JurikaPdfTheme.SLATE;
import static ma.jurika.common.pdf.JurikaPdfTheme.WHITE;

/**
 * Document PDF a la charte JURIKA, rendu avec Apache PDFBox. Fournit les blocs
 * reutilisables (en-tete cabinet, titre de section a filet or, bloc
 * libelle/valeur, tableau a en-tete navy avec retour a la ligne + saut de page +
 * repetition d'en-tete, pied de page pagine). PDFBox etant bas niveau, toute la
 * geometrie (mesure texte, wrapping, pagination) est encapsulee ici — une seule
 * fois pour toute la plateforme.
 *
 * <p>Cycle de vie : obtenir via {@link JurikaPdfTheme#newDocument()}, empiler des
 * blocs, puis {@link #finish()} (qui dessine les pieds de page et ferme le doc).
 */
public final class JurikaPdfDocument implements AutoCloseable {

    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float MARGIN_X = 42f;
    private static final float MARGIN_TOP = 44f;
    private static final float MARGIN_BOTTOM = 58f;
    private static final float CONTENT_LEFT = MARGIN_X;
    private static final float CONTENT_RIGHT = PAGE.getWidth() - MARGIN_X;
    private static final float CONTENT_WIDTH = CONTENT_RIGHT - CONTENT_LEFT;
    private static final float CELL_PAD = 6f;
    private static final float LINE_FACTOR = 1.30f;
    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PDDocument doc = new PDDocument();
    private final PDFont serif;
    private final PDFont serifBold;
    private final PDFont sans;
    private final PDFont sansBold;

    private PDPage page;
    private PDPageContentStream cs;
    private float y;
    /** Identité du cabinet (papier à en-tête) ; sert au pied de page (mentions légales). */
    private CabinetIdentity identity;

    JurikaPdfDocument() {
        try {
            // Titres en serif = Times (Standard-14), volontairement NON embarque.
            // Les serifs elegants embarques (Playfair, Lora...) ont un GSUB 'liga'
            // dont PDFBox genere une table ToUnicode incorrecte : « Identifiants »
            // s'extrayait « Identiants », cassant Ctrl+F / copier-coller / indexation.
            // Times garantit une couche texte exacte (encodage WinAnsi standard,
            // pas de subset ni de ToUnicode a generer) — critere non negociable pour
            // un document remis a une banque/administration. Inter (corps) est
            // embarque et s'extrait proprement (verifie par test).
            this.serif = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            this.serifBold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
            this.sans = loadFont(JurikaPdfTheme.FONT_SANS, Standard14Fonts.FontName.HELVETICA);
            this.sansBold = loadFont(JurikaPdfTheme.FONT_SANS_BOLD, Standard14Fonts.FontName.HELVETICA_BOLD);
            startPage();
        } catch (IOException ex) {
            throw new RuntimeException("Echec initialisation PDF : " + ex.getMessage(), ex);
        }
    }

    /** Chemin classpath de l'embleme JURIKA par defaut (papier a en-tete sans logo cabinet). */
    private static final String DEFAULT_LOGO_RESOURCE = "/branding/jurika-emblem.png";
    /**
     * Cache des octets de l'embleme par defaut. Charge une seule fois depuis le
     * classpath. {@code EMPTY} = deja tente mais introuvable (evite de recharger a
     * chaque PDF). {@code null} = pas encore charge.
     */
    private static final byte[] EMPTY = new byte[0];
    private static volatile byte[] defaultLogoCache;

    /** Embleme JURIKA par defaut (octets), ou null si la ressource est absente. */
    private static byte[] defaultLogoBytes() {
        byte[] cached = defaultLogoCache;
        if (cached == null) {
            synchronized (JurikaPdfDocument.class) {
                cached = defaultLogoCache;
                if (cached == null) {
                    try (InputStream in = JurikaPdfDocument.class.getResourceAsStream(DEFAULT_LOGO_RESOURCE)) {
                        cached = (in != null) ? in.readAllBytes() : EMPTY;
                    } catch (IOException ex) {
                        cached = EMPTY;
                    }
                    defaultLogoCache = cached;
                }
            }
        }
        return cached.length == 0 ? null : cached;
    }

    private PDFont loadFont(String resource, Standard14Fonts.FontName fallback) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) return new PDType1Font(fallback);
            // embedSubset = false : on embarque Inter en entier. Le sous-ensemble de
            // PDFBox corrompt la table ToUnicode des glyphes atteignables par ligature
            // (ff/fl/ffi) -> la couche texte (Ctrl+F, copier-coller, indexation)
            // devenait fausse. Sans subset, l'extraction est exacte. Cout : +~130 Ko
            // par PDF (Inter Regular + Bold complets), acceptable.
            return PDType0Font.load(doc, in, false);
        } catch (IOException ex) {
            return new PDType1Font(fallback);
        }
    }

    // ── En-tete ───────────────────────────────────────────────────────────────

    /**
     * Papier à en-tête : bloc cabinet (logo à gauche + nom / adresse / coordonnées),
     * puis grand titre du document, sujet, ligne meta, filet or. L'identité est
     * mémorisée pour les mentions légales du pied de page. Tout élément vide est
     * simplement omis — la mise en page ne se dégrade jamais ; le nom reste présent.
     */
    public JurikaPdfDocument header(CabinetIdentity id, String title, String subject, String meta) {
        this.identity = id;
        run(() -> {
            float textX = CONTENT_LEFT;
            float logoBottom = y;
            // Papier a en-tete par defaut (2026-07-27) : si le cabinet n'a pas
            // uploade son propre logo, on utilise l'embleme JURIKA (ressource
            // classpath). Le logo cabinet reste prioritaire quand il existe.
            boolean cabinetLogo = id != null && id.hasLogo();
            byte[] logoBytes = cabinetLogo ? id.logo() : defaultLogoBytes();
            if (logoBytes != null) {
                try {
                    PDImageXObject img = PDImageXObject.createFromByteArray(
                            doc, logoBytes, cabinetLogo ? "cabinet-logo" : "jurika-logo");
                    float maxH = 45f;
                    float maxW = 150f;
                    float scale = Math.min(maxH / img.getHeight(), maxW / img.getWidth());
                    if (scale > 1f) scale = 1f; // ne jamais agrandir
                    float w = img.getWidth() * scale;
                    float h = img.getHeight() * scale;
                    cs.drawImage(img, CONTENT_LEFT, y - h, w, h);
                    textX = CONTENT_LEFT + w + 14f;
                    logoBottom = y - h;
                } catch (Exception ex) {
                    // Logo illisible -> on rend le bloc texte seul (jamais bloquant).
                    textX = CONTENT_LEFT;
                }
            }

            float ty = y;
            String name = (id != null) ? id.name() : "Cabinet";
            show(textX, ty - 13f * 0.8f, serifBold, 13f, NAVY, name, 0f);
            ty -= 13f * 1.15f + 2f;
            if (id != null && id.adresse() != null) {
                show(textX, ty - 8.5f * 0.8f, sans, 8.5f, SLATE, id.adresse(), 0f);
                ty -= 8.5f * 1.3f;
            }
            String contact = (id != null)
                    ? joinDot(id.telephone(), id.email(), id.siteWeb()) : null;
            if (contact != null) {
                show(textX, ty - 8.5f * 0.8f, sans, 8.5f, SLATE, contact, 0f);
                ty -= 8.5f * 1.3f;
            }

            // Le bloc se termine sous le plus bas du logo et du texte.
            y = Math.min(logoBottom, ty) - 12f;

            show(CONTENT_LEFT, y - 27f * 0.8f, serifBold, 27f, NAVY, title, 0f);
            y -= 31f;
            if (subject != null && !subject.isBlank()) {
                show(CONTENT_LEFT, y - 16f * 0.8f, serif, 16f, NAVY_SOFT, subject, 0f);
                y -= 20f;
            }
            if (meta != null && !meta.isBlank()) {
                show(CONTENT_LEFT, y - 10f * 0.8f, sans, 10f, SLATE, meta, 0f);
                y -= 15f;
            }
            hrule(GOLD, 2f);
            y -= 10f;
        });
        return this;
    }

    private static String joinDot(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append("   ·   ");
            sb.append(p.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // ── Titre de section ───────────────────────────────────────────────────────

    public JurikaPdfDocument sectionTitle(String num, String title) {
        run(() -> {
            ensure(30f);
            float size = 14.5f;
            String prefix = num + ".  ";
            float baseline = y - size * 0.8f;
            show(CONTENT_LEFT, baseline, serifBold, size, GOLD, prefix, 0f);
            float px = CONTENT_LEFT + width(serifBold, size, prefix);
            show(px, baseline, serifBold, size, NAVY, title, 0f);
            y -= size * LINE_FACTOR;
            hrule(GOLD, 0.8f);
            y -= 8f;
        });
        return this;
    }

    // ── Bloc libelle / valeur ────────────────────────────────────────────────

    /** Bloc d'identite : lignes [libelle, valeur]. Libelle sur bande, valeur wrap. */
    public JurikaPdfDocument keyValues(List<String[]> rows) {
        run(() -> {
            float labelW = CONTENT_WIDTH * 0.34f;
            float valueW = CONTENT_WIDTH - labelW;
            for (String[] r : rows) {
                String label = r[0];
                String value = r.length > 1 ? r[1] : "";
                List<String> vlines = wrap(sans, 10f, valueW - 2 * CELL_PAD, value);
                float rowH = Math.max(2 * CELL_PAD + Math.max(1, vlines.size()) * 10f * LINE_FACTOR, 24f);
                if (y - rowH < MARGIN_BOTTOM) newPage();
                // Bande libelle.
                fillRect(CONTENT_LEFT, y - rowH, labelW, rowH, BAND);
                show(CONTENT_LEFT + CELL_PAD, y - CELL_PAD - 7.5f * 0.8f, sansBold, 7.5f, SLATE, upper(label), 0.5f);
                // Valeur.
                float ty = y - CELL_PAD;
                for (String line : vlines) {
                    show(CONTENT_LEFT + labelW + CELL_PAD, ty - 10f * 0.8f, sans, 10f, NAVY, line, 0f);
                    ty -= 10f * LINE_FACTOR;
                }
                // Filet bas discret.
                hline(CONTENT_LEFT + labelW, y - rowH, CONTENT_RIGHT, y - rowH, BAND, 1f);
                y -= rowH;
            }
            y -= 8f;
        });
        return this;
    }

    // ── Note / etat vide ───────────────────────────────────────────────────────

    public JurikaPdfDocument note(String message) {
        run(() -> {
            List<String> lines = wrap(sans, 9.5f, CONTENT_WIDTH - 2 * 10f, message);
            float h = 2 * 10f + lines.size() * 9.5f * LINE_FACTOR;
            if (y - h < MARGIN_BOTTOM) newPage();
            fillRect(CONTENT_LEFT, y - h, CONTENT_WIDTH, h, BAND);
            float ty = y - 10f;
            for (String line : lines) {
                show(CONTENT_LEFT + 10f, ty - 9.5f * 0.8f, sans, 9.5f, SLATE, line, 0f);
                ty -= 9.5f * LINE_FACTOR;
            }
            y -= h + 8f;
        });
        return this;
    }

    // ── Tableau ────────────────────────────────────────────────────────────────

    public JurikaPdfDocument table(PdfTable table) {
        run(() -> {
            List<PdfTable.Column> cols = table.columns();
            float totalWeight = 0f;
            for (PdfTable.Column c : cols) totalWeight += c.weight();
            float[] w = new float[cols.size()];
            float[] x = new float[cols.size()];
            float cx = CONTENT_LEFT;
            for (int i = 0; i < cols.size(); i++) {
                w[i] = CONTENT_WIDTH * (cols.get(i).weight() / totalWeight);
                x[i] = cx;
                cx += w[i];
            }

            drawTableHeader(cols, w, x);

            int rowIndex = 0;
            for (List<PdfTable.Cell> row : table.rows()) {
                float rowH = rowHeight(row, w);
                if (y - rowH < MARGIN_BOTTOM) {
                    newPage();
                    drawTableHeader(cols, w, x);
                }
                if (rowIndex % 2 == 1) {
                    fillRect(CONTENT_LEFT, y - rowH, CONTENT_WIDTH, rowH, BAND);
                }
                for (int i = 0; i < cols.size(); i++) {
                    PdfTable.Cell cell = i < row.size() ? row.get(i) : null;
                    if (cell == null) continue;
                    PdfTable.Align align = cell.align() != null ? cell.align() : cols.get(i).align();
                    drawCell(cell, x[i], w[i], rowH, align);
                }
                hline(CONTENT_LEFT, y - rowH, CONTENT_RIGHT, y - rowH, BAND, 0.5f);
                y -= rowH;
                rowIndex++;
            }
            y -= 10f;
        });
        return this;
    }

    private void drawTableHeader(List<PdfTable.Column> cols, float[] w, float[] x) throws IOException {
        float hh = 8f * LINE_FACTOR + 2 * CELL_PAD;
        if (y - hh < MARGIN_BOTTOM) newPage();
        fillRect(CONTENT_LEFT, y - hh, CONTENT_WIDTH, hh, NAVY);
        for (int i = 0; i < cols.size(); i++) {
            String label = upper(cols.get(i).header());
            float tw = width(sansBold, 8f, label);
            float tx = alignX(cols.get(i).align(), x[i], w[i], tw);
            show(tx, y - CELL_PAD - 8f * 0.8f, sansBold, 8f, WHITE, label, 0.4f);
        }
        y -= hh;
    }

    private void drawCell(PdfTable.Cell cell, float cx, float cw, float rowH, PdfTable.Align align)
            throws IOException {
        float ty = y - CELL_PAD;
        for (PdfTable.Line line : cell.lines()) {
            PDFont font = styleFont(line.style());
            float size = styleSize(line.style());
            Color color = styleColor(line.style());
            for (String sub : wrap(font, size, cw - 2 * CELL_PAD, line.text())) {
                float tw = width(font, size, sub);
                float tx = alignX(align, cx, cw, tw);
                show(tx, ty - size * 0.8f, font, size, color, sub, 0f);
                ty -= size * LINE_FACTOR;
            }
        }
    }

    private float rowHeight(List<PdfTable.Cell> row, float[] w) throws IOException {
        float max = 16f;
        for (int i = 0; i < row.size() && i < w.length; i++) {
            PdfTable.Cell cell = row.get(i);
            if (cell == null) continue;
            float h = 2 * CELL_PAD;
            for (PdfTable.Line line : cell.lines()) {
                float size = styleSize(line.style());
                int n = wrap(styleFont(line.style()), size, w[i] - 2 * CELL_PAD, line.text()).size();
                h += Math.max(1, n) * size * LINE_FACTOR;
            }
            max = Math.max(max, h);
        }
        return max;
    }

    /**
     * Total mis en valeur : filet or + libelle (a gauche) et montant en gras
     * (aligne a droite sur la marge). Reutilisable pour tout PDF avec un total.
     */
    public JurikaPdfDocument totalRow(String label, String value) {
        run(() -> {
            ensure(30f);
            hrule(GOLD, 1.2f);
            y -= 8f;
            float vSize = 12f;
            float lSize = 10f;
            float vW = width(sansBold, vSize, value);
            float lW = width(sans, lSize, label);
            float gap = 12f;
            float baseline = y - vSize * 0.8f;
            show(CONTENT_RIGHT - vW, baseline, sansBold, vSize, NAVY, value, 0f);
            show(CONTENT_RIGHT - vW - gap - lW, baseline, sans, lSize, SLATE, label, 0f);
            y -= vSize * LINE_FACTOR + 4f;
        });
        return this;
    }

    /** Ligne discrete centree (mention legale, note de bas de contenu). */
    public JurikaPdfDocument caption(String text) {
        run(() -> {
            ensure(16f);
            float size = 8f;
            float tw = width(sans, size, text);
            float x = CONTENT_LEFT + (CONTENT_WIDTH - tw) / 2f;
            show(x, y - size * 0.8f, sans, size, SLATE, text, 0f);
            y -= size * LINE_FACTOR + 2f;
        });
        return this;
    }

    // ── Divers ────────────────────────────────────────────────────────────────

    public JurikaPdfDocument spacer(float h) {
        y -= h;
        return this;
    }

    /** Ferme le contenu, dessine les pieds de page pagines, retourne le PDF. */
    public byte[] finish() {
        try {
            if (cs != null) {
                cs.close();
                cs = null;
            }
            int total = doc.getNumberOfPages();
            String genere = "Généré par JURIKA le " + LocalDate.now().format(FR_DATE);
            String legal = legalLine();
            for (int p = 0; p < total; p++) {
                PDPage pg = doc.getPage(p);
                try (PDPageContentStream f = new PDPageContentStream(
                        doc, pg, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    // Mentions légales du cabinet (ICE · RC · IF), centrées, si présentes.
                    if (legal != null) {
                        float lw = width(sans, 7.5f, legal);
                        showOn(f, CONTENT_LEFT + (CONTENT_WIDTH - lw) / 2f, 51f,
                                sans, 7.5f, SLATE, legal);
                    }
                    f.setStrokingColor(GOLD);
                    f.setLineWidth(0.6f);
                    f.moveTo(CONTENT_LEFT, 40f);
                    f.lineTo(CONTENT_RIGHT, 40f);
                    f.stroke();
                    showOn(f, CONTENT_LEFT, 26f, sans, 7.5f, SLATE, genere);
                    String pageLabel = "Page " + (p + 1) + " / " + total;
                    float pw = width(sans, 7.5f, pageLabel);
                    showOn(f, CONTENT_RIGHT - pw, 26f, sans, 7.5f, SLATE, pageLabel);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Echec generation PDF : " + ex.getMessage(), ex);
        } finally {
            try { doc.close(); } catch (IOException ignore) { /* best effort */ }
        }
    }

    /** Ligne « ICE · RC · IF » du pied de page (null si aucun identifiant). */
    private String legalLine() {
        if (identity == null) return null;
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (identity.ice() != null) parts.add("ICE : " + identity.ice());
        if (identity.rc() != null) parts.add("RC : " + identity.rc());
        if (identity.iff() != null) parts.add("IF : " + identity.iff());
        return parts.isEmpty() ? null : String.join("   ·   ", parts);
    }

    @Override
    public void close() {
        try { if (cs != null) cs.close(); } catch (IOException ignore) { }
        try { doc.close(); } catch (IOException ignore) { }
    }

    // ── Primitives PDFBox ────────────────────────────────────────────────────

    private void startPage() throws IOException {
        if (cs != null) cs.close();
        page = new PDPage(PAGE);
        doc.addPage(page);
        cs = new PDPageContentStream(doc, page);
        y = PAGE.getHeight() - MARGIN_TOP;
    }

    private void newPage() throws IOException {
        startPage();
    }

    private void ensure(float needed) throws IOException {
        if (y - needed < MARGIN_BOTTOM) newPage();
    }

    private void hrule(Color color, float lineWidth) throws IOException {
        hline(CONTENT_LEFT, y, CONTENT_RIGHT, y, color, lineWidth);
    }

    private void hline(float x1, float y1, float x2, float y2, Color color, float lineWidth)
            throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(lineWidth);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private void fillRect(float x, float ry, float w, float h, Color color) throws IOException {
        cs.setNonStrokingColor(color);
        cs.addRect(x, ry, w, h);
        cs.fill();
    }

    private void show(float x, float baseline, PDFont font, float size, Color color,
                      String s, float charSpacing) throws IOException {
        String enc = enc(font, s);
        if (enc.isEmpty()) return;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        if (charSpacing != 0f) cs.setCharacterSpacing(charSpacing);
        cs.newLineAtOffset(x, baseline);
        cs.showText(enc);
        cs.endText();
        if (charSpacing != 0f) cs.setCharacterSpacing(0f);
    }

    private void showOn(PDPageContentStream stream, float x, float baseline, PDFont font,
                        float size, Color color, String s) throws IOException {
        String enc = enc(font, s);
        if (enc.isEmpty()) return;
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(color);
        stream.newLineAtOffset(x, baseline);
        stream.showText(enc);
        stream.endText();
    }

    private static float alignX(PdfTable.Align align, float cx, float cw, float textW) {
        return switch (align == null ? PdfTable.Align.LEFT : align) {
            case RIGHT -> cx + cw - CELL_PAD - textW;
            case CENTER -> cx + (cw - textW) / 2f;
            case LEFT -> cx + CELL_PAD;
        };
    }

    // ── Texte : mesure, wrapping, encodage sur ───────────────────────────────

    private float width(PDFont font, float size, String s) {
        try {
            String enc = enc(font, s);
            return enc.isEmpty() ? 0f : font.getStringWidth(enc) / 1000f * size;
        } catch (IOException ex) {
            return s.length() * size * 0.5f; // estimation de repli
        }
    }

    private List<String> wrap(PDFont font, float size, float maxWidth, String text) {
        List<String> out = new ArrayList<>();
        String s = enc(font, text).trim();
        if (s.isEmpty()) {
            out.add("");
            return out;
        }
        String[] words = s.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (width(font, size, candidate) <= maxWidth || line.length() == 0) {
                if (width(font, size, word) > maxWidth && line.length() == 0) {
                    // Mot plus large que la colonne : coupe caractere par caractere.
                    for (String piece : hardSplit(font, size, maxWidth, word)) out.add(piece);
                    line.setLength(0);
                } else {
                    line.setLength(0);
                    line.append(candidate);
                }
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    private List<String> hardSplit(PDFont font, float size, float maxWidth, String word) {
        List<String> pieces = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (width(font, size, cur.toString() + c) > maxWidth && cur.length() > 0) {
                pieces.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) pieces.add(cur.toString());
        return pieces;
    }

    /**
     * Garantit que la chaine est encodable par la police (sinon showText leve) :
     * remplace les espaces insecables et tout caractere non encodable par une
     * espace ordinaire.
     */
    private String enc(PDFont font, String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\t' || cp == '\r' || cp == '\n' || Character.isSpaceChar(cp)) {
                sb.append(' ');
                continue;
            }
            String ch = new String(Character.toChars(cp));
            try {
                font.getStringWidth(ch);
                sb.append(ch);
            } catch (Exception ex) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    private PDFont styleFont(PdfTable.Style style) {
        return switch (style) {
            case STRONG, ACCENT, SUCCESS -> sansBold;
            case NORMAL, MUTED -> sans;
        };
    }

    private static float styleSize(PdfTable.Style style) {
        return switch (style) {
            case STRONG -> 9.5f;
            case NORMAL -> 9f;
            case MUTED -> 7.5f;
            case ACCENT, SUCCESS -> 8.5f;
        };
    }

    private static Color styleColor(PdfTable.Style style) {
        return switch (style) {
            case NORMAL, STRONG -> NAVY;
            case MUTED -> SLATE;
            case ACCENT -> GOLD;
            case SUCCESS -> JurikaPdfTheme.GREEN;
        };
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    // Petit foncteur pour centraliser la conversion IOException -> unchecked.
    private interface IoBlock { void run() throws IOException; }

    private void run(IoBlock block) {
        try {
            block.run();
        } catch (IOException ex) {
            throw new RuntimeException("Echec rendu PDF : " + ex.getMessage(), ex);
        }
    }
}
