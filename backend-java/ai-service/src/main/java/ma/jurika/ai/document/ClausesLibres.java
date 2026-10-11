package ma.jurika.ai.document;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lot L3 (RG-GEN-05 a 07) : clauses libres dans les proces-verbaux.
 *
 * <p>Emplacement prevu par le modele (le gabarit n'est jamais modifie, P8) : la boucle
 * {@code RESOLUTIONS} qui imprime, pour chaque resolution, {@code $RESOLUTION_INTITULE}
 * puis {@code $RESOLUTION_TEXTE}. Une clause libre y devient une resolution de plus,
 * "a la suite des resolutions", imprimee telle que saisie dans la mise en forme du
 * modele. Un modele sans cette boucle n'a pas d'emplacement : la clause n'y est pas
 * imprimee en silence, la generation le refuse.
 *
 * <p>Lot L4d (E10), decision du directeur du 11 octobre 2026 : l'employe choisit
 * l'emplacement (a la suite des resolutions, ou avant la resolution n). Statuts : voir
 * {@link ClausesStatuts} (apres l'article n du document rendu).
 */
public final class ClausesLibres {

    public static final String EMPLACEMENT = "A_LA_SUITE_DES_RESOLUTIONS";
    public static final String LIBELLE_EMPLACEMENT = "À la suite des résolutions";
    /** Lot L4d (E10) : avant la resolution n du modele ("AVANT_RESOLUTION:n"). */
    public static final String AVANT_RESOLUTION = "AVANT_RESOLUTION:";

    private static final String[] ORDINAUX = {"", "PREMIÈRE", "DEUXIÈME", "TROISIÈME", "QUATRIÈME", "CINQUIÈME",
            "SIXIÈME", "SEPTIÈME", "HUITIÈME", "NEUVIÈME", "DIXIÈME", "ONZIÈME", "DOUZIÈME", "TREIZIÈME",
            "QUATORZIÈME", "QUINZIÈME", "SEIZIÈME", "DIX-SEPTIÈME", "DIX-HUITIÈME", "DIX-NEUVIÈME", "VINGTIÈME"};

    private ClausesLibres() {
    }

    /** Le gabarit prevoit-il l'emplacement (boucle RESOLUTIONS a intitule et texte) ? */
    public static boolean emplacementPrevu(byte[] docx) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx));
             XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
            String t = ex.getText();
            int debut = t.indexOf("BOUCLE — RESOLUTIONS");
            int fin = t.indexOf("FIN BOUCLE — RESOLUTIONS");
            if (debut < 0 || fin < debut) return false;
            String boucle = t.substring(debut, fin);
            // Lot L4d (E11c) : PV du corpus (PV_AG_SARL, decisions de l'associe unique) :
            // "$RESOLUTION_ORDINAL ... -- $RESOLUTION_OBJET" puis, pour un type non catalogue,
            // la branche "$RESOLUTION_TEXTE_LIBRE".
            boolean corpus = boucle.contains("$RESOLUTION_OBJET") && boucle.contains("$RESOLUTION_TEXTE_LIBRE");
            return corpus || (boucle.contains("$RESOLUTION_INTITULE")
                    && boucle.matches("(?s).*\\$RESOLUTION_TEXTE(?![A-Z_]).*"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Gabarit illisible", e);
        }
    }

    /**
     * Ajoute chaque clause a la suite des resolutions du mapper. La numerotation suit
     * celle du mapper ("TROISIEME RESOLUTION" -> "QUATRIEME RESOLUTION", ou 3 -> 4).
     */
    public static Map<String, Object> inserer(Map<String, Object> variables, List<Map<String, Object>> clauses) {
        return inserer(null, variables, clauses);
    }

    /**
     * Lot L4d (E10), decision du directeur du 11 octobre 2026 : l'employe choisit l'emplacement
     * de chaque clause -- a la suite des resolutions, ou avant la resolution n
     * ({@code AVANT_RESOLUTION:n}). Les resolutions sont renumerotees ; les renvois ordinaux
     * des resolutions du modele ("la deuxieme resolution") suivent. Un renvoi qu'on ne sait
     * pas mettre a jour (numero en chiffres vise par le decalage) refuse la generation.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> inserer(String templateCode, Map<String, Object> variables,
                                              List<Map<String, Object>> clauses) {
        Map<String, Object> out = new LinkedHashMap<>(variables);
        List<Map<String, Object>> modeleResolutions = new ArrayList<>();
        if (variables.get("RESOLUTIONS") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) modeleResolutions.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        }
        int n = modeleResolutions.size();
        String modele = modeleResolutions.isEmpty() ? null
                : String.valueOf(modeleResolutions.get(n - 1).getOrDefault("RESOLUTION_NUMERO", ""));
        // Lot L4d (E11c) : resolutions typees des PV du corpus ($RESOLUTION_TYPE, $RESOLUTION_ORDINAL).
        boolean corpus = modeleResolutions.stream().anyMatch(r -> r.containsKey("RESOLUTION_TYPE"));
        String majorite = corpus ? majoriteCommune(modeleResolutions) : null;
        if (corpus) {
            for (Map<String, Object> c : clauses) {
                String resultat = c.get("resultat") == null ? "" : String.valueOf(c.get("resultat")).trim();
                if (resultat.toLowerCase(Locale.ROOT).startsWith("rejet")) {
                    // "Cette resolution, mise aux voix, est adoptee $RESOLUTION_RESULTAT" : le modele
                    // n'ecrit qu'une resolution adoptee.
                    throw new ClauseLibreImpossibleException(templateCode, "Clause libre impossible : la clause \u00ab "
                            + c.get("titre") + " \u00bb est rejet\u00e9e, or le mod\u00e8le n'\u00e9crit qu'une r\u00e9solution adopt\u00e9e. "
                            + "Retirez la clause de ce document.");
                }
            }
        }
        // Position de chaque clause : avant la resolution k du modele (1..n), ou n+1 (a la suite).
        List<int[]> places = new ArrayList<>();
        for (int j = 0; j < clauses.size(); j++) {
            int avant = avant(clauses.get(j).get("emplacement"));
            if (avant == 0) avant = n + 1;
            if (avant < 1 || avant > n + 1) {
                throw new ClauseLibreImpossibleException(templateCode, "Clause libre impossible \u00e0 placer : la clause \u00ab "
                        + clauses.get(j).get("titre") + " \u00bb doit se placer avant une r\u00e9solution existante (1 \u00e0 "
                        + n + ") ou \u00e0 la suite des r\u00e9solutions.");
            }
            places.add(new int[]{avant, j});
        }
        // Nouveau rang de chaque resolution du modele (1..n).
        int[] nouveauRang = new int[n + 1];
        for (int k = 1; k <= n; k++) {
            int d = 0;
            for (int[] p : places) if (p[0] <= k) d++;
            nouveauRang[k] = k + d;
        }
        List<Map<String, Object>> resolutions = new ArrayList<>();
        for (int k = 1; k <= n + 1; k++) {
            for (int[] p : places) {
                if (p[0] != k) continue;
                Map<String, Object> c = clauses.get(p[1]);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("RESOLUTION_INTITULE", c.get("titre"));
                r.put("RESOLUTION_TEXTE", c.get("texte"));
                r.put("RESOLUTION_RESULTAT", c.get("resultat"));
                r.put("RESOLUTION_VOIX_POUR", c.get("voixPour"));
                r.put("RESOLUTION_VOIX_CONTRE", c.get("voixContre"));
                r.put("RESOLUTION_ABSTENTIONS", c.get("abstentions"));
                if (corpus) {
                    // Type non catalogue : le modele imprime $RESOLUTION_TEXTE_LIBRE ; la majorite
                    // est celle du vote saisi pour le proces-verbal (commune a ses resolutions).
                    r.put("RESOLUTION_TYPE", "clause_libre");
                    r.put("RESOLUTION_OBJET", c.get("titre"));
                    r.put("RESOLUTION_TEXTE_LIBRE", c.get("texte"));
                    r.put("RESOLUTION_RESULTAT", majorite == null ? "" : majorite);
                }
                resolutions.add(r);
            }
            if (k <= n) {
                Map<String, Object> r = modeleResolutions.get(k - 1);
                for (String champ : new String[]{"RESOLUTION_INTITULE", "RESOLUTION_TEXTE",
                        "RESOLUTION_OBJET", "RESOLUTION_TEXTE_LIBRE"}) {
                    if (r.get(champ) instanceof String t) r.put(champ, renvois(templateCode, t, nouveauRang));
                }
                resolutions.add(r);
            }
        }
        for (int i = 0; i < resolutions.size(); i++) {
            resolutions.get(i).put("RESOLUTION_NUMERO", numero(modele, i + 1));
            if (corpus) {
                resolutions.get(i).put("RESOLUTION_ORDINAL", i + 1 < ORDINAUX.length ? ORDINAUX[i + 1] : (i + 1) + "E");
            }
        }
        out.put("RESOLUTIONS", resolutions);
        return out;
    }

    /** Majorite imprimee par les resolutions du modele, si elle est la meme pour toutes ; sinon null. */
    private static String majoriteCommune(List<Map<String, Object>> resolutions) {
        String commune = null;
        for (Map<String, Object> r : resolutions) {
            String m = r.get("RESOLUTION_RESULTAT") == null ? "" : String.valueOf(r.get("RESOLUTION_RESULTAT")).trim();
            if (m.isEmpty() || (commune != null && !commune.equals(m))) return null;
            commune = m;
        }
        return commune;
    }

    static int avant(Object emplacement) {
        String e = emplacement == null ? "" : String.valueOf(emplacement).trim();
        if (e.isEmpty() || EMPLACEMENT.equals(e)) return 0;
        if (!e.startsWith(AVANT_RESOLUTION)) return -1;
        try {
            return Integer.parseInt(e.substring(AVANT_RESOLUTION.length()).trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static final java.util.regex.Pattern RENVOI_ORDINAL = java.util.regex.Pattern.compile(
            "\\b(PREMI\u00c8RE|DEUXI\u00c8ME|TROISI\u00c8ME|QUATRI\u00c8ME|CINQUI\u00c8ME|SIXI\u00c8ME|SEPTI\u00c8ME|HUITI\u00c8ME|NEUVI\u00c8ME|DIXI\u00c8ME"
                    + "|ONZI\u00c8ME|DOUZI\u00c8ME|TREIZI\u00c8ME|QUATORZI\u00c8ME|QUINZI\u00c8ME|SEIZI\u00c8ME|DIX-SEPTI\u00c8ME|DIX-HUITI\u00c8ME"
                    + "|DIX-NEUVI\u00c8ME|VINGTI\u00c8ME)(\\s+r\u00e9solution)",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern RENVOI_CHIFFRE = java.util.regex.Pattern.compile(
            "(r\u00e9solution\\s+n\\s*\u00b0\\s*\\d+|\\b\\d+\\s*(?:e|\u00e8me|eme)\\s+r\u00e9solution)",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    /** Met a jour les renvois ordinaux d'un texte du modele ; refuse ce qui ne se met pas a jour. */
    static String renvois(String templateCode, String texte, int[] nouveauRang) {
        boolean decalage = false;
        for (int k = 1; k < nouveauRang.length; k++) if (nouveauRang[k] != k) decalage = true;
        if (!decalage) return texte;
        java.util.regex.Matcher chiffre = RENVOI_CHIFFRE.matcher(texte);
        if (chiffre.find()) {
            throw new ClauseLibreImpossibleException(templateCode, "Clause libre impossible \u00e0 placer : le renvoi \u00ab "
                    + chiffre.group() + " \u00bb d'une r\u00e9solution du mod\u00e8le ne peut pas \u00eatre mis \u00e0 jour "
                    + "automatiquement. Placez la clause \u00e0 la suite des r\u00e9solutions.");
        }
        java.util.regex.Matcher m = RENVOI_ORDINAL.matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String mot = m.group(1);
            int rang = 0;
            for (int i = 1; i < ORDINAUX.length; i++) if (ORDINAUX[i].equalsIgnoreCase(mot)) rang = i;
            String remplace = m.group();
            if (rang > 0 && rang < nouveauRang.length && nouveauRang[rang] != rang) {
                int nouveau = nouveauRang[rang];
                if (nouveau >= ORDINAUX.length) {
                    throw new ClauseLibreImpossibleException(templateCode, "Clause libre impossible \u00e0 placer : la "
                            + "r\u00e9solution renvoy\u00e9e deviendrait la " + nouveau + "e ; placez la clause \u00e0 la suite.");
                }
                String o = ORDINAUX[nouveau];
                if (mot.equals(mot.toLowerCase(Locale.ROOT))) o = o.toLowerCase(Locale.ROOT);
                else if (!mot.equals(mot.toUpperCase(Locale.ROOT))) o = o.charAt(0) + o.substring(1).toLowerCase(Locale.ROOT);
                remplace = o + m.group(2);
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(remplace));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String numero(String modele, int rang) {
        if (modele == null || modele.isBlank() || modele.trim().matches("\\d+")) {
            return String.valueOf(rang);
        }
        String m = modele.trim();
        int espace = m.indexOf(' ');
        String mot = espace < 0 ? m : m.substring(0, espace);
        String suite = espace < 0 ? "" : m.substring(espace);
        for (int i = 1; i < ORDINAUX.length; i++) {
            if (ORDINAUX[i].equalsIgnoreCase(mot)) {
                String nouveau = rang < ORDINAUX.length ? ORDINAUX[rang] : rang + "E";
                boolean majuscules = mot.equals(mot.toUpperCase(Locale.ROOT));
                if (!majuscules) {
                    nouveau = nouveau.charAt(0) + nouveau.substring(1).toLowerCase(Locale.ROOT);
                }
                return nouveau + suite;
            }
        }
        return String.valueOf(rang);
    }
}
