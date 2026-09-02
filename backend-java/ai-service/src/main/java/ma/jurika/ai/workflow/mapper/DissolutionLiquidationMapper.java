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

/**
 * Noyau de mapping <b>partagé</b> des workflows Dissolution / Liquidation (Phase C).
 *
 * <p>Réutilise le <b>noyau séance d'AG</b> ({@link SeancePvVarsBuilder}) pour le procès-verbal
 * unifié {@code PV_DISSOLUTION_LIQUIDATION_SARL(/_AU)} qui couvre les trois étapes du cycle via
 * la variable {@code $PV_ETAPE} (« dissolution » / « cours de liquidation » / « clôture »), et
 * produit à part les variables du {@code RAPPORT_LIQUIDATION_DIRECTEUR} (rapport final du
 * liquidateur — document distinct, ni séance ni PV).
 *
 * <p>Ce n'est pas un bean : c'est le cœur commun appelé par les deux mappers minces
 * {@link DissolutionMapper} (workflow {@code DISSOLUTION}, étape « dissolution ») et
 * {@link LiquidationMapper} (workflow {@code LIQUIDATION}, étape « clôture » + rapport). Chacun
 * conserve son code de workflow ; {@code $PV_ETAPE} est déterminé par l'étape (jamais saisi
 * librement) — voir {@code defaultEtape}. Aucune lecture DB : tout provient du payload
 * (identité société + associés pré-remplis depuis le dossier par l'appelant).
 *
 * <p>Montants en lettres via {@link FrenchNumberToLetters}. Toutes les variables des modèles
 * sont systématiquement produites (les branches conditionnelles inutilisées sont retirées par
 * le moteur), de sorte qu'aucun marqueur {@code $…} résiduel ne subsiste.
 */
public final class DissolutionLiquidationMapper {

    static final String TPL_PV_SARL = "PV_DISSOLUTION_LIQUIDATION_SARL";
    static final String TPL_PV_SARL_AU = "PV_DISSOLUTION_LIQUIDATION_SARL_AU";
    static final String TPL_RAPPORT = "RAPPORT_LIQUIDATION_DIRECTEUR";

    /** Étapes du cycle — libellés EXACTS pilotant {@code $PV_ETAPE}. */
    static final String ETAPE_DISSOLUTION = "dissolution";
    static final String ETAPE_COURS = "cours de liquidation";
    static final String ETAPE_CLOTURE = "clôture";

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private DissolutionLiquidationMapper() {}

    // ------------------------------------------------------------------
    // PV unifié dissolution / liquidation (réutilise la séance)
    // ------------------------------------------------------------------

    /**
     * Variables du PV unifié : noyau séance + variables propres à l'opération.
     *
     * @param templateCode {@code PV_DISSOLUTION_LIQUIDATION_SARL(/_AU)}
     * @param payload      contrat de données (societe, seance, convocation, associes,
     *                     resolutions, ordreDuJour + blocs dissolution/liquidateur/cloture)
     * @param defaultEtape étape par défaut si {@code pvEtape} absent du payload (fixée par le
     *                     workflow appelant : « dissolution » ou « clôture »)
     */
    static Map<String, Object> pvVars(String templateCode, Map<String, Object> payload,
                                      String defaultEtape) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // 1) Noyau séance d'AG (réutilisable, double nommage, présence/quorum, résolutions).
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        // 2) Variables propres à la dissolution / liquidation (racine — référencées dans les
        //    conditions par type de résolution : $RESOLUTION_TYPE = « … »).
        Map<String, Object> dissolution = asMap(safe.get("dissolution"));
        Map<String, Object> liquidateur = asMap(safe.get("liquidateur"));
        Map<String, Object> cloture = asMap(safe.get("cloture"));

        // Étape du cycle (déterminée par le workflow, pas saisie librement).
        put(v, "PV_ETAPE", normEtape(first0(safe.get("pvEtape"), defaultEtape)));

        // Dissolution : motif + dates d'effet / d'exercice clos.
        put(v, "DISSOLUTION_MOTIF", normMotif(dissolution.get("motif")));
        put(v, "DATE_EFFET", dateStr(first0(dissolution.get("dateEffet"), dissolution.get("dateDissolution"))));
        put(v, "EXERCICE_CLOS_DATE", dateStr(first0(dissolution.get("exerciceClosDate"),
                cloture.get("exerciceClosDate"))));

