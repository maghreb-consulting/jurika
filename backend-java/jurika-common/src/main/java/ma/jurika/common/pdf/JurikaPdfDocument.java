package ma.jurika.common.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
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
    /** Marges hautes et basses : elles laissent la place au decor d'en-tete et de pied de page. */
    private static final float MARGIN_TOP = 60f;
    private static final float MARGIN_BOTTOM = 76f;
    /** Hauteur du logo Moucharabieh (image complete, marges transparentes comprises). */
    private static final float LOGO_H = 44f;
    /** Marge transparente a gauche du logo (67 px sur 2360) : l'embleme s'aligne sur la marge. */
    private static final float LOGO_MARGE_GAUCHE = LOGO_H * 67f / 600f;
    private static final float CONTENT_LEFT = MARGIN_X;
    private static final float CONTENT_RIGHT = PAGE.getWidth() - MARGIN_X;
    private static final float CONTENT_WIDTH = CONTENT_RIGHT - CONTENT_LEFT;
    private static final float CELL_PAD = 5f;
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
    private String titre;
    private String sujet;
    private LocalDate dateEdition;

    JurikaPdfDocument() {
        try {
            // Lot L6 (E1) : toutes les polices sont EMBARQUEES (Inter, licence libre SIL OFL 1.1),
            // pour un rendu identique sur tous les ordinateurs et a l'impression. Les titres etaient
            // en Times non embarque (police standard 14) : chaque lecteur la remplacait par sa propre
            // police, aux chasses differentes (espacement irregulier constate en L5b). Inter est
            // embarque en entier (voir loadFont) : la couche texte reste exacte.
            this.sans = loadFont(JurikaPdfTheme.FONT_SANS);
            this.sansBold = loadFont(JurikaPdfTheme.FONT_SANS_BOLD);
            this.serif = this.sans;
            this.serifBold = this.sansBold;
            startPage();
        } catch (IOException ex) {
            throw new RuntimeException("Echec initialisation PDF : " + ex.getMessage(), ex);
        }
    }

    private PDFont loadFont(String resource) {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            // Lot L6 (E1) : plus de repli silencieux sur une police non embarquee (Helvetica) :
            // une police absente est un defaut de livraison, il doit se voir.
            if (in == null) throw new IllegalStateException("Police absente du classpath : " + resource);
            // embedSubset = false : on embarque Inter en entier. Le sous-ensemble de
            // PDFBox corrompt la table ToUnicode des glyphes atteignables par ligature
            // (ff/fl/ffi) -> la couche texte (Ctrl+F, copier-coller, indexation)
            // devenait fausse. Sans subset, l'extraction est exacte. Cout : +~130 Ko
            // par PDF (Inter Regular + Bold complets), acceptable.
            return PDType0Font.load(doc, in, false);
        } catch (IOException ex) {
            throw new IllegalStateException("Police illisible : " + resource, ex);
        }
    }

    // -- En-tete ---------------------------------------------------------------------------

    /**
     * En-tete des documents de synthese (lot L5b E3, RG-FIC-08) : logo Moucharabieh de JURIKA a
     * gauche ; a droite, le bloc du cabinet (logo du cabinet s'il en a depose un, nom,
     * adresse, coordonnees) ; puis grand titre, sujet, ligne meta et filet or. L'identite est
     * memorisee pour les mentions legales du pied de page. Tout element vide est omis.
     */
    public JurikaPdfDocument header(CabinetIdentity id, String title, String subject, String meta) {
        this.identity = id;
        this.titre = title;
        this.sujet = subject;
        run(() -> {
            float top = y;
            // Logo JURIKA (Moucharabieh), toujours present.
            PDImageXObject logo = LosslessFactory.createFromImage(doc, LogoMoucharabieh.image());
            float logoH = LOGO_H;
            float logoW = logoH * LogoMoucharabieh.ratio();
            cs.drawImage(logo, CONTENT_LEFT - LOGO_MARGE_GAUCHE, top - logoH, logoW, logoH);
            float bottom = top - logoH;

            // Bloc du cabinet, aligne a droite.
            float right = CONTENT_RIGHT;
            if (id != null && id.hasLogo()) {
                try {
                    PDImageXObject img = PDImageXObject.createFromByteArray(doc, id.logo(), "cabinet-logo");
                    float scale = Math.min(LOGO_H / img.getHeight(), 90f / img.getWidth());
                    if (scale > 1f) scale = 1f; // ne jamais agrandir
                    float w = img.getWidth() * scale;
                    float h = img.getHeight() * scale;
                    cs.drawImage(img, CONTENT_RIGHT - w, top - h, w, h);
                    right = CONTENT_RIGHT - w - 10f;
                    bottom = Math.min(bottom, top - h);
                } catch (Exception ex) {
                    // Logo du cabinet illisible : le bloc texte seul (jamais bloquant).
                    right = CONTENT_RIGHT;
                }
            }
            float textMax = right - (CONTENT_LEFT + logoW + 12f);
            float ty = top;
            String name = (id != null) ? id.name() : "Cabinet";
            for (String l : wrap(serifBold, 12f, textMax, name)) {
                showRight(right, ty - 12f * 0.8f, serifBold, 12f, NAVY, l);
                ty -= 12f * 1.2f;
            }
            ty -= 1f;
            if (id != null && id.adresse() != null) {
                for (String l : wrap(sans, 8f, textMax, id.adresse())) {
                    showRight(right, ty - 8f * 0.8f, sans, 8f, SLATE, l);
                    ty -= 8f * 1.35f;
                }
            }
            String contact = (id != null) ? joinDot(id.telephone(), id.email(), id.siteWeb()) : null;
            if (contact != null) {
                for (String l : wrap(sans, 8f, textMax, contact)) {
                    showRight(right, ty - 8f * 0.8f, sans, 8f, SLATE, l);
                    ty -= 8f * 1.35f;
                }
            }
            y = Math.min(bottom, ty) - 6f; // le bas du logo porte deja une marge transparente

            for (String l : wrap(serifBold, 22f, CONTENT_WIDTH, title)) {
                show(CONTENT_LEFT, y - 22f * 0.8f, serifBold, 22f, NAVY, l, 0f);
                y -= 26f;
            }
            if (subject != null && !subject.isBlank()) {
                for (String l : wrap(serif, 14f, CONTENT_WIDTH, subject)) {
                    show(CONTENT_LEFT, y - 14f * 0.8f, serif, 14f, NAVY_SOFT, l, 0f);
                    y -= 18f;
                }
            }
            if (meta != null && !meta.isBlank()) {
                for (String l : wrap(sans, 9.5f, CONTENT_WIDTH, meta)) {
                    show(CONTENT_LEFT, y - 9.5f * 0.8f, sans, 9.5f, SLATE, l, 0f);
                    y -= 13f;
                }
            }
            y -= 3f;
            hrule(GOLD, 1.5f);
            y -= 12f;
        });
        return this;
    }

    /**
     * Date d'edition imprimee au pied de page et posee dans les proprietes du PDF. Obligatoire :
     * le document ne lit jamais l'horloge (memes donnees, memes octets).
     */
    public JurikaPdfDocument dateEdition(LocalDate date) {
        this.dateEdition = date;
        return this;
    }

    private static String joinDot(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append("   \u00b7   ");
            sb.append(p.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // ── Titre de section ───────────────────────────────────────────────────────

    public JurikaPdfDocument sectionTitle(String num, String title) {
        run(() -> {
            ensure(70f); // le titre ne reste jamais seul en bas de page
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

    /**
     * Titre de section sans numero (lot L7 : contrat d'abonnement, ou les pieces portent deja
     * leur lettre dans le titre ou n'en ont pas).
     */
    public JurikaPdfDocument sectionTitle(String title) {
        run(() -> {
            ensure(70f);
            float size = 14.5f;
            for (String l : wrap(serifBold, size, CONTENT_WIDTH, title)) {
                show(CONTENT_LEFT, y - size * 0.8f, serifBold, size, NAVY, l, 0f);
                y -= size * LINE_FACTOR;
            }
            hrule(GOLD, 0.8f);
            y -= 8f;
        });
        return this;
    }

    /**
     * Garantit {@code hauteur} points disponibles sur la page courante, sinon passe a la page
     * suivante (lot L7 : un en-tete de tableau ne reste pas seul en bas de page).
     */
    public JurikaPdfDocument espaceDisponible(float hauteur) {
        run(() -> ensure(hauteur));
        return this;
    }

    /** Intertitre (article d'un contrat) : gras, navy ; ne reste jamais seul en bas de page. */
    public JurikaPdfDocument subheading(String text) {
        run(() -> {
            ensure(48f);
            float size = 11f;
            y -= 2f;
            for (String l : wrap(serifBold, size, CONTENT_WIDTH, text)) {
                show(CONTENT_LEFT, y - size * 0.8f, serifBold, size, NAVY_SOFT, l, 0f);
                y -= size * LINE_FACTOR;
            }
            y -= 3f;
        });
        return this;
    }

    // ── Bloc libelle / valeur ────────────────────────────────────────────────

    /**
     * Bloc d'identite : lignes [libelle, valeur] ou [libelle, valeur, precision]. Libelle sur
     * bande, valeur a la ligne si besoin ; la precision (provenance...) en petit dessous.
     */
    public JurikaPdfDocument keyValues(List<String[]> rows) {
        run(() -> {
            float labelW = CONTENT_WIDTH * 0.32f;
            float valueW = CONTENT_WIDTH - labelW;
            for (String[] r : rows) {
                String label = r[0];
                String value = r.length > 1 ? r[1] : "";
                String detail = r.length > 2 ? r[2] : null;
                List<String> llines = wrap(sansBold, 7.5f, labelW - 2 * CELL_PAD, upper(label), 0.4f);
                List<String> vlines = wrap(sans, 10f, valueW - 2 * CELL_PAD, value);
                List<String> dlines = (detail == null || detail.isBlank()) ? List.of()
                        : wrap(sans, 7.5f, valueW - 2 * CELL_PAD, detail);
                float valueH = Math.max(1, vlines.size()) * 10f * LINE_FACTOR + dlines.size() * 7.5f * LINE_FACTOR;
                float labelH = llines.size() * 7.5f * LINE_FACTOR;
                float rowH = Math.max(2 * CELL_PAD + Math.max(valueH, labelH), 20f);
                if (y - rowH < MARGIN_BOTTOM) newPage();
                fillRect(CONTENT_LEFT, y - rowH, labelW, rowH, BAND);
                float ly = y - CELL_PAD;
                for (String line : llines) {
                    show(CONTENT_LEFT + CELL_PAD, ly - 7.5f * 0.8f - 1.5f, sansBold, 7.5f, SLATE, line, 0.4f);
                    ly -= 7.5f * LINE_FACTOR;
                }
                float ty = y - CELL_PAD;
                for (String line : vlines) {
                    show(CONTENT_LEFT + labelW + CELL_PAD, ty - 10f * 0.8f, sans, 10f, NAVY, line, 0f);
                    ty -= 10f * LINE_FACTOR;
                }
                for (String line : dlines) {
                    show(CONTENT_LEFT + labelW + CELL_PAD, ty - 7.5f * 0.8f, sans, 7.5f, SLATE, line, 0f);
                    ty -= 7.5f * LINE_FACTOR;
                }
                hline(CONTENT_LEFT + labelW, y - rowH, CONTENT_RIGHT, y - rowH, BAND, 1f);
                y -= rowH;
            }
            y -= 8f;
        });
        return this;
    }

    /** Paragraphe de texte courant (corps, a la ligne automatiquement). */
    public JurikaPdfDocument paragraph(String text) {
        run(() -> {
            for (String line : wrap(sans, 10f, CONTENT_WIDTH, text)) {
                ensure(10f * LINE_FACTOR);
                show(CONTENT_LEFT, y - 10f * 0.8f, sans, 10f, NAVY, line, 0f);
                y -= 10f * LINE_FACTOR;
            }
            y -= 6f;
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
        // Libelles en capitales espacees, a la ligne si la colonne est etroite : jamais de
        // chevauchement entre colonnes.
        List<List<String>> labels = new ArrayList<>();
        int maxLines = 1;
        for (int i = 0; i < cols.size(); i++) {
            List<String> l = wrap(sansBold, 8f, w[i] - 2 * CELL_PAD, upper(cols.get(i).header()), 0.4f);
            labels.add(l);
            maxLines = Math.max(maxLines, l.size());
        }
        float hh = maxLines * 8f * LINE_FACTOR + 2 * CELL_PAD;
        if (y - hh < MARGIN_BOTTOM) newPage();
        fillRect(CONTENT_LEFT, y - hh, CONTENT_WIDTH, hh, NAVY);
        for (int i = 0; i < cols.size(); i++) {
            float ty = y - CELL_PAD;
            for (String label : labels.get(i)) {
                float tw = width(sansBold, 8f, label, 0.4f);
                float tx = alignX(cols.get(i).align(), x[i], w[i], tw);
                show(tx, ty - 8f * 0.8f, sansBold, 8f, WHITE, label, 0.4f);
                ty -= 8f * LINE_FACTOR;
            }
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

    /**
     * Ferme le contenu, dessine sur chaque page le decor d'en-tete (bandeau de zellige) et de
     * pied de page (treillis de moucharabieh), les mentions legales et la pagination, fige les
     * proprietes du PDF (dates = date d'edition, identifiant derive du contenu), retourne le PDF.
     */
    public byte[] finish() {
        if (dateEdition == null) {
            throw new IllegalStateException("Date d'edition du PDF non fournie (dateEdition)");
        }
        try {
            if (cs != null) {
                cs.close();
                cs = null;
            }
            int total = doc.getNumberOfPages();
            String genere = "\u00c9dit\u00e9 par JURIKA le " + dateEdition.format(FR_DATE);
            String legal = legalLine();
            for (int p = 0; p < total; p++) {
                PDPage pg = doc.getPage(p);
                try (PDPageContentStream f = new PDPageContentStream(
                        doc, pg, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    DecorMarocain.zellige(f, CONTENT_LEFT, PAGE.getHeight() - 38f, CONTENT_WIDTH, 16f);
                    DecorMarocain.moucharabieh(f, CONTENT_LEFT, 18f, CONTENT_WIDTH, 14f);
                    if (legal != null) {
                        float lw = width(sans, 7.5f, legal);
                        showOn(f, CONTENT_LEFT + (CONTENT_WIDTH - lw) / 2f, 60f, sans, 7.5f, SLATE, legal);
                    }
                    f.setStrokingColor(GOLD);
                    f.setLineWidth(0.6f);
                    f.moveTo(CONTENT_LEFT, 52f);
                    f.lineTo(CONTENT_RIGHT, 52f);
                    f.stroke();
                    showOn(f, CONTENT_LEFT, 40f, sans, 7.5f, SLATE, genere);
                    String pageLabel = "Page " + (p + 1) + " / " + total;
                    float pw = width(sans, 7.5f, pageLabel);
                    showOn(f, CONTENT_RIGHT - pw, 40f, sans, 7.5f, SLATE, pageLabel);
                }
            }
            proprietes();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Echec generation PDF : " + ex.getMessage(), ex);
        } finally {
            try { doc.close(); } catch (IOException ignore) { /* best effort */ }
        }
    }

    /**
     * Proprietes figees : dates de creation et de modification = date d'edition (minuit UTC),
     * producteur JURIKA, identifiant de document derive du titre, du sujet et de la date
     * (PDFBox en tire sinon un de l'horloge : deux generations differaient).
     */
    private void proprietes() {
        PDDocumentInformation info = doc.getDocumentInformation();
        Calendar jour = new GregorianCalendar(TimeZone.getTimeZone(ZoneOffset.UTC));
        jour.clear();
        jour.setTimeInMillis(dateEdition.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        info.setCreationDate(jour);
        info.setModificationDate(jour);
        info.setCreator("JURIKA");
        info.setProducer("JURIKA");
        if (titre != null) info.setTitle(sujet == null || sujet.isBlank() ? titre : titre + " \u2014 " + sujet);
        byte[] id = empreinte(titre + "|" + sujet + "|" + dateEdition + "|" + doc.getNumberOfPages());
        COSArray ids = new COSArray();
        ids.add(new COSString(id));
        ids.add(new COSString(id));
        doc.getDocument().getTrailer().setItem(COSName.ID, ids);
    }

    private static byte[] empreinte(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(h, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Ligne « ICE · RC · IF » du pied de page (null si aucun identifiant). */
    private String legalLine() {
        if (identity == null) return null;
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (identity.ice() != null) parts.add("ICE : " + identity.ice());
        if (identity.rc() != null) parts.add("RC : " + identity.rc());
        if (identity.iff() != null) parts.add("IF : " + identity.iff());
        return parts.isEmpty() ? null : String.join("   \u00b7   ", parts);
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

    private void showRight(float right, float baseline, PDFont font, float size, Color color, String s)
            throws IOException {
        show(right - width(font, size, s), baseline, font, size, color, s, 0f);
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
        return wrap(font, size, maxWidth, text, 0f);
    }

    /** Largeur d'un texte avec un espacement des lettres (Tc) de {@code spacing}. */
    private float width(PDFont font, float size, String s, float spacing) {
        float w = width(font, size, s);
        return spacing == 0f ? w : w + spacing * Math.max(0, enc(font, s).length() - 1);
    }

    /** Coupe a la largeur en tenant compte de l'espacement des lettres (libelles en capitales). */
    private List<String> wrap(PDFont font, float size, float maxWidth, String text, float spacing) {
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
            if (width(font, size, candidate, spacing) <= maxWidth || line.length() == 0) {
                if (width(font, size, word, spacing) > maxWidth && line.length() == 0) {
                    // Mot plus large que la colonne : coupe caractere par caractere.
                    for (String piece : hardSplit(font, size, maxWidth, word, spacing)) out.add(piece);
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

    private List<String> hardSplit(PDFont font, float size, float maxWidth, String word, float spacing) {
        List<String> pieces = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (width(font, size, cur.toString() + c, spacing) > maxWidth && cur.length() > 0) {
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
            if (cp == 0x2019 && font instanceof PDType0Font) {
                // Inter partage le glyphe de l'apostrophe typographique avec U+02BC : la table
                // ToUnicode rendait U+02BC (copier-coller faux). Apostrophe droite, exacte.
                sb.append('\'');
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
