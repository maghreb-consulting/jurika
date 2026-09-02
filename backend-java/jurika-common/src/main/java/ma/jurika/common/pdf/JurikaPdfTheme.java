package ma.jurika.common.pdf;

import java.awt.Color;

/**
 * Charte PDF partagee JURIKA (tokens index.css, mode clair). Centralise les
 * couleurs et sert de fabrique au document PDF style ({@link JurikaPdfDocument}),
 * pour que TOUS les PDF de la plateforme (Fiche client, Etat des debours, ...)
 * partagent la meme identite visuelle.
 *
 * <p>Moteur : Apache PDFBox (Apache 2.0). Remplace iText (AGPL) — 2026-07-14.
 */
public final class JurikaPdfTheme {

    private JurikaPdfTheme() {}

    // ── Couleurs charte ───────────────────────────────────────────────────────
    public static final Color WHITE = new Color(0xff, 0xff, 0xff);
    public static final Color VELIN = new Color(0xfa, 0xfa, 0xf7);
    public static final Color BAND = new Color(0xf4, 0xf4, 0xef); // encadres / lignes alternees
    public static final Color NAVY = new Color(0x05, 0x0d, 0x1f); // titres
    public static final Color NAVY_SOFT = new Color(0x1c, 0x34, 0x61); // secondaire
    public static final Color SLATE = new Color(0x64, 0x74, 0x8b); // libelles discrets
    public static final Color GOLD = new Color(0xa8, 0x85, 0x3d); // filets / accents
    public static final Color GOLD_SOFT = new Color(0xd4, 0xb0, 0x6a);
    public static final Color GREEN = new Color(0x10, 0x81, 0x5a);

    /**
     * Police du corps embarquee (Inter, OFL) — repli Helvetica si absente.
     * Les titres serif utilisent Times (Standard-14, non embarque) : voir
     * {@link JurikaPdfDocument} pour la raison (couche texte exacte).
     */
    public static final String FONT_SANS = "/fonts/Inter-Regular.ttf";
    public static final String FONT_SANS_BOLD = "/fonts/Inter-Bold.ttf";

    /**
     * Ouvre un nouveau document PDF a la charte. A utiliser en try-with-resources
     * ou en appelant {@link JurikaPdfDocument#finish()} qui ferme le document.
     */
    public static JurikaPdfDocument newDocument() {
        return new JurikaPdfDocument();
    }

    /**
     * Resolution UNIQUE du nom du cabinet affiche en en-tete des PDF : nom affiche
     * personnalise si renseigne, sinon la denomination du workspace. Garantit un
     * en-tete jamais vide. A appeler par chaque service AVANT de passer le nom au
     * theme, pour que la logique de repli ne soit ecrite qu'une seule fois.
     *
     * @param nomAfficheDocuments valeur personnalisee (peut etre null/vide)
     * @param workspaceName       denomination du workspace (repli)
     */
    public static String resolveCabinetName(String nomAfficheDocuments, String workspaceName) {
        if (nomAfficheDocuments != null && !nomAfficheDocuments.isBlank()) {
            return nomAfficheDocuments.trim();
        }
        if (workspaceName != null && !workspaceName.isBlank()) {
            return workspaceName.trim();
        }
        return "Cabinet";
    }
}
