package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDecimalNumber;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Convertit du HTML produit par l'éditeur TipTap (frontend) en .docx Apache POI.
 *
 * <p>Tags supportés (suffisant pour les besoins juridiques cabinet) :
 * <ul>
 *   <li>{@code h1 h2 h3 h4} : titres avec tailles 18/14/12/11 et gras (h1 centré)</li>
 *   <li>{@code p, br} : paragraphes et sauts de ligne</li>
 *   <li>{@code strong, b} : gras</li>
 *   <li>{@code em, i} : italique</li>
 *   <li>{@code u} : souligné</li>
 *   <li>{@code s, strike, del} : barré</li>
 *   <li>{@code ul, ol, li} : listes à puces et numérotées (1 niveau)</li>
 *   <li>{@code table, tr, td, th} : tableaux simples</li>
 *   <li>{@code blockquote} : indenté gauche + italique</li>
 *   <li>{@code hr} : trait horizontal (paragraphe avec bordure)</li>
 *   <li>{@code a} : lien (texte conservé, l'URL est ignorée pour V1)</li>
 * </ul>
 *
 * <p>Tags ignorés : {@code img, video, iframe, script, style}.
 *
 * <p>Sprint 2026-06-12 — éditeur in-app TipTap : roundtrip DOCX → HTML (Mammoth
 * côté front) → édition WYSIWYG → HTML → DOCX (cette classe).
 */
@Component
public class HtmlToDocxConverter {

    public byte[] convert(String html) {
        if (html == null) html = "";
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Document parsed = Jsoup.parse(html);
            Element body = parsed.body();
            renderBlock(doc, body, new Style());

            doc.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Echec conversion HTML -> DOCX : " + ex.getMessage(), ex);
        }
    }

    // ----------------------------------------------------------------------
    // Rendering
    // ----------------------------------------------------------------------

    private void renderBlock(XWPFDocument doc, Element container, Style inherited) {
        for (Node child : container.childNodes()) {
            if (child instanceof Element el) {
                renderElement(doc, el, inherited);
            } else if (child instanceof TextNode tn) {
                String text = tn.text();
                if (!text.isBlank()) {
                    XWPFParagraph p = doc.createParagraph();
                    applyText(p, text, inherited);
                }
            }
        }
    }

    private void renderElement(XWPFDocument doc, Element el, Style inherited) {
        String tag = el.tagName().toLowerCase(Locale.ROOT);
        switch (tag) {
            case "h1" -> renderHeading(doc, el, 18, true, ParagraphAlignment.CENTER);
            case "h2" -> renderHeading(doc, el, 14, true, ParagraphAlignment.LEFT);
            case "h3" -> renderHeading(doc, el, 12, true, ParagraphAlignment.LEFT);
            case "h4" -> renderHeading(doc, el, 11, true, ParagraphAlignment.LEFT);
            case "p" -> renderParagraph(doc, el, inherited);
            case "br" -> doc.createParagraph();
            case "ul" -> renderList(doc, el, /*ordered*/ false);
            case "ol" -> renderList(doc, el, /*ordered*/ true);
            case "table" -> renderTable(doc, el);
            case "blockquote" -> renderBlockquote(doc, el, inherited);
            case "hr" -> renderHr(doc);
            // Inline at top level → wrap in paragraph
            case "strong", "b", "em", "i", "u", "s", "strike", "del", "a", "span" -> {
                XWPFParagraph p = doc.createParagraph();
                renderInline(p, el, inherited);
            }
            // Containers → recurse
            case "div", "section", "article", "header", "footer", "main", "body" ->
                    renderBlock(doc, el, inherited);
            // Ignored
            case "img", "video", "iframe", "script", "style", "head" -> {
                // skip
            }
            default -> {
                // Unknown tag — treat as a div
                renderBlock(doc, el, inherited);
            }
        }
    }

    private void renderHeading(XWPFDocument doc, Element el, int size, boolean bold,
                                ParagraphAlignment align) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(align);
        Style s = new Style().withSize(size).withBold(bold);
        renderInline(p, el, s);
    }

    private void renderParagraph(XWPFDocument doc, Element el, Style inherited) {
        XWPFParagraph p = doc.createParagraph();
        renderInline(p, el, inherited);
    }

    private void renderList(XWPFDocument doc, Element el, boolean ordered) {
        BigInteger numId = ensureListNum(doc, ordered);
        int index = 1;
        for (Element li : el.children()) {
            if (!"li".equalsIgnoreCase(li.tagName())) continue;
            XWPFParagraph p = doc.createParagraph();
            // Indentation via Numbering, fallback à un prefix si numbering non dispo.
            if (numId != null) {
                CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
                CTNumPr numPr = pPr.isSetNumPr() ? pPr.getNumPr() : pPr.addNewNumPr();
                CTDecimalNumber ilvl = numPr.isSetIlvl() ? numPr.getIlvl() : numPr.addNewIlvl();
                ilvl.setVal(BigInteger.ZERO);
                CTDecimalNumber n = numPr.isSetNumId() ? numPr.getNumId() : numPr.addNewNumId();
                n.setVal(numId);
            } else {
                p.setIndentationLeft(720);
                String prefix = ordered ? (index + ". ") : "• ";
                XWPFRun r = p.createRun();
                r.setText(prefix);
            }
            renderInline(p, li, new Style());
            index++;
        }
    }

    /** Crée (ou réutilise) une numérotation Word. Retourne null si POI ne le permet pas. */
    private BigInteger ensureListNum(XWPFDocument doc, boolean ordered) {
        try {
            XWPFNumbering numbering = doc.getNumbering();
            if (numbering == null) numbering = doc.createNumbering();
            // POI nécessite un abstractNum existant ; on en crée un minimal au besoin via une approche
            // déterministe : créer un nouveau numId pour chaque appel. En pratique on simplifie en
            // retournant null si pas de méthode disponible. Le fallback prefix marche bien.
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void renderTable(XWPFDocument doc, Element el) {
        // Detect rows
        java.util.List<Element> rows = el.select("tr");
        if (rows.isEmpty()) return;
        int cols = 0;
        for (Element r : rows) cols = Math.max(cols, r.select("td,th").size());
        if (cols == 0) return;

        XWPFTable table = doc.createTable(rows.size(), cols);
        for (int i = 0; i < rows.size(); i++) {
            Element rowEl = rows.get(i);
            java.util.List<Element> cells = rowEl.select("td,th");
            XWPFTableRow row = table.getRow(i);
            for (int j = 0; j < cells.size(); j++) {
                Element cellEl = cells.get(j);
                XWPFTableCell cell = row.getCell(j);
                // Empty default paragraph created by POI
                if (!cell.getParagraphs().isEmpty()) {
                    cell.removeParagraph(0);
                }
                XWPFParagraph p = cell.addParagraph();
                Style s = new Style();
                if ("th".equalsIgnoreCase(cellEl.tagName())) s = s.withBold(true);
                renderInline(p, cellEl, s);
            }
        }
    }

    private void renderBlockquote(XWPFDocument doc, Element el, Style inherited) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(720);
        renderInline(p, el, inherited.withItalic(true));
    }

    private void renderHr(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setBorderBottom(Borders.SINGLE);
    }

    // ----------------------------------------------------------------------
    // Inline rendering
    // ----------------------------------------------------------------------

    private void renderInline(XWPFParagraph p, Element el, Style baseStyle) {
        Deque<Style> stack = new ArrayDeque<>();
        stack.push(baseStyle);
        walkInline(p, el, stack);
    }

    private void walkInline(XWPFParagraph p, Element el, Deque<Style> stack) {
        for (Node child : el.childNodes()) {
            if (child instanceof TextNode tn) {
                String text = tn.text();
                if (text.isEmpty()) continue;
                applyText(p, text, stack.peek());
            } else if (child instanceof Element childEl) {
                String tag = childEl.tagName().toLowerCase(Locale.ROOT);
                Style nested = switch (tag) {
                    case "strong", "b" -> stack.peek().withBold(true);
                    case "em", "i" -> stack.peek().withItalic(true);
                    case "u" -> stack.peek().withUnderline(true);
                    case "s", "strike", "del" -> stack.peek().withStrike(true);
                    case "br" -> {
                        XWPFRun r = p.createRun();
                        r.addBreak();
                        yield stack.peek();
                    }
                    case "a" -> stack.peek().withUnderline(true);
                    default -> stack.peek();
                };
                if (!"br".equals(tag)) {
                    stack.push(nested);
                    walkInline(p, childEl, stack);
                    stack.pop();
                }
            }
        }
    }

    private void applyText(XWPFParagraph p, String text, Style style) {
        XWPFRun r = p.createRun();
        r.setText(text);
        if (style.bold) r.setBold(true);
        if (style.italic) r.setItalic(true);
        if (style.underline) r.setUnderline(UnderlinePatterns.SINGLE);
        if (style.strike) r.setStrikeThrough(true);
        if (style.size != null) r.setFontSize(style.size);
    }

    // ----------------------------------------------------------------------
    // Style — immutable
    // ----------------------------------------------------------------------

    private static final class Style {
        final boolean bold;
        final boolean italic;
        final boolean underline;
        final boolean strike;
        final Integer size;

        Style() { this(false, false, false, false, null); }
        Style(boolean bold, boolean italic, boolean underline, boolean strike, Integer size) {
            this.bold = bold; this.italic = italic; this.underline = underline;
            this.strike = strike; this.size = size;
        }
        Style withBold(boolean v) { return new Style(v, italic, underline, strike, size); }
        Style withItalic(boolean v) { return new Style(bold, v, underline, strike, size); }
        Style withUnderline(boolean v) { return new Style(bold, italic, v, strike, size); }
        Style withStrike(boolean v) { return new Style(bold, italic, underline, v, size); }
        Style withSize(Integer v) { return new Style(bold, italic, underline, strike, v); }
    }
}
