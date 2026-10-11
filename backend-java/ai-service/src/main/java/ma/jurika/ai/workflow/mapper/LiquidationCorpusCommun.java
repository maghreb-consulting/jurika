package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.format.FrenchNumberToLetters;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lot L4d (E11d) : outils communs aux modeles du corpus de la dissolution et de la
 * liquidation (PV de dissolution anticipee, PV de cloture, rapport du liquidateur) :
 * mentions de l'acte, seance, identite de l'associe unique, conversions.
 *
 * <p>Regle : une donnee absente reste absente (jamais de valeur de repli) ; le controle de
 * completude la nomme a la generation.
 */
final class LiquidationCorpusCommun {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final Pattern HEURE = Pattern.compile("^\\s*(\\d{1,2})\\s*(?:[:hH]\\s*(\\d{2})?)?\\s*(?:heures?)?\\s*$");

    private LiquidationCorpusCommun() {
    }

    static boolean estAu(String templateCode) {
        return templateCode != null && templateCode.toUpperCase(Locale.ROOT).endsWith("_AU");
    }

    /**
     * Seance : date du proces-verbal (date de la seance), heures en toutes lettres d'usage
     * ("10 heures 30"), premiere assemblee (seconde convocation), valeur nominale, quorum et
     * majorite (SARL). Le quorum pilote le PV de carence : saisi, jamais suppose.
     */
    static void seanceCorpus(String templateCode, Map<String, Object> v, Map<String, Object> safe, boolean au) {
        Map<String, Object> seance = asMap(safe.get("seance"));
        LocalDate date = toDate(first0(seance.get("date"), seance.get("assembleeDate")));
        if (date != null) v.put("DATE_SIGNATURE", date.format(DATE_FR));
        putOuRetire(v, "ASSEMBLEE_HEURE", heure(seance.get("heure")));
        putOuRetire(v, "ASSEMBLEE_HEURE_CLOTURE", heure(seance.get("heureCloture")));
        if ("deuxi\u00e8me".equals(v.get("CONVOCATION_RANG"))) putSi(v, "ASSEMBLEE_DATE_PREMIERE", str(v.get("DATE_AG_PREMIERE")));
        putSi(v, "VALEUR_NOMINALE_PART", valeurNominale(asMap(safe.get("societe"))));
        if (!au) {
            v.put("QUORUM_ATTEINT", SaisieExigee.ouiNon(templateCode,
                    first0(safe.get("quorumAtteint"), seance.get("quorumAtteint")), "QUORUM_ATTEINT",
                    "Quorum atteint (oui ou non)"));
            putOuRetire(v, "RESOLUTION_RESULTAT", VoteSaisi.de(safe).majorite);
            accordPresence(v, safe);
        }
    }

    /**
     * Presence accordee : "Mme X, ..., presente" ; une personne morale s'accorde avec
     * "personne morale". Le noyau de seance ecrit "present" / "represente" pour tous.
     */
    static void accordPresence(Map<String, Object> v, Map<String, Object> safe) {
        List<Map<String, Object>> lignes = asListOfMaps(v.get("ASSOCIES"));
        List<Map<String, Object>> associes = new ArrayList<>();
        for (Map<String, Object> a : asListOfMaps(safe.get("associes"))) if (!a.isEmpty()) associes.add(a);
        if (lignes.size() != associes.size()) return;
        for (int i = 0; i < lignes.size(); i++) {
            Map<String, Object> a = associes.get(i);
            String c = civilite(a.get("civilite"));
            boolean feminin = "MORALE".equalsIgnoreCase(str(a.get("typePersonne"))) || c.equals("Mme") || c.equals("Mlle");
            String p = str(lignes.get(i).get("ASSOCIE_PRESENCE"));
            if (feminin && (p.equals("pr\u00e9sent") || p.equals("repr\u00e9sent\u00e9"))) {
                lignes.get(i).put("ASSOCIE_PRESENCE", p + "e");
            }
        }
    }

    /**
     * SARL AU : les modeles de la dissolution impriment {@code $ASSOCIE_UNIQUE} comme l'IDENTITE
     * de l'associe unique ("$ASSOCIE_UNIQUE, $ASSOCIE_TYPE, proprietaire de la totalite des
     * parts") et ne le testent jamais : le dictionnaire de la famille Dissolution et
     * liquidation le definit "Nom de l'associe unique". Le drapeau "oui" imprimerait "oui,
     * personne physique, ..." (ecart du dictionnaire unique, rapporte).
     */
    static void identiteAssocieUnique(Map<String, Object> v, Map<String, Object> safe, boolean au) {
        if (!au) return;
        List<Map<String, Object>> associes = asListOfMaps(safe.get("associes"));
        Map<String, Object> a = associes.isEmpty() ? asMap(safe.get("associeUnique")) : associes.get(0);
        String label = "MORALE".equalsIgnoreCase(str(a.get("typePersonne")))
                ? str(a.get("denomination")) : labelPhysique(a);
        putOuRetire(v, "ASSOCIE_UNIQUE", label);
    }