        // Liquidateur (personne physique) : identité (label), adresse, rémunération, remplacement.
        put(v, "LIQUIDATEUR_NOM", liquidateurLabel(liquidateur));
        put(v, "LIQUIDATEUR_ADRESSE", str(liquidateur.get("adresse")));
        put(v, "LIQUIDATEUR_REMUNERATION",
                first(str(liquidateur.get("remuneration")), "exercées à titre gratuit"));
        put(v, "LIQUIDATEUR_SORTANT_NOM", str(liquidateur.get("sortantNom")));
        put(v, "LIQUIDATION_SIEGE", first(str(liquidateur.get("siege")),
                str(safe.get("liquidationSiege")), str(asMap(safe.get("societe")).get("siegeSocial"))));
        put(v, "CESSION_ACTIF_OBJET", str(safe.get("cessionActifObjet")));

        // Clôture : résultat (boni / mali) + répartition du boni.
        String sens = normBoniMali(first0(cloture.get("resultatSens"), cloture.get("sens")));
        put(v, "LIQ_RESULTAT_SENS", sens);
        put(v, "LIQ_RESULTAT_MONTANT", amount(cloture.get("resultatMontant")));
        String boniExiste = normOuiNon(cloture.get("boniExiste"),
                "boni".equals(sens) ? "oui" : "non");
        put(v, "BONI_EXISTE", boniExiste);
        put(v, "BONI_MONTANT", amount(first0(cloture.get("boniMontant"), cloture.get("resultatMontant"))));
        put(v, "BONI_PAR_PART", amount(cloture.get("boniParPart")));

