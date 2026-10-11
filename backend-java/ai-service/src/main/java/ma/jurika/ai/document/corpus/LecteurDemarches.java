package ma.jurika.ai.document.corpus;

import ma.jurika.ai.document.corpus.ReferentielDemarches.Condition;
import ma.jurika.ai.document.corpus.ReferentielDemarches.Decision;
import ma.jurika.ai.document.corpus.ReferentielDemarches.Delai;
import ma.jurika.ai.document.corpus.ReferentielDemarches.Ligne;
import ma.jurika.ai.document.corpus.ReferentielDemarches.Rapport;
import ma.jurika.ai.document.corpus.ReferentielDemarches.Renvoi;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lot L4b (E1, RG-DEM-01) : lecture du referentiel des demarches dans les matrices du
 * corpus ({@code <famille>/MATRICES/*.xlsx}).
 *
 * <p>Trois formats d'onglet "Demarches" : 12 colonnes (9 matrices), 9 colonnes (succursale
 * d'une societe etrangere, import de dossier). L'en-tete est la ligne dont la premiere
 * cellule commence par "Decision ($RESOLUTION_TYPE)". Le parcours de creation n'a pas
 * d'onglet "Demarches" : son onglet unique est lu a part ({@link #lireParcoursCreation}),
 * pour comparaison avec le referentiel en service.
 *
 * <p>Analyse sans invention : une condition que l'on ne sait pas lire devient une PHRASE a
 * apprecier par l'employe ; un delai sans valeur exploitable reste en texte ; un statut
 * inconnu est une erreur (import refuse).
 */
public final class LecteurDemarches {

    static final String ENTETE_DEMARCHES = "D\u00e9cision ($RESOLUTION_TYPE)";
    static final String COL_RESOLUTION = "$RESOLUTION_TYPE";
    static final String COL_DECISION = "D\u00e9cision / R\u00e9solution";
    static final String PARCOURS_CREATION = "PARCOURS_CREATION";

    /** Libelles des statuts dans les matrices -> codes du ticket. */
    static final Map<String, String> STATUTS = Map.of(
            "Cr\u00e9ation du ticket et collecte d'information", "CREATION_TICKET",
            "G\u00e9n\u00e9ration des documents", "GENERATION_DOCUMENTS",
            "D\u00e9roulement de la d\u00e9marche", "DEROULEMENT_DEMARCHE",
            "Cl\u00f4ture de dossier", "CLOTURE_DOSSIER",
            "Annul\u00e9", "ANNULE",
            "Ticket annul\u00e9", "ANNULE");

    private static final Pattern COND_EGAL =
            Pattern.compile("^\\$([A-Z][A-Z0-9_]*) = \u00ab ([^\u00bb]+) \u00bb(?:\\s*\u2014\\s*\\((.*)\\))?\\s*$", Pattern.DOTALL);
    private static final Pattern COND_EXISTE =
            Pattern.compile("^\\$([A-Z][A-Z0-9_]*) existe(?:\\s*\u2014\\s*\\((.*)\\))?\\s*$", Pattern.DOTALL);
    private static final Pattern DECISION =
            Pattern.compile("^([a-z0-9_]+)\\s*(?:\\((.*)\\))?\\s*$", Pattern.DOTALL);
    private static final Pattern SOUS_COND =
            Pattern.compile("^\\$([A-Z][A-Z0-9_]*) = \u00ab ([^\u00bb]+) \u00bb\\s*(?:,\\s*(.*))?$", Pattern.DOTALL);
    private static final Pattern DELAI_TEXTE =
            Pattern.compile("^(\\d+) (jours?|mois|ans?) \u2014 texte de la matrice : (.*)$", Pattern.DOTALL);
    private static final Pattern DELAI_DEPUIS =
            Pattern.compile("^(\\d+) (jours?|mois|ans?) \u00e0 compter de \\$([A-Z][A-Z0-9_]*)\\b.*?(?:\u2014\\s*\\$([A-Z][A-Z0-9_]*))?\\s*$",
                    Pattern.DOTALL);
    private static final Pattern DELAI_AU_PLUS_TARD =
            Pattern.compile("^au plus tard le \\$([A-Z][A-Z0-9_]*)\\b.*$", Pattern.DOTALL);
    private static final Pattern DELAI_LEGAL = Pattern.compile("^D\u00e9lai l\u00e9gal de la d\u00e9cision : (.*)$", Pattern.DOTALL);

    private LecteurDemarches() {
    }

    /** Lit toutes les matrices du corpus (hors parcours de creation). */
    public static ReferentielDemarches lire(Path racine) {
        Path absolue = racine.toAbsolutePath().normalize();
        List<Ligne> lignes = new ArrayList<>();
        List<Renvoi> renvois = new ArrayList<>();
        List<Decision> decisions = new ArrayList<>();
        Map<String, Integer> parOnglet = new LinkedHashMap<>();
        List<String> phrases = new ArrayList<>();
        List<String> delaisTextuels = new ArrayList<>();
        List<String> erreurs = new ArrayList<>();
        for (Path fichier : matrices(absolue)) {
            String matrice = nomSansExtension(fichier);
            if (matrice.equals(PARCOURS_CREATION)) continue;
            String famille = fichier.getParent().getParent().getFileName().toString();
            try (Workbook wb = WorkbookFactory.create(fichier.toFile(), null, true)) {
                Sheet demarches = null;
                for (Sheet s : wb) {
                    String n = s.getSheetName();
                    if (n.equals("D\u00e9marches") || n.equals("Demarches")) demarches = s;
                    else if (n.equals("Renvois")) renvois.addAll(lireRenvois(matrice, s));
                }
                decisions.addAll(lireDecisions(matrice, wb.getSheetAt(0)));
                if (demarches == null) {
                    erreurs.add(matrice + " : onglet \u00ab D\u00e9marches \u00bb absent");
                    continue;
                }
                List<Ligne> lues = lireDemarches(matrice, famille, forme(matrice), demarches, phrases,
                        delaisTextuels, erreurs);
                parOnglet.put(matrice, lues.size());
                lignes.addAll(lues);
            } catch (IOException | RuntimeException ex) {
                erreurs.add(matrice + " : classeur illisible : " + ex.getMessage());
            }
        }
        if (parOnglet.isEmpty()) erreurs.add("Aucun onglet \u00ab D\u00e9marches \u00bb lu");
        return new ReferentielDemarches(absolue.getFileName().toString(), lignes, renvois, decisions,
                new Rapport(parOnglet, lignes.size(), phrases, delaisTextuels, erreurs));
    }

    /**
     * Onglet unique du parcours de creation (N, statut, etape, condition, delai, document,
     * justificatif, modele, donnees). Les titres de section (sans numero) sont ignores.
     */
    public static List<Ligne> lireParcoursCreation(Path racine) {
        Path fichier = matrices(racine.toAbsolutePath().normalize()).stream()
                .filter(p -> nomSansExtension(p).equals(PARCOURS_CREATION)).findFirst()
                .orElseThrow(() -> new CorpusException("Parcours de cr\u00e9ation introuvable"));
        DataFormatter f = new DataFormatter();
        List<Ligne> out = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(fichier.toFile(), null, true)) {
            Sheet s = wb.getSheetAt(0);
            boolean apresEntete = false;
            for (Row r : s) {
                String n = cellule(f, r, 0);
                if (!apresEntete) {
                    apresEntete = n.equals("N\u00b0");
                    continue;
                }
                if (!n.matches("\\d+")) continue;
                String statutTexte = cellule(f, r, 1);
                String delai = cellule(f, r, 4);
                out.add(new Ligne(PARCOURS_CREATION, fichier.getParent().getParent().getFileName().toString(),
                        null, Integer.parseInt(n), "creation", null, STATUTS.getOrDefault(statutTexte, statutTexte),
                        cellule(f, r, 2), null, condition(cellule(f, r, 3)), true,
                        delai.isEmpty() ? null : new Delai(delai, null, null, null, null), vide(cellule(f, r, 6)),
                        vide(cellule(f, r, 7)), Integer.parseInt(n), null, vide(cellule(f, r, 8))));
            }
        } catch (IOException ex) {
            throw new CorpusException("Parcours de cr\u00e9ation illisible : " + ex.getMessage(), ex);
        }
        return out;
    }

    static List<Ligne> lireDemarches(String matrice, String famille, String forme, Sheet s, List<String> phrases,
                                     List<String> delaisTextuels, List<String> erreurs) {
        DataFormatter f = new DataFormatter();
        List<Ligne> out = new ArrayList<>();
        boolean apresEntete = false;
        int ordre = 0;
        for (Row r : s) {
            String premiere = cellule(f, r, 0);
            if (!apresEntete) {
                apresEntete = premiere.startsWith(ENTETE_DEMARCHES);
                continue;
            }
            if (premiere.isEmpty()) continue;
            ordre++;
            String lieu = matrice + " l. " + ordre;
            Matcher md = DECISION.matcher(premiere);
            String decision = premiere;
            Condition sous = null;
            if (md.matches()) {
                decision = md.group(1);
                if (md.group(2) != null) sous = sousCondition(md.group(2));
            } else {
                erreurs.add(lieu + " : d\u00e9cision illisible \u00ab " + premiere + " \u00bb");
            }
            String statutTexte = cellule(f, r, 1);
            String statut = STATUTS.get(statutTexte);
            if (statut == null) {
                erreurs.add(lieu + " : statut inconnu \u00ab " + statutTexte + " \u00bb");
                statut = statutTexte;
            }
            Condition condition = condition(cellule(f, r, 4));
            if (condition != null && "PHRASE".equals(condition.operateur())) phrases.add(lieu + " : " + condition.texte());
            Delai delai = delai(cellule(f, r, 6));
            if (delai != null && delai.valeur() == null && delai.echeanceVariable() == null) {
                delaisTextuels.add(lieu + " : " + delai.texte());
            }
            String numero = cellule(f, r, 9);
            out.add(new Ligne(matrice, famille, forme, ordre, decision, sous, statut, cellule(f, r, 2),
                    vide(cellule(f, r, 3)), condition, !"Conditionnelle".equalsIgnoreCase(cellule(f, r, 5)), delai,
                    vide(cellule(f, r, 7)), vide(cellule(f, r, 8)),
                    numero.matches("\\d+") ? Integer.valueOf(numero) : null,
                    vide(cellule(f, r, 10)), vide(cellule(f, r, 11))));
        }
        if (!apresEntete) erreurs.add(matrice + " : en-t\u00eate \u00ab " + ENTETE_DEMARCHES + " \u00bb introuvable");
        return out;
    }

    static List<Renvoi> lireRenvois(String matrice, Sheet s) {
        DataFormatter f = new DataFormatter();
        List<Renvoi> out = new ArrayList<>();
        boolean apresEntete = false;
        for (Row r : s) {
            String n = cellule(f, r, 0);
            if (!apresEntete) {
                apresEntete = n.startsWith("N\u00b0");
                continue;
            }
            if (!n.matches("\\d+")) continue;
            String num = cellule(f, r, 6);
            out.add(new Renvoi(matrice, Integer.parseInt(n), vide(cellule(f, r, 1)), vide(cellule(f, r, 2)),
                    cellule(f, r, 3), vide(cellule(f, r, 4)), cellule(f, r, 5),
                    num.matches("\\d+") ? Integer.valueOf(num) : null, vide(cellule(f, r, 7))));
        }
        return out;
    }

    /**
     * Decisions de l'onglet d'origine : sous chaque en-tete "N ... $RESOLUTION_TYPE (moteur)",
     * les lignes numerotees ; la section est la derniere ligne de titre rencontree.
     */
    static List<Decision> lireDecisions(String matrice, Sheet s) {
        DataFormatter f = new DataFormatter();
        List<Decision> out = new ArrayList<>();
        int colCode = -1;
        int colLibelle = -1;
        int colType = 1;
        String section = null;
        for (Row r : s) {
            String n = cellule(f, r, 0);
            if (n.equals("N\u00b0")) {
                colCode = -1;
                colLibelle = -1;
                for (Cell c : r) {
                    String v = f.formatCellValue(c).trim();
                    if (v.startsWith(COL_RESOLUTION)) colCode = c.getColumnIndex();
                    if (v.equals(COL_DECISION)) colLibelle = c.getColumnIndex();
                }
                continue;
            }
            if (n.matches("\\d+") && colCode >= 0) {
                String code = cellule(f, r, colCode);
                if (code.matches("[a-z0-9_]+")) {
                    out.add(new Decision(matrice, Integer.parseInt(n), section, code,
                            colLibelle >= 0 ? vide(cellule(f, r, colLibelle)) : null, vide(cellule(f, r, colType))));
                }
            } else if (n.matches("^[A-Z] \u2014 .*")) {
                section = n;
            }
        }
        return out;
    }

    static Condition condition(String brut) {
        String c = brut == null ? "" : brut.trim();
        if (c.isEmpty() || c.equalsIgnoreCase("Tous dossiers")) return null;
        Matcher m = COND_EGAL.matcher(c);
        if (m.matches()) return new Condition(m.group(1), "EGAL", m.group(2), m.group(3));
        m = COND_EXISTE.matcher(c);
        if (m.matches()) return new Condition(m.group(1), "EXISTE", null, m.group(2));
        return new Condition(null, "PHRASE", null, c);
    }

    /** Sous-condition de la colonne Decision : {@code $VAR = << v >>[, reste]} ; le reste est conserve en texte. */
    static Condition sousCondition(String brut) {
        Matcher m = SOUS_COND.matcher(brut.trim());
        if (m.matches()) return new Condition(m.group(1), "EGAL", m.group(2), vide(m.group(3)));
        return new Condition(null, "PHRASE", null, brut.trim());
    }

    static Delai delai(String brut) {
        String d = brut == null ? "" : brut.trim();
        if (d.isEmpty()) return null;
        Matcher legal = DELAI_LEGAL.matcher(d);
        if (legal.matches()) {
            String t = legal.group(1).trim();
            // "Delai legal de la decision : --" : la matrice n'en fixe aucun.
            if (t.isEmpty() || t.equals("\u2014")) return null;
            return new Delai(d, null, null, null, null);
        }
        Matcher m = DELAI_DEPUIS.matcher(d);
        if (m.matches()) {
            int[] vu = valeurUnite(m.group(1), m.group(2));
            return new Delai(d, vu[0], unite(vu[1]), m.group(3), m.group(4));
        }
        m = DELAI_TEXTE.matcher(d);
        if (m.matches()) {
            int[] vu = valeurUnite(m.group(1), m.group(2));
            return new Delai(d, vu[0], unite(vu[1]), null, null);
        }
        m = DELAI_AU_PLUS_TARD.matcher(d);
        if (m.matches()) return new Delai(d, null, null, null, m.group(1));
        return new Delai(d, null, null, null, null);
    }

    /** Annees converties en mois (12 mois calendaires par an) ; le calcul reste en mois. */
    private static int[] valeurUnite(String valeur, String unite) {
        int v = Integer.parseInt(valeur);
        if (unite.startsWith("an")) return new int[]{v * 12, 1};
        return new int[]{v, unite.startsWith("mois") ? 1 : 0};
    }

    private static String unite(int code) {
        return code == 1 ? "MOIS" : "JOURS";
    }

    static String forme(String matrice) {
        if (matrice.endsWith("_SARL_AU")) return "SARL_AU";
        if (matrice.endsWith("_SARL")) return "SARL";
        if (matrice.endsWith("_SOCIETE_ETRANGERE")) return "ETRANGERE";
        return null;
    }

    static List<Path> matrices(Path racine) {
        try (Stream<Path> familles = Files.list(racine)) {
            List<Path> out = new ArrayList<>();
            for (Path famille : familles.filter(Files::isDirectory).sorted().toList()) {
                Path dossier = famille.resolve("MATRICES");
                if (!Files.isDirectory(dossier)) continue;
                try (Stream<Path> fichiers = Files.list(dossier)) {
                    fichiers.filter(p -> p.getFileName().toString().endsWith(".xlsx")).sorted().forEach(out::add);
                }
            }
            return out;
        } catch (IOException ex) {
            throw new CorpusException("Matrices illisibles : " + ex.getMessage(), ex);
        }
    }

    private static String nomSansExtension(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.length() - ".xlsx".length());
    }

    private static String cellule(DataFormatter f, Row r, int i) {
        Cell c = r.getCell(i);
        return c == null ? "" : f.formatCellValue(c).trim();
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