    /** Mentions de l'acte : reference du dossier, folio du registre, lieu d'enregistrement, originaux. */
    static void mentionsActe(Map<String, Object> v, Map<String, Object> safe, Map<String, Object> mentions) {
        putSi(v, "DOSSIER_REFERENCE", str(mentions.get("dossierReference")));
        putSi(v, "REGISTRE_FOLIO", str(mentions.get("registreFolio")));
        putSi(v, "ENREGISTREMENT_LIEU", str(mentions.get("enregistrementLieu")));
        putSi(v, "NOMBRE_ORIGINAUX", str(first0(mentions.get("nombreOriginaux"),
                asMap(safe.get("signature")).get("nombreOriginaux"))));
    }

    /** Valeur nominale d'une part : fiche, sinon capital / nombre de parts quand la division est exacte. */
    static String valeurNominale(Map<String, Object> societe) {
        Long vn = toLong(societe.get("valeurNominalePart"));
        if (vn != null) return formatAmount(vn);
        Long capital = toLong(first0(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        Long parts = toLong(societe.get("nombreParts"));
        if (capital == null || parts == null || parts <= 0 || capital % parts != 0) return null;
        return formatAmount(capital / parts);
    }

    /** "10:00" / "10H00" / "10" : "10 heures" ; "10:30" : "10 heures 30". Illisible : tel quel. */
    static String heure(Object raw) {
        String s = str(raw).trim();
        if (s.isEmpty()) return null;
        Matcher m = HEURE.matcher(s);
        if (!m.matches()) return s;
        int h = Integer.parseInt(m.group(1));
        String min = m.group(2);
        String base = h + (h <= 1 ? " heure" : " heures");
        return min == null || min.equals("00") ? base : base + " " + min;
    }

    static String labelPhysique(Map<String, Object> p) {
        StringBuilder sb = new StringBuilder();
        for (String s : new String[]{civilite(p.get("civilite")), str(p.get("prenom")), str(p.get("nom"))}) {
            if (s == null || s.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(s.trim());
        }
        return sb.toString();
    }

    /** "A", "A et B", "A, B et C". */
    static String joinEt(List<String> noms) {
        if (noms.isEmpty()) return null;
        if (noms.size() == 1) return noms.get(0);
        return String.join(", ", noms.subList(0, noms.size() - 1)) + " et " + noms.get(noms.size() - 1);
    }

    static String civilite(Object raw) {
        String s = norm(raw);
        if (s.startsWith("mme") || s.startsWith("madame")) return "Mme";
        if (s.startsWith("mlle") || s.startsWith("mademoiselle")) return "Mlle";
        if (s.startsWith("m")) return "M.";
        return str(raw);
    }

    // ------------------------------------------------------------------ conversions

    static String montant(Object raw) {
        Long n = toLong(raw);
        return n == null ? null : formatAmount(n);
    }

    static String lettres(long n) {
        return FrenchNumberToLetters.numberToLetters(n);
    }

    static String formatAmount(long n) {
        return String.format(Locale.FRANCE, "%,d", n);
    }

    static String dateOuNull(Object o) {
        LocalDate d = toDate(o);
        if (d != null) return d.format(DATE_FR);
        String s = str(o);
        return s.isBlank() ? null : s;
    }

    static String dateFr(LocalDate d) {
        return d == null ? null : d.format(DATE_FR);
    }

    static LocalDate toDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate ld) return ld;
        try {
            return LocalDate.parse(String.valueOf(o).trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(o).trim().replaceAll("[\\s\\u00A0\\u202F_]", "").replace(',', '.');
            if (s.isEmpty()) return null;
            return new java.math.BigDecimal(s).longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            return null;
        }
    }

    static void putSi(Map<String, Object> v, String key, String value) {
        if (value != null && !value.isBlank()) v.put(key, value);
    }

    /** Pose la valeur, ou retire la cle (une valeur vide du noyau de seance reste absente). */
    static void putOuRetire(Map<String, Object> v, String key, String value) {
        if (value != null && !value.isBlank()) v.put(key, value);
        else v.remove(key);
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static String norm(Object raw) {
        if (raw == null) return "";
        String n = java.text.Normalizer.normalize(String.valueOf(raw), java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").toLowerCase(Locale.ROOT).trim();
    }

    static Object first0(Object... vals) {
        for (Object o : vals) if (o != null && !(o instanceof String s && s.isBlank())) return o;
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    static List<Object> asList(Object o) {
        List<Object> out = new ArrayList<>();
        if (o instanceof List<?> l) out.addAll(l);
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> asListOfMaps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object e : l) if (e instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }
}
