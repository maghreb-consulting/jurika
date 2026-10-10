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
 * « a la suite des resolutions », imprimee telle que saisie dans la mise en forme du
 * modele. Un modele sans cette boucle n'a pas d'emplacement : la clause n'y est pas
 * imprimee en silence, la generation le refuse.
 *
 * <p>Statuts : le cabinet doit ajouter aux modeles STATUTS_SARL et STATUTS_SARL_AU
 * l'emplacement des clauses particulieres (CDC § 18-6) -- A_DECIDER.
 */
public final class ClausesLibres {

    public static final String EMPLACEMENT = "A_LA_SUITE_DES_RESOLUTIONS";
    public static final String LIBELLE_EMPLACEMENT = "À la suite des résolutions";
    /** A_DECIDER (CDC § 18-6) : emplacement des clauses particulieres des statuts. */
    public static final String A_DECIDER_STATUTS =
            "Les statuts n'ont pas encore d'emplacement pour les clauses particulières : le cabinet doit "
                    + "l'ajouter aux modèles (cahier des charges, § 18-6).";

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
            return boucle.contains("$RESOLUTION_INTITULE") && boucle.matches("(?s).*\\$RESOLUTION_TEXTE(?![A-Z_]).*");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Gabarit illisible", e);
        }
    }

    /**
     * Ajoute chaque clause a la suite des resolutions du mapper. La numerotation suit
     * celle du mapper (« TROISIEME RESOLUTION » -> « QUATRIEME RESOLUTION », ou 3 -> 4).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> inserer(Map<String, Object> variables, List<Map<String, Object>> clauses) {
        Map<String, Object> out = new LinkedHashMap<>(variables);
        List<Map<String, Object>> resolutions = new ArrayList<>();
        if (variables.get("RESOLUTIONS") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) resolutions.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        }
        String modele = resolutions.isEmpty() ? null
                : String.valueOf(resolutions.get(resolutions.size() - 1).getOrDefault("RESOLUTION_NUMERO", ""));
        for (Map<String, Object> c : clauses) {
            int rang = resolutions.size() + 1;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("RESOLUTION_NUMERO", numero(modele, rang));
            r.put("RESOLUTION_INTITULE", c.get("titre"));
            r.put("RESOLUTION_TEXTE", c.get("texte"));
            r.put("RESOLUTION_RESULTAT", c.get("resultat"));
            r.put("RESOLUTION_VOIX_POUR", c.get("voixPour"));
            r.put("RESOLUTION_VOIX_CONTRE", c.get("voixContre"));
            r.put("RESOLUTION_ABSTENTIONS", c.get("abstentions"));
            resolutions.add(r);
        }
        out.put("RESOLUTIONS", resolutions);
        return out;
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
