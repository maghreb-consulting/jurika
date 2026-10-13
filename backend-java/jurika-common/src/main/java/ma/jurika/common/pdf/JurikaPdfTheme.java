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

    // Couleurs de la charte (lot L5b E3, RG-FIC-08) : lues dans les SVG du logo Moucharabieh
    // valide (~/docs/logo/3-moucharabieh/logo-horizontal-clair.svg), aucune inventee.
    public static final Color WHITE = new Color(0xff, 0xff, 0xff);
    public static final Color BAND = new Color(0xf4, 0xf4, 0xef); // encadres / lignes alternees (fond neutre)
    /** Marine du nom JURIKA (fill #0c1d3f), aussi trait du zellige marine (pattern zeln). */
    public static final Color NAVY = new Color(0x0c, 0x1d, 0x3f);
    /** Marine clair, premier arret du degrade de l'embleme (#16325f). */
    public static final Color NAVY_SOFT = new Color(0x16, 0x32, 0x5f);
    public static final Color SLATE = new Color(0x64, 0x74, 0x8b); // libelles discrets (gris neutre lisible)
    /** Or de la devise "Gestion juridique des entreprises" (fill #a8853d) : texte et filets. */
    public static final Color GOLD = new Color(0xa8, 0x85, 0x3d);
    /** Or des filets de l'embleme (stroke #c9a24a). */
    public static final Color GOLD_LINE = new Color(0xc9, 0xa2, 0x4a);
    /** Or du motif de zellige et du treillis (stroke #d4b06a) : decor. */
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
