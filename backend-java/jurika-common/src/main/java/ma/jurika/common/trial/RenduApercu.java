package ma.jurika.common.trial;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) : rendu d'apercu en AFFICHAGE seulement.
 *
 * <p>Un PDF (ou une image) est rendu par le serveur en pages PNG ; pendant l'essai, la mention
 * "VERSION D'EVALUATION -- SANS VALEUR JURIDIQUE" est dessinee DANS les pixels de chaque page
 * (trois diagonales et un bandeau en haut et en bas) : le navigateur ne recoit que des images,
 * jamais le fichier, et ne peut pas retirer la mention. Le texte reste lisible (mention
 * translucide).
 *
 * <p>Deterministe : memes octets d'entree, memes pages (rendu PDFBox, police de la mention
 * embarquee {@code /fonts/Inter-Bold.ttf}, indications de rendu fixees, PNG sans horodatage).
 */
public final class RenduApercu {

    /** Resolution des pages rendues : lisible a l'ecran (corps 11 pt ~ 17 pixels). */
    public static final float DPI = 110f;
    /** Au-dela, les pages suivantes ne sont pas rendues ({@link ApercuPages#tronque()}). */
    public static final int MAX_PAGES = 60;
    /** Largeur maximale d'une image deposee rendue en apercu. */
    static final int LARGEUR_MAX_IMAGE = 1400;

    private static final Color ROUGE = new Color(178, 34, 34);
    private static volatile Font policeMention;

    private RenduApercu() {}

    /** Pages d'un PDF ; la mention de l'essai incrustee si {@code filigrane}. */
    public static ApercuPages depuisPdf(byte[] pdf, boolean filigrane) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer rendu = new PDFRenderer(doc);
            rendu.setSubsamplingAllowed(false);
            int total = doc.getNumberOfPages();
            int n = Math.min(total, MAX_PAGES);
            List<ApercuPages.Page> pages = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                BufferedImage image = rendu.renderImageWithDPI(i, DPI, ImageType.RGB);
                pages.add(page(i + 1, filigrane ? filigraner(image) : image));
            }
            return new ApercuPages(filigrane, filigrane ? EssaiRegles.MENTION_FILIGRANE : null, total, total > n, pages);
        } catch (IOException e) {
            throw new ApercuImpossibleException("Le document PDF n\u2019a pas pu \u00eatre lu pour l\u2019aper\u00e7u.", e);
        }
    }

    /** Une image deposee (PNG, JPEG, GIF, BMP) rendue en une page PNG produite par le serveur. */
    public static ApercuPages depuisImage(byte[] octets, boolean filigrane) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(octets));
        } catch (IOException e) {
            throw new ApercuImpossibleException("L\u2019image n\u2019a pas pu \u00eatre lue pour l\u2019aper\u00e7u.", e);
        }
        if (source == null) {
            throw new ApercuImpossibleException("Format d\u2019image non reconnu : l\u2019aper\u00e7u est impossible.", null);
        }
        int largeur = source.getWidth();
        int hauteur = source.getHeight();
        if (largeur > LARGEUR_MAX_IMAGE) {
            hauteur = Math.max(1, Math.round(hauteur * (LARGEUR_MAX_IMAGE / (float) largeur)));
            largeur = LARGEUR_MAX_IMAGE;
        }
        BufferedImage rgb = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, largeur, hauteur);
            g.drawImage(source, 0, 0, largeur, hauteur, null);
        } finally {
            g.dispose();
        }
        List<ApercuPages.Page> pages = List.of(page(1, filigrane ? filigraner(rgb) : rgb));
        return new ApercuPages(filigrane, filigrane ? EssaiRegles.MENTION_FILIGRANE : null, 1, false, pages);
    }

    /**
     * Incruste la mention de l'essai dans l'image (modifiee en place et rendue) : une grande
     * diagonale au centre, deux diagonales plus pales au tiers et aux deux tiers, et un bandeau
     * lisible en haut et en bas de la page.
     */
    public static BufferedImage filigraner(BufferedImage page) {
        int l = page.getWidth();
        int h = page.getHeight();
        String mention = EssaiRegles.MENTION_FILIGRANE;
        Graphics2D g = page.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(ROUGE);

            double angle = -Math.atan2(h, l);
            double diagonale = Math.hypot(l, h);
            Font grande = policeALargeur(g, mention, diagonale * 0.78);
            dessinerDiagonale(g, grande, mention, l / 2.0, h / 2.0, angle, 0.24f);
            Font moyenne = policeALargeur(g, mention, diagonale * 0.40);
            dessinerDiagonale(g, moyenne, mention, l * 0.25, h * 0.25, angle, 0.14f);
            dessinerDiagonale(g, moyenne, mention, l * 0.75, h * 0.75, angle, 0.14f);

            Font bandeau = police().deriveFont(Math.max(10f, l / 60f));
            g.setFont(bandeau);
            FontRenderContext frc = g.getFontRenderContext();
            Rectangle2D b = bandeau.getStringBounds(mention, frc);
            float x = (float) ((l - b.getWidth()) / 2.0);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            g.drawString(mention, x, (float) (b.getHeight() * 1.2));
            g.drawString(mention, x, (float) (h - b.getHeight() * 0.6));
        } finally {
            g.dispose();
        }
        return page;
    }

    /** Police de la mention dont le texte occupe la largeur demandee. */
    private static Font policeALargeur(Graphics2D g, String texte, double largeur) {
        Font reference = police().deriveFont(100f);
        double l100 = reference.getStringBounds(texte, g.getFontRenderContext()).getWidth();
        return police().deriveFont((float) (100.0 * largeur / l100));
    }

    private static void dessinerDiagonale(Graphics2D g, Font police, String texte, double cx, double cy,
                                          double angle, float opacite) {
        AffineTransform avant = g.getTransform();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacite));
        g.setFont(police);
        Rectangle2D b = police.getStringBounds(texte, g.getFontRenderContext());
        g.rotate(angle, cx, cy);
        g.drawString(texte, (float) (cx - b.getWidth() / 2.0), (float) (cy + b.getHeight() / 4.0));
        g.setTransform(avant);
    }

    /** Une page en PNG (URL de donnees). */
    public static ApercuPages.Page page(int numero, BufferedImage image) {
        return new ApercuPages.Page(numero, image.getWidth(), image.getHeight(),
                "data:image/png;base64," + Base64.getEncoder().encodeToString(png(image)));
    }

    static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("Aucun encodeur PNG disponible");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Font police() {
        Font f = policeMention;
        if (f == null) {
            synchronized (RenduApercu.class) {
                f = policeMention;
                if (f == null) {
                    try (InputStream in = RenduApercu.class.getResourceAsStream("/fonts/Inter-Bold.ttf")) {
                        if (in == null) throw new IllegalStateException("Police /fonts/Inter-Bold.ttf absente");
                        f = Font.createFont(Font.TRUETYPE_FONT, in);
                    } catch (IOException | FontFormatException e) {
                        throw new IllegalStateException("Police de la mention d'essai illisible", e);
                    }
                    policeMention = f;
                }
            }
        }
        return f;
    }

    /** Apercu impossible (document illisible) : refus nomme, jamais de repli sur le fichier. */
    public static class ApercuImpossibleException extends RuntimeException {
        public ApercuImpossibleException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Apercu momentanement indisponible (service de mise en page injoignable) : 503, jamais le fichier. */
    public static class ApercuIndisponibleException extends RuntimeException {
        public ApercuIndisponibleException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
