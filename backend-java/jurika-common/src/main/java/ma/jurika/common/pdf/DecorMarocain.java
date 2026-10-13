package ma.jurika.common.pdf;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import java.io.IOException;

import static ma.jurika.common.pdf.JurikaPdfTheme.GOLD_LINE;
import static ma.jurika.common.pdf.JurikaPdfTheme.GOLD_SOFT;
import static ma.jurika.common.pdf.JurikaPdfTheme.NAVY;

/**
 * Lot L5b (E3, RG-FIC-08) : decor discret des documents de synthese, dessine en vectoriel par
 * PDFBox, repris des motifs du logo Moucharabieh :
 * <ul>
 *   <li>en-tete : frise de zellige, etoiles a huit pointes formees de deux carres superposes
 *       (motif {@code zel} du SVG : carre de 7,6 dans une maille de 12, et le meme tourne de
 *       45 degres), trait or fin, ponctuee de losanges marine ;</li>
 *   <li>pied de page : treillis de moucharabieh (motif {@code lat} : maillage a 45 degres,
 *       noeuds marques), trait or fin.</li>
 * </ul>
 * Opacite faible et traits fins : le decor reste en dehors du texte (marges) et s'imprime en
 * gris clair en niveaux de gris. Aucune donnee variable : le decor est identique d'un document
 * a l'autre (rendu deterministe).
 */
final class DecorMarocain {

    /** Rapport du carre a la maille dans le motif zel du logo (7,6 / 12). */
    private static final float CARRE = 7.6f / 12f;

    private DecorMarocain() {}

    /** Frise de zellige dans la boite (x, y bas gauche, largeur w, hauteur h). */
    static void zellige(PDPageContentStream cs, float x, float y, float w, float h)
            throws IOException {
        int n = Math.max(1, Math.round(w / h));
        float c = w / n;
        float cy = y + h / 2f;
        cs.saveGraphicsState();
        alpha(cs, 0.85f);
        filets(cs, x, y, w, h);
        cs.setStrokingColor(GOLD_SOFT);
        cs.setLineWidth(0.5f);
        float demi = Math.min(c, h) * CARRE / 2f;
        for (int i = 0; i < n; i++) {
            float cx = x + c * i + c / 2f;
            // Carre droit.
            cs.addRect(cx - demi, cy - demi, 2 * demi, 2 * demi);
            // Le meme tourne de 45 degres (losange de meme cote).
            float r = demi * (float) Math.sqrt(2);
            cs.moveTo(cx, cy + r);
            cs.lineTo(cx + r, cy);
            cs.lineTo(cx, cy - r);
            cs.lineTo(cx - r, cy);
            cs.closePath();
        }
        cs.stroke();
        // Losanges marine entre les etoiles.
        alpha(cs, 0.45f);
        cs.setNonStrokingColor(NAVY);
        for (int i = 1; i < n; i++) {
            losange(cs, x + c * i, cy, 1.1f);
        }
        cs.fill();
        cs.restoreGraphicsState();
    }

    /** Treillis de moucharabieh dans la boite (x, y bas gauche, largeur w, hauteur h). */
    static void moucharabieh(PDPageContentStream cs, float x, float y, float w, float h)
            throws IOException {
        float pas = h / 2f;
        cs.saveGraphicsState();
        alpha(cs, 0.85f);
        filets(cs, x, y, w, h);
        cs.addRect(x, y, w, h);
        cs.clip();
        alpha(cs, 0.7f);
        cs.setStrokingColor(GOLD_SOFT);
        cs.setLineWidth(0.4f);
        // Diagonales montantes et descendantes, de pas regulier.
        for (float d = -h; d <= w + h; d += pas) {
            cs.moveTo(x + d, y);
            cs.lineTo(x + d + h, y + h);
            cs.moveTo(x + d, y + h);
            cs.lineTo(x + d + h, y);
        }
        cs.stroke();
        // Noeuds du treillis (croisements), petits losanges or.
        cs.setNonStrokingColor(GOLD_SOFT);
        for (float d = -h; d <= w + h; d += pas) {
            losange(cs, x + d + h / 2f, y + h / 2f, 0.9f);
        }
        cs.fill();
        cs.restoreGraphicsState();
    }

    private static void filets(PDPageContentStream cs, float x, float y, float w, float h) throws IOException {
        cs.setStrokingColor(GOLD_LINE);
        cs.setLineWidth(0.5f);
        cs.moveTo(x, y);
        cs.lineTo(x + w, y);
        cs.moveTo(x, y + h);
        cs.lineTo(x + w, y + h);
        cs.stroke();
    }

    private static void losange(PDPageContentStream cs, float cx, float cy, float r) throws IOException {
        cs.moveTo(cx, cy + r);
        cs.lineTo(cx + r, cy);
        cs.lineTo(cx, cy - r);
        cs.lineTo(cx - r, cy);
        cs.closePath();
    }

    private static void alpha(PDPageContentStream cs, float a) throws IOException {
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setStrokingAlphaConstant(a);
        gs.setNonStrokingAlphaConstant(a);
        cs.setGraphicsStateParameters(gs);
    }
}
