package ma.jurika.common.pdf;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Lot L5b (E3, RG-FIC-08) : logo Moucharabieh valide par la direction (embleme et nom JURIKA,
 * version claire), pour l'en-tete des documents de synthese.
 *
 * <p>Source : copie a l'identique de {@code ~/docs/logo/3-moucharabieh/logo-horizontal-clair.png}
 * (2360 x 600, empreinte SHA-256 d6748db1...b43b10, verifiee par test). Le SVG vectoriel a ete
 * essaye (Batik et pdfbox-graphics2d, disponibles hors ligne) : le rendu perdait les traits
 * dores en degrade, le fondu des masques et l'orientation du motif de zellige ; il n'etait pas
 * le logo valide. Le PNG officiel est donc repris, reduit ici de moitie (1180 x 300, environ
 * 450 dpi a la largeur imprimee) pour garder un PDF leger. Reduction deterministe : memes
 * octets source, meme image.
 */
public final class LogoMoucharabieh {

    public static final String RESSOURCE = "/branding/moucharabieh/logo-horizontal-clair.png";
    static final int LARGEUR = 1180;
    static final int HAUTEUR = 300;

    private static volatile BufferedImage cache;

    private LogoMoucharabieh() {}

    /** Image du logo reduite (chargee une fois). */
    public static BufferedImage image() {
        BufferedImage img = cache;
        if (img == null) {
            synchronized (LogoMoucharabieh.class) {
                img = cache;
                if (img == null) {
                    img = reduire(lire());
                    cache = img;
                }
            }
        }
        return img;
    }

    /** Rapport largeur / hauteur du logo. */
    public static float ratio() {
        return (float) LARGEUR / HAUTEUR;
    }

    static BufferedImage lire() {
        try (InputStream in = LogoMoucharabieh.class.getResourceAsStream(RESSOURCE)) {
            if (in == null) throw new IllegalStateException("Logo introuvable : " + RESSOURCE);
            BufferedImage src = ImageIO.read(in);
            if (src == null) throw new IllegalStateException("Logo illisible : " + RESSOURCE);
            return src;
        } catch (IOException ex) {
            throw new UncheckedIOException("Logo illisible : " + RESSOURCE, ex);
        }
    }

    private static BufferedImage reduire(BufferedImage src) {
        BufferedImage out = new BufferedImage(LARGEUR, HAUTEUR, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, LARGEUR, HAUTEUR, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
