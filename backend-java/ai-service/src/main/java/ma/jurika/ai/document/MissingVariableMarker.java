package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passe universelle de mise en rouge des variables manquantes.
 *
 * <p>{@link DocxTemplateEngine#resolveUpper(String, java.util.Map, java.util.Map, String)}
 * et {@code resolveLower} emettent, lorsqu'une variable est introuvable, un sentinel
 * {@link #SENTINEL_OPEN}{@code NOM_VAR}{@link #SENTINEL_CLOSE} (caracteres Private Use
 * Area garantis absents du contenu juridique). Cette classe parcourt le document
 * post-substitution (body + tableaux + headers + footers), detecte les sentinels et
 * reconstruit les runs en remplacant chaque sentinel par un run gras+rouge
 * <code>&laquo; VALEUR MANQUANTE : NOM &raquo;</code> (couleur {@code C00000}),
 * en preservant la police/taille des segments voisins (clonage depuis le 1er run du
 * paragraphe).
 *
 * <p>Pure fonction des octets passes ; idempotent (executer la passe deux fois donne
 * le meme resultat des lors que les sentinels ont disparu). Aucune dependance externe.
 *
 * @return {@link #apply(XWPFDocument)} retourne la liste ordonnee et dedupliquee des
 *         noms de variables manquantes rencontrees.
 */
public final class MissingVariableMarker {

    /**
     * Sentinel d'ouverture (Unicode Private Use Area U+E000) injecte par
     * {@link DocxTemplateEngine} pour delimiter le nom d'une variable manquante.
     */
    public static final char SENTINEL_OPEN = '';

    /** Sentinel de fermeture (Unicode Private Use Area U+E001). */
    public static final char SENTINEL_CLOSE = '';

    /** Pattern de recuperation d'un marqueur sentinel {@code <SENTINEL_OPEN>NOM<SENTINEL_CLOSE>}. */
    private static final Pattern SENTINEL_PATTERN = Pattern.compile(
            "([^]+)");

    /** Prefixe humain affiche a la place d'une variable manquante. */
    private static final String MISSING_PREFIX = "‹ VALEUR MANQUANTE : ";

    /** Suffixe humain. */
    private static final String MISSING_SUFFIX = " ›";

    /**
     * Sprint Cowork 2026-06-21 (C3) — Libelle affiche pour les variables connues
     * POST-immatriculation (declarees dans {@code dictionary.json#fill_later}).
     * Volontairement court et neutre, mais TOUJOURS en ROUGE pour signaler que
     * l'employe doit revenir remplir la valeur reelle apres immatriculation.
     */
    private static final String FILL_LATER_LABEL = "[à compléter]";

    /** Rouge sombre lisible sur fond blanc (Word color code). */
    private static final String MISSING_COLOR = "C00000";

    private MissingVariableMarker() {}

    /**
     * Compose un sentinel {@code <SENTINEL_OPEN>NOM<SENTINEL_CLOSE>}.
     */
    public static String sentinel(String name) {
        return String.valueOf(SENTINEL_OPEN) + name + SENTINEL_CLOSE;
    }

    /**
     * Applique la passe sur le document. Doit etre appelee APRES la substitution
     * scalaire de {@link DocxTemplateEngine}.
     *
     * <p>Compat : appelle {@link #apply(XWPFDocument, Set)} avec un set fillLater
     * vide -> toutes les variables manquantes ressortent en "‹ VALEUR MANQUANTE ›".
     *
     * @return liste ordonnee et dedupliquee des noms de variables manquantes.
     */
    public static List<String> apply(XWPFDocument doc) {
        return apply(doc, Collections.emptySet());
    }

    /**
     * Sprint Cowork 2026-06-21 (C3) — Variante avec set des variables
     * "fill later" (post-immatriculation). Pour chaque sentinel rencontre :
     * <ul>
     *   <li>Si la variable est dans {@code fillLaterKeys} -> rendu rouge
     *       {@value #FILL_LATER_LABEL} (libelle court neutre).</li>
     *   <li>Sinon -> rendu rouge
     *       {@code "‹ VALEUR MANQUANTE : NOM ›"} (signal de bug / champ
     *       oublie a saisir).</li>
     * </ul>
     *
     * @param doc document a annoter.
     * @param fillLaterKeys ensemble (UPPERCASE_SNAKE) des variables
     *        post-immatriculation. Comparison casse-insensible (uppercase
     *        applique avant lookup).
     * @return liste ordonnee et dedupliquee des noms de variables manquantes
     *         (toutes, fillLater incluses).
     */
    public static List<String> apply(XWPFDocument doc, Set<String> fillLaterKeys) {
        List<Manquante> detail = applyDetailed(doc, fillLaterKeys);
        List<String> noms = new ArrayList<>(detail.size());
        for (Manquante m : detail) noms.add(m.nom());
        return noms;
    }

    /**
     * Une variable non resolue, avec de quoi la juger : l'endroit ou elle apparait
     * et la nature de ce vide.
     *
     * @param nom       nom de la variable (sans le {@code $})
     * @param endroit   texte du paragraphe ou elle apparait (marqueurs rendus), pour
     *                  dire a l'employe OU se trouve le trou
     * @param bloquante {@code true} si le vide apparait DANS UNE PHRASE — c'est le
     *                  defaut « ne le  a , demeurant a  » ; {@code false} si la
     *                  variable est seule sur sa ligne apres un libelle, cas d'une
     *                  case de formulaire administratif, qu'un blanc laisse recevable
     */
    public record Manquante(String nom, String endroit, boolean bloquante) {}

    /**
     * Lot 5 (2026-09-07) — variante detaillee : meme passe de rendu, mais elle dit
     * AUSSI ou est le trou et s'il se voit dans une phrase.
     *
     * <p>La regle de classement est mecanique, et se lit sur le paragraphe RENDU
     * (donc apres suppression des branches conditionnelles non retenues et des
     * boucles vides — un vide qui ne s'imprime pas n'existe pas) :
     *
     * <ul>
     *   <li>le paragraphe ne porte QU'UNE variable, et rien ne la suit → c'est une
     *       CASE : « Ville : » sans ville reste un formulaire recevable ;</li>
     *   <li>du texte suit la variable, ou une autre variable figure dans le meme
     *       paragraphe → c'est une PHRASE : le blanc se lit. « Date et lieu de
     *       naissance : 12/03/1980 à  » et « ne le  a , demeurant a  » tombent tous
     *       les deux ici.</li>
     * </ul>
     *
     * <p>Une variable {@code fill_later} n'est JAMAIS bloquante : sa valeur n'existe
     * pas encore a la generation, par construction.
     */
    public static List<Manquante> applyDetailed(XWPFDocument doc, Set<String> fillLaterKeys) {
        return applyDetailed(doc, fillLaterKeys, false);
    }

    /**
     * Lot 5 (2026-09-07) — VARIANTE POUR LES IMPRIMES ADMINISTRATIFS.
     *
     * <p>Decouvert en conditions reelles, sur le document produit et non sur un
     * test : une case laissee blanche sortait avec
     * « ‹ VALEUR MANQUANTE : ASSOCIE_PRINCIPAL_FAX › » EN ROUGE, imprime sur un
     * formulaire destine a la DGI. Le controle de completude avait pourtant raison
     * de laisser passer — une case vide reste recevable ; c'est le RENDU qui
     * contredisait cette regle en criant a l'erreur.
     *
     * <p>Quand {@code casesEnBlanc} vaut {@code true}, une variable manquante
     * classee CASE ne rend RIEN : le formulaire sort avec sa case vide, comme un
     * imprime rempli a la main qu'on laisse en blanc. Elle reste remontee dans le
     * resultat, donc ni perdue ni masquee.
     *
     * <p>Deux exceptions gardent leur marque, a dessein :
     * <ul>
     *   <li>une variable {@code fill_later} rend « [a completer] » — l'employe doit
     *       revenir la remplir apres immatriculation, l'imprime le lui rappelle ;</li>
     *   <li>une variable classee PHRASE garde le rouge : son vide se lit dans le
     *       texte. Sur le workflow CREATION la generation est de toute facon
     *       refusee ; ailleurs, le marqueur reste le seul signal.</li>
     * </ul>
     */
    public static List<Manquante> applyDetailed(XWPFDocument doc, Set<String> fillLaterKeys,
                                                 boolean casesEnBlanc) {
        CASES_EN_BLANC.set(casesEnBlanc);
        try {
            return applyDetailedInterne(doc, fillLaterKeys);
        } finally {
            CASES_EN_BLANC.remove();
        }
    }

    /**
     * Drapeau du rendu « case en blanc », porte par le thread le temps d'une passe.
     * Le rendu se fait au fond d'une recursion sur les paragraphes ; le faire
     * descendre en parametre aurait touche cinq signatures pour un booleen.
     */
    private static final ThreadLocal<Boolean> CASES_EN_BLANC = ThreadLocal.withInitial(() -> false);

    private static List<Manquante> applyDetailedInterne(XWPFDocument doc, Set<String> fillLaterKeys) {
        Set<String> normalized = new LinkedHashSet<>();
        if (fillLaterKeys != null) {
            for (String k : fillLaterKeys) {
                if (k != null && !k.isBlank()) {
                    normalized.add(k.toUpperCase(java.util.Locale.ROOT));
                }
            }
        }
        java.util.Map<String, Manquante> missing = new java.util.LinkedHashMap<>();
        for (XWPFParagraph p : new ArrayList<>(doc.getParagraphs())) {
            renderMissingMarkers(p, missing, normalized);
        }
        for (XWPFTable table : doc.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : new ArrayList<>(cell.getParagraphs())) {
                        renderMissingMarkers(p, missing, normalized);
                    }
                }
            }
        }
        for (XWPFHeader header : doc.getHeaderList()) {
            for (XWPFParagraph p : new ArrayList<>(header.getParagraphs())) {
                renderMissingMarkers(p, missing, normalized);
            }
        }
        for (XWPFFooter footer : doc.getFooterList()) {
            for (XWPFParagraph p : new ArrayList<>(footer.getParagraphs())) {
                renderMissingMarkers(p, missing, normalized);
            }
        }
        return new ArrayList<>(missing.values());
    }

    /**
     * Detecte les sentinels du paragraphe et reconstruit ses runs avec des
     * segments rouges+gras a la place. Preserve la police et la taille des autres
     * segments en clonant depuis le 1er run.
     *
     * <p>Sprint Cowork 2026-06-21 (C3) — Si la variable manquante est dans
     * {@code fillLaterUpper}, le libelle affiche est {@value #FILL_LATER_LABEL}
     * (rouge, court) au lieu de "‹ VALEUR MANQUANTE : NOM ›".
     */
    private static void renderMissingMarkers(XWPFParagraph p,
                                              java.util.Map<String, Manquante> missing,
                                              Set<String> fillLaterUpper) {
        List<XWPFRun> runs = p.getRuns();
        if (runs.isEmpty()) {
            // Lot 5 — un paragraphe CLONE par l'expansion de boucle porte un CTP
            // remplace par XmlBeans : le cache de runs de POI est alors obsolete et
            // `getRuns()` rend une liste vide. La passe sautait donc silencieusement
            // TOUT le contenu des boucles — un sentinel y survivait tel quel dans le
            // .docx remis au client, et n'etait jamais compte comme manquant. On
            // reconstruit le paragraphe autour du meme CTP (meme geste que
            // DocxTemplateEngine#refetchParagraph) avant de renoncer.
            p = new XWPFParagraph(p.getCTP(), p.getBody());
            runs = p.getRuns();
            if (runs.isEmpty()) return;
        }
        String full = paragraphText(p);
        if (full.indexOf(SENTINEL_OPEN) < 0) return;

        // `endroit` = le paragraphe sentinels retires, ce que l'employe lit a l'ecran.
        String endroit = SENTINEL_PATTERN.matcher(full).replaceAll("").replaceAll("\\s+", " ").trim();

        XWPFRun base = runs.get(0);
        String baseFamily = base.getFontFamily();
        int baseSize = base.getFontSize();
        boolean baseBold = base.isBold();
        boolean baseItalic = base.isItalic();

        for (int i = runs.size() - 1; i >= 0; i--) {
            p.removeRun(i);
        }

        Matcher m = SENTINEL_PATTERN.matcher(full);
        int cursor = 0;
        while (m.find()) {
            String prefix = full.substring(cursor, m.start());
            String varName = m.group(1);
            boolean bloquante = !fillLaterUpper.contains(varName.toUpperCase(java.util.Locale.ROOT))
                    && dansUnePhrase(full, m.start(), m.end());
            Manquante deja = missing.get(varName);
            if (deja == null || (bloquante && !deja.bloquante())) {
                missing.put(varName, new Manquante(varName, endroit, bloquante));
            }
            if (!prefix.isEmpty()) {
                addRun(p, prefix, baseFamily, baseSize, baseBold, baseItalic, null);
            }
            boolean fillLater = fillLaterUpper.contains(varName.toUpperCase(java.util.Locale.ROOT));
            // Imprime administratif : une CASE laissee blanche sort blanche. Crier
            // « VALEUR MANQUANTE » en rouge sur un formulaire remis a la DGI
            // contredirait la regle qui l'a laisse passer.
            boolean enBlanc = CASES_EN_BLANC.get() && !fillLater && !bloquante;
            if (!enBlanc) {
                String label = fillLater
                        ? FILL_LATER_LABEL
                        : MISSING_PREFIX + varName + MISSING_SUFFIX;
                addRun(p,
                        label,
                        baseFamily,
                        baseSize,
                        true,
                        false,
                        MISSING_COLOR);
            }
            cursor = m.end();
        }
        String tail = full.substring(cursor);
        if (!tail.isEmpty()) {
            addRun(p, tail, baseFamily, baseSize, baseBold, baseItalic, null);
        }
    }

    /**
     * Séparateurs de champs sur une même ligne. Un tiret cadratin, un point médian
     * ou une barre verticale ENTOURÉS D'ESPACES séparent deux champs ; collés à un
     * mot, ils appartiennent au mot et ne séparent rien.
     *
     * <p>Le tiret demi-cadratin {@code –} (U+2013) n'y figure pas volontairement :
     * il sert aussi d'intervalle (« 2020–2024 »), et le corpus emploie le cadratin.
     */
    private static final Pattern SEPARATEUR_DE_CHAMPS = Pattern.compile(
            "[\\s\\u00A0\\u202F][\\u2014\\u00B7|][\\s\\u00A0\\u202F]");

    /**
     * Le vide de cette variable se lit-il DANS UNE PHRASE ?
     *
     * <p>Une CASE de formulaire s'écrit « Libellé : $VAR » — un libellé, deux points,
     * la valeur, fin de ligne. La laisser blanche donne « Ville : », qui reste un
     * imprimé recevable. Tout le reste est de la prose : du texte suit la variable,
     * ou ce qui la précède n'est pas un libellé mais une liaison. C'est le cas de
     * « Date et lieu de naissance : 20/01/1980 à  » — le « à » suspendu — comme de
     * « né(e) le  à , demeurant à  ».
     *
     * <p>Un paragraphe qui ne contient QUE la variable n'est pas une phrase : il ne
     * laisse aucun mot en suspens, seulement une ligne vide.
     *
     * <h2>Lot A (2026-09-10) — LES CHAMPS GROUPÉS SUR UNE MÊME LIGNE</h2>
     * Le corpus livré le 9 septembre met plusieurs champs par ligne :
     *
     * <pre>    Téléphone : $TELEPHONE — Télécopie : $FAX — Courriel : $EMAIL</pre>
     *
     * La règle d'origine ne regardait que la LIGNE : du texte suivant
     * {@code $TELEPHONE}, elle le déclarait « dans une phrase », et un dossier sans
     * numéro de télécopie voyait sa génération refusée — alors que la case peut
     * légitimement rester vide sur un imprimé.
     *
     * <p>On raisonne donc désormais par SEGMENT. La ligne est coupée sur ses
     * séparateurs de champs ; la règle s'applique au segment qui porte la variable.
     * « Télécopie : $FAX » est une case, comme si elle occupait sa ligne.
     *
     * <p>Deux garde-fous, pour ne relâcher que ce qui doit l'être :
     * <ul>
     *   <li>le segment doit porter SON PROPRE libellé. Sur une ligne groupée, un
     *       segment réduit à la variable laisserait un séparateur en suspens
     *       (« Téléphone : 05… —  ») : il reste une phrase ;</li>
     *   <li>ce qui suit la variable DANS SON SEGMENT compte toujours. « Durée de la
     *       société : $DUREE_SOCIETE années — date d'expiration : $DATE_FIN » garde
     *       « années » suspendu : le premier champ reste une phrase, le second est
     *       une case.</li>
     * </ul>
     *
     * <p>Sans séparateur, le comportement est exactement celui d'avant.
     */
    static boolean dansUnePhrase(String full, int debut, int fin) {
        int[] segment = segmentPorteur(full, debut, fin);
        boolean ligneGroupee = segment[0] > 0 || segment[1] < full.length();

        String apres = SENTINEL_PATTERN.matcher(
                full.substring(fin, segment[1])).replaceAll("");
        if (!apres.isBlank()) return true;

        String avant = SENTINEL_PATTERN.matcher(
                full.substring(segment[0], debut)).replaceAll("");
        avant = avant.replaceAll("[\\s\\u00A0\\u202F]+$", "");
        if (avant.isBlank()) return ligneGroupee;
        return !(avant.endsWith(":") || avant.endsWith("："));
    }

    /**
     * Bornes {@code [début, fin)} du segment de ligne qui porte {@code [debut, fin)}.
     * Sans séparateur, c'est la ligne entière.
     */
    private static int[] segmentPorteur(String full, int debut, int fin) {
        int gauche = 0;
        int droite = full.length();
        Matcher sep = SEPARATEUR_DE_CHAMPS.matcher(full);
        int depuis = 0;
        while (sep.find(depuis)) {
            if (sep.end() <= debut) {
                gauche = sep.end();
            } else if (sep.start() >= fin) {
                droite = sep.start();
                break;
            }
            // Un séparateur qui chevauche la variable elle-même n'en est pas un.
            depuis = sep.start() + 1;
        }
        return new int[]{gauche, droite};
    }

    private static void addRun(XWPFParagraph p,
                                String text,
                                String family,
                                int size,
                                boolean bold,
                                boolean italic,
                                String hexColorOrNull) {
        XWPFRun r = p.createRun();
        if (family != null && !family.isBlank()) {
            r.setFontFamily(family);
        }
        if (size > 0) r.setFontSize(size);
        r.setBold(bold);
        r.setItalic(italic);
        if (hexColorOrNull != null) {
            r.setColor(hexColorOrNull);
        }
        r.setText(text, 0);
    }

    private static String paragraphText(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun r : p.getRuns()) {
            String t = r.text();
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }
}