        return v;
    }

    // ------------------------------------------------------------------
    // Rapport de liquidation (document distinct — rapport du liquidateur)
    // ------------------------------------------------------------------

    /** Variables du {@code RAPPORT_LIQUIDATION_DIRECTEUR}. */
    static Map<String, Object> rapportVars(Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        Map<String, Object> societe = asMap(safe.get("societe"));
        Map<String, Object> liquidateur = asMap(safe.get("liquidateur"));
        Map<String, Object> dissolution = asMap(safe.get("dissolution"));
        Map<String, Object> cloture = asMap(safe.get("cloture"));
        Map<String, Object> signature = asMap(safe.get("signature"));
        List<Map<String, Object>> associes = asListOfMaps(safe.get("associes"));

        Map<String, Object> v = new LinkedHashMap<>();

        // Identité société (en-tête).
        put(v, "DENOMINATION", str(societe.get("denomination")));
        Long capital = toLong(first0(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        put(v, "CAPITAL_CHIFFRES", capital == null ? "" : formatAmount(capital));
        put(v, "SIEGE_SOCIAL", first(str(societe.get("siegeSocial")), str(societe.get("adresseSiege"))));
        put(v, "RC_NUMERO", first(str(societe.get("rcNumero")), str(societe.get("rc"))));
        put(v, "VILLE_GREFFE", first(str(societe.get("villeGreffe")), str(societe.get("rcVille"))));
        put(v, "ASSOCIE_UNIQUE", resolveAssocieUnique(safe, societe));

        // Liquidateur (identité détaillée + accord de genre).
        put(v, "LIQUIDATEUR_GENRE", normGenre(liquidateur.get("genre")));
        put(v, "LIQUIDATEUR_CIVILITE", normCivilite(liquidateur.get("civilite")));
        put(v, "LIQUIDATEUR_PRENOM", str(liquidateur.get("prenom")));
        put(v, "LIQUIDATEUR_NOM", str(liquidateur.get("nom")));
        put(v, "LIQUIDATEUR_ADRESSE", str(liquidateur.get("adresse")));
        put(v, "LIQUIDATEUR_PIECE_TYPE", first(str(liquidateur.get("pieceType")), "CIN"));
        put(v, "LIQUIDATEUR_PIECE_NUMERO", first(str(liquidateur.get("pieceNumero")),
                str(liquidateur.get("cin"))));

        // Dates dissolution / clôture.
        put(v, "DATE_DISSOLUTION", dateStr(first0(dissolution.get("date"),
                dissolution.get("dateDissolution"))));
        String dateCloture = dateStr(first0(cloture.get("dateClotureLiquidation"),
                cloture.get("dateCloture")));
        put(v, "DATE_CLOTURE_LIQUIDATION", dateCloture);

        // Actif réalisé / passif réglé (chiffres + lettres).
        putAmountPair(v, "ACTIF_REALISE", first0(cloture.get("actifRealise"),
                cloture.get("actifRealiseChiffres")));
        putAmountPair(v, "PASSIF_REGLE", first0(cloture.get("passifRegle"),
                cloture.get("passifRegleChiffres")));

        // Résultat de liquidation : boni OU mali (chiffres + lettres).
        String type = normBoniMali(first0(cloture.get("resultatType"), cloture.get("resultatSens"),
                cloture.get("sens")));
        put(v, "RESULTAT_LIQUIDATION_TYPE", type);
        Long boni = toLong(first0(cloture.get("boniMontant"),
                "boni".equals(type) ? cloture.get("resultatMontant") : null));
        Long mali = toLong(first0(cloture.get("maliMontant"),
                "mali".equals(type) ? cloture.get("resultatMontant") : null));
        put(v, "BONI_LIQUIDATION_CHIFFRES", boni == null ? "" : formatAmount(boni));
        put(v, "BONI_LIQUIDATION_LETTRES", boni == null ? "" : FrenchNumberToLetters.numberToLetters(boni));
        put(v, "MALI_LIQUIDATION_CHIFFRES", mali == null ? "" : formatAmount(mali));
        put(v, "MALI_LIQUIDATION_LETTRES", mali == null ? "" : FrenchNumberToLetters.numberToLetters(mali));

        // Associé unique (nom hors boucle, référencé dans la répartition / signatures AU).
        put(v, "ASSOCIE_NOM", associes.isEmpty() ? "" : associeLabel(associes.get(0)));

        // Boucle ASSOCIES : répartition du boni par associé (SARL pluripersonnelle).
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> a : associes) {
            if (a == null || a.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("ASSOCIE_NOM", associeLabel(a));
            Long parts = toLong(first0(a.get("nombreParts"), a.get("partsChiffres")));
            it.put("ASSOCIE_NOMBRE_PARTS", parts == null ? "" : String.valueOf(parts));
            it.put("ASSOCIE_BONI_CHIFFRES", amount(first0(a.get("boniChiffres"), a.get("boni"))));
            rows.add(it);
        }
        v.put("ASSOCIES", rows);

        // Signatures.
        put(v, "LIEU_SIGNATURE", first(str(signature.get("lieu")), str(safe.get("lieuSignature")),
                str(societe.get("villeGreffe")), str(societe.get("rcVille"))));
        put(v, "DATE_SIGNATURE", dateStr(first0(signature.get("date"), safe.get("dateSignature"))));
        put(v, "NOMBRE_ORIGINAUX", first(str(signature.get("nombreOriginaux")), "quatre"));

        return v;
    }

    // ------------------------------------------------------------------
    // Normalisations (libellés EXACTS des modèles)
    // ------------------------------------------------------------------

    /** Étape du cycle : « dissolution » / « cours de liquidation » / « clôture ». */
    private static String normEtape(Object raw) {
        String s = norm(raw);
        if (s.startsWith("clot") || s.startsWith("cloture") || s.contains("clotur")) return ETAPE_CLOTURE;
        if (s.startsWith("cours") || s.contains("en cours")) return ETAPE_COURS;
        return ETAPE_DISSOLUTION;
    }

    /** Motif de dissolution : « volontaire » / « article 86 » / « arrivée du terme ». */
    private static String normMotif(Object raw) {
        String s = norm(raw);
        if (s.contains("86") || s.contains("article")) return "article 86";
        if (s.contains("terme") || s.contains("arriv")) return "arrivée du terme";
        return "volontaire";
    }

    /** Sens du résultat de liquidation : « boni » / « mali ». */
    private static String normBoniMali(Object raw) {
        String s = norm(raw);
        if (s.startsWith("mali") || s.startsWith("perte") || s.startsWith("defic")
                || s.startsWith("neg")) return "mali";
        return "boni";
    }

    private static String normGenre(Object raw) {
        String s = norm(raw);
        if (s.startsWith("f")) return "féminin";
        return "masculin";
    }

    private static String normOuiNon(Object raw, String def) {
        if (raw == null) return def;
        if (raw instanceof Boolean b) return b ? "oui" : "non";
        String s = norm(raw);
        if (s.isEmpty()) return def;
        if (s.startsWith("o") || s.equals("true") || s.equals("1")) return "oui";
        if (s.startsWith("n") || s.equals("false") || s.equals("0")) return "non";
        return def;
    }

    private static String normCivilite(Object raw) {
        String s = norm(raw);
        if (s.startsWith("mme") || s.startsWith("madame")) return "Mme";
        if (s.startsWith("mlle") || s.startsWith("mademoiselle")) return "Mlle";
        if (s.startsWith("m")) return "M.";
        return raw == null ? "" : String.valueOf(raw);
    }

    /** Résout {@code ASSOCIE_UNIQUE} (« oui »/« non ») depuis le flag ou la forme juridique. */
    private static String resolveAssocieUnique(Map<String, Object> safe, Map<String, Object> societe) {
        Object flag = first0(safe.get("associeUnique"), societe.get("associeUnique"));
        if (flag != null && !(flag instanceof Map)) {
            return normOuiNon(flag, "non");
        }
        String forme = norm(first0(societe.get("formeJuridique"), societe.get("forme"),
                safe.get("formeJuridique")));
        return (forme.contains("au") || forme.contains("unique")) ? "oui" : "non";
    }

    private static String liquidateurLabel(Map<String, Object> l) {
        String direct = str(l.get("nomComplet"));
        if (direct != null && !direct.isBlank()) return direct;
        return join(" ", normCivilite(l.get("civilite")), strOr(l.get("prenom"), ""),
                strOr(l.get("nom"), ""));
    }

    private static String associeLabel(Map<String, Object> a) {
        if ("MORALE".equals(String.valueOf(first(str(a.get("typePersonne")), "PHYSIQUE"))
                .toUpperCase(Locale.ROOT))) {
            return strOr(a.get("denomination"), "");
        }
        return join(" ", normCivilite(a.get("civilite")), strOr(a.get("prenom"), ""),
                strOr(a.get("nom"), ""));
    }

    // ------------------------------------------------------------------
    // Helpers (coercion / format) — alignés sur SeancePvVarsBuilder
    // ------------------------------------------------------------------

    private static void putAmountPair(Map<String, Object> v, String base, Object raw) {
        Long n = toLong(raw);
        v.put(base + "_CHIFFRES", n == null ? "" : formatAmount(n));
        v.put(base + "_LETTRES", n == null ? "" : FrenchNumberToLetters.numberToLetters(n));
    }

    /** Montant en chiffres formaté (ou "" si absent). */
    private static String amount(Object raw) {
        Long n = toLong(raw);
        return n == null ? "" : formatAmount(n);
    }

    private static void put(Map<String, Object> v, String key, String value) {
        v.put(key, value == null ? "" : value);
    }

    private static String formatAmount(long n) {
        return String.format(Locale.FRANCE, "%,d", n);
    }

    private static String dateStr(Object o) {
        LocalDate d = toDate(o);
        if (d != null) return d.format(DATE_FR);
        return o == null ? "" : String.valueOf(o);
    }

    private static String norm(Object raw) {
        if (raw == null) return "";
        String n = java.text.Normalizer.normalize(String.valueOf(raw), java.text.Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p.trim());
        }
        return sb.toString();
    }

    private static String first(String... vals) {
        for (String s : vals) if (s != null && !s.isBlank()) return s;
        return "";
    }

    private static Object first0(Object... vals) {
        for (Object o : vals) if (o != null && !(o instanceof String s && s.isBlank())) return o;
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String strOr(Object o, String fallback) {
        if (o == null) return fallback;
        String s = String.valueOf(o);
        return s.isBlank() ? fallback : s;
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(o).trim().replaceAll("[\\s\\u00A0\\u202F_]", "");
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate toDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate ld) return ld;
        try {
            return LocalDate.parse(String.valueOf(o).trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object e : l) if (e instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }
}
