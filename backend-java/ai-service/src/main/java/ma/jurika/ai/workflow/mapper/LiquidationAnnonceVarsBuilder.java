package ma.jurika.ai.workflow.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builder des variables de l'<b>avis de cl\u00f4ture de liquidation</b> (annonce l\u00e9gale) \u2014
 * lot Liquidation 4 \u00e9tapes (2026-08-13).
 *
 * <p>Mod\u00e8les {@code ANNONCE_LEGALE_LIQUIDATION_SARL} / {@code _SARL_AU} : <b>second</b> des deux
 * avis du cycle (le premier \u00e9tant l'avis de dissolution, cf.
 * {@link DissolutionAnnonceVarsBuilder}). Avis \u00e0 <b>condition unique</b>
 * ({@code \u25c7 SI $RESULTAT_LIQUIDATION_TYPE = \u00ab boni \u00bb / \u25c7 SINON}) et sans boucle : seuls le
 * chapeau (\u00ab les associ\u00e9s \u2026 ont d\u00e9cid\u00e9 \u00bb vs \u00ab l'associ\u00e9 unique \u2026 a d\u00e9cid\u00e9 \u00bb) et l'attribution
 * du boni diff\u00e8rent entre les deux formes, ce qui est port\u00e9 par le mod\u00e8le lui-m\u00eame. Les
 * variables sont donc <b>identiques</b> pour les deux codes.
 *
 * <p>R\u00e9utilise le noyau s\u00e9ance {@link SeancePvVarsBuilder} pour l'identit\u00e9 soci\u00e9t\u00e9
 * (DENOMINATION, CAPITAL_CHIFFRES, SIEGE_SOCIAL, VILLE_GREFFE, RC_NUMERO) \u2014 pr\u00e9-remplie
 * depuis la BD par {@code SocieteIdentityEnricher} c\u00f4t\u00e9 contr\u00f4leur, jamais re-saisie.
 *
 * <p>Variables propres \u00e0 l'avis :
 * <ul>
 *   <li>{@code $DATE_CLOTURE_LIQUIDATION} \u2014 date de l'AGE de cl\u00f4ture (ou de la d\u00e9cision de
 *       l'associ\u00e9 unique) saisie \u00e0 l'\u00e9tape 1 du workflow ;</li>
 *   <li>{@code $LIQUIDATEUR_CIVILITE / _PRENOM / _NOM} \u2014 liquidateur <b>nomm\u00e9 \u00e0 la
 *       dissolution</b> et relu depuis la BD (jamais re-saisi en liquidation) ;</li>
 *   <li>{@code $RESULTAT_LIQUIDATION_TYPE} (\u00ab boni \u00bb / \u00ab mali \u00bb) +
 *       {@code $BONI_LIQUIDATION_CHIFFRES} / {@code $MALI_LIQUIDATION_CHIFFRES} \u2014 <b>d\u00e9riv\u00e9s
 *       des comptes finaux</b> (total actif \u2212 total passif), jamais saisis s\u00e9par\u00e9ment.</li>
 * </ul>
 *
 * <p><b>{@code $DATE_DEPOT_LEGAL} / {@code $DEPOT_LEGAL_NUMERO}</b> : attribu\u00e9s par le greffe
 * <b>APR\u00c8S</b> le d\u00e9p\u00f4t, donc inconnus \u00e0 la g\u00e9n\u00e9ration. Rendus <b>vides</b> si non fournis
 * (aucun marqueur r\u00e9siduel) \u2014 m\u00eame contrat que les annonces de modification et de dissolution.
 *
 * <p>Sens du resultat : {@code cloture.resultatType} / {@code resultatSens} s'il est fourni et
 * lisible, sinon le signe de {@code actifRealise - passifRegle}. Lot L4d (E11d) : sans l'un ni
 * l'autre, la generation est refusee et la donnee nommee (avant : "boni" suppose).
 */
public final class LiquidationAnnonceVarsBuilder {


    static final String TPL_SARL = "ANNONCE_LEGALE_LIQUIDATION_SARL";
    static final String TPL_SARL_AU = "ANNONCE_LEGALE_LIQUIDATION_SARL_AU";

    /** Libell\u00e9s EXACTS attendus par la condition {@code \u25c7 SI \u2026 = \u00ab boni \u00bb} du mod\u00e8le. */
    static final String RESULTAT_BONI = "boni";
    static final String RESULTAT_MALI = "mali";

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private LiquidationAnnonceVarsBuilder() {}

    static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // 1) Identit\u00e9 soci\u00e9t\u00e9 (pr\u00e9-remplie BD par l'appelant) via le noyau s\u00e9ance.
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> cloture = asMap(safe.get("cloture"));
        Map<String, Object> liquidateur = asMap(safe.get("liquidateur"));
        Map<String, Object> seance = asMap(safe.get("seance"));

        // 2) Date de cl\u00f4ture = date de l'AGE de cl\u00f4ture / de la d\u00e9cision de l'associ\u00e9 unique.
        put(v, "DATE_CLOTURE_LIQUIDATION", dateStr(first0(
                cloture.get("dateClotureLiquidation"), cloture.get("dateCloture"),
                safe.get("dateClotureLiquidation"), safe.get("dateCloture"),
                seance.get("date"))));

        // 3) Liquidateur (personne physique) \u2014 nomm\u00e9 \u00e0 la dissolution, relu en BD.
        put(v, "LIQUIDATEUR_CIVILITE", normCivilite(liquidateur.get("civilite")));
        put(v, "LIQUIDATEUR_PRENOM", str(liquidateur.get("prenom")));
        put(v, "LIQUIDATEUR_NOM", str(liquidateur.get("nom")));

        // L4b (E7, D5) : variables du modele du corpus (avis de cloture).
        corpus(v, safe, cloture, liquidateur, seance);

        // 4) R\u00e9sultat de liquidation : d\u00e9riv\u00e9 des comptes finaux (jamais re-saisi).
        String type = resolveResultatType(templateCode, cloture);
        put(v, "RESULTAT_LIQUIDATION_TYPE", type);
        Long montant = absLong(first0(cloture.get("resultatMontant"), cloture.get("boniMali")));
        Long boni = firstLong(cloture.get("boniMontant"),
                RESULTAT_BONI.equals(type) ? montant : null);
        Long mali = firstLong(cloture.get("maliMontant"),
                RESULTAT_MALI.equals(type) ? montant : null);
        // Repli : si le montant n'est pas fourni, il se d\u00e9duit des comptes finaux.
        if (boni == null && RESULTAT_BONI.equals(type)) boni = absLong(soldeComptes(cloture));
        if (mali == null && RESULTAT_MALI.equals(type)) mali = absLong(soldeComptes(cloture));
        put(v, "BONI_LIQUIDATION_CHIFFRES", boni == null ? "" : formatAmount(boni));
        put(v, "MALI_LIQUIDATION_CHIFFRES", mali == null ? "" : formatAmount(mali));

        // 5) D\u00e9p\u00f4t l\u00e9gal : attribu\u00e9 APR\u00c8S d\u00e9p\u00f4t \u2192 vide si non fourni (aucun marqueur r\u00e9siduel).
        Map<String, Object> depot = asMap(safe.get("depotLegal"));
        // Grammaire d'assemblage (2026-08-17) \u2014 le greffe n'attribue ces deux valeurs
        // qu'APR\u00c8S le d\u00e9p\u00f4t : au moment de r\u00e9diger l'avis, elles sont normalement
        // inconnues. On les rendait vides, d'o\u00f9 \u00ab \u2026 le  sous le num\u00e9ro  RC N\u00b0 123456 \u00bb
        // \u2014 une phrase trou\u00e9e que rien ne signalait. On reprend le marqueur d\u00e9j\u00e0
        // Lot L3 : plus de bouche-trou "[a completer apres immatriculation]". Date et
        // numero du depot legal sont des donnees EXTERNES (greffe) : absentes, l'acte
        // porte le marqueur "A OBTENIR" et la donnee est reclamee (regle des variables).
        String dateDepotLegal = dateStr(first0(depot.get("date"), safe.get("dateDepotLegal")));
        String numeroDepotLegal = str(first0(depot.get("numero"), safe.get("depotLegalNumero")));
        put(v, "DATE_DEPOT_LEGAL",
                dateDepotLegal);
        put(v, "DEPOT_LEGAL_NUMERO",
                numeroDepotLegal);

        return v;
    }

    /** Sens du quitus testes par le modele ; "sans_quitus" prend la branche "decharge seule". */
    static final java.util.Set<String> QUITUS = java.util.Set.of("accorde", "sous_reserve", "sans_quitus");

    /**
     * Lot L4b (E7, D5) : l'avis du corpus sert trois objets ; le parcours de liquidation
     * publie la CLOTURE. Nature de l'acte, date d'effet de la dissolution, date du compte
     * definitif, quitus et siege de la liquidation sont saisis ; rien n'est suppose.
     */
    static void corpus(Map<String, Object> v, Map<String, Object> safe, Map<String, Object> cloture,
                       Map<String, Object> liquidateur, Map<String, Object> seance) {
        v.put("ANNONCE_LIQUIDATION_OBJET", "cloture");
        Object date = first0(cloture.get("dateClotureLiquidation"), cloture.get("dateCloture"),
                safe.get("dateClotureLiquidation"), safe.get("dateCloture"), seance.get("date"));
        putSi(v, "LIQUIDATION_CLOTURE_DATE", date == null ? null : dateStr(date));
        putSi(v, "LIQUIDATION_CLOTURE_ACTE_NATURE", str(cloture.get("acteNature")));
        putSi(v, "DISSOLUTION_DATE_EFFET", dateOuNull(cloture.get("dissolutionDateEffet")));
        putSi(v, "LIQUIDATION_COMPTE_DEFINITIF_DATE", dateOuNull(cloture.get("compteDefinitifDate")));
        String quitus = str(cloture.get("quitus")).trim();
        if (QUITUS.contains(quitus)) v.put("LIQUIDATION_QUITUS_SENS", quitus);
        putSi(v, "LIQUIDATION_SIEGE_ADRESSE", str(first0(cloture.get("siegeLiquidation"), safe.get("liquidationSiege"),
                safe.get("siegeLiquidation"))));
        Map<String, Object> depot = asMap(safe.get("depotLegal"));
        putSi(v, "LIQUIDATION_CLOTURE_DEPOT_GREFFE_DATE", dateOuNull(depot.get("date")));
        DissolutionAnnonceVarsBuilder.liquidateurCorpus(v, liquidateur);
        DissolutionAnnonceVarsBuilder.tribunal(v, safe);
    }

    private static String dateOuNull(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        return dateStr(o);
    }

    private static void putSi(Map<String, Object> v, String key, String value) {
        if (value != null && !value.isBlank()) v.put(key, value);
    }

    // ==================================================================
    // R\u00e9sultat de liquidation (boni / mali)
    // ==================================================================

    /**
     * Sens du r\u00e9sultat : le libell\u00e9 explicite s'il est fourni et lisible, sinon le <b>signe
     * du solde</b> des comptes finaux ({@code actifRealise - passifRegle}, un solde nul ou positif
     * etant un boni). Lot L4d (E11d) : ni l'un ni l'autre, la generation est refusee et la
     * donnee nommee ; le "boni" n'est plus suppose.
     */
    private static String resolveResultatType(String templateCode, Map<String, Object> cloture) {
        Object explicite = first0(cloture.get("resultatType"), cloture.get("resultatSens"),
                cloture.get("sens"));
        String sens = explicite == null ? null : normBoniMali(explicite);
        if (sens != null) return sens;
        Long solde = soldeComptes(cloture);
        if (solde != null) return solde < 0 ? RESULTAT_MALI : RESULTAT_BONI;
        throw SaisieExigee.refus(templateCode, "RESULTAT_LIQUIDATION_TYPE",
                "R\u00e9sultat de la liquidation (boni ou mali) : total de l'actif r\u00e9alis\u00e9 et du passif r\u00e9gl\u00e9");
    }

    /** Solde des comptes finaux : {@code actifRealise \u2212 passifRegle} ({@code null} si absent). */
    private static Long soldeComptes(Map<String, Object> cloture) {
        Long actif = toLong(first0(cloture.get("actifRealise"), cloture.get("actifRealiseChiffres"),
                cloture.get("totalActif")));
        Long passif = toLong(first0(cloture.get("passifRegle"), cloture.get("passifRegleChiffres"),
                cloture.get("totalPassif")));
        if (actif == null || passif == null) return null;
        return actif - passif;
    }

    /** "boni" / "mali" ; un libelle illisible ne vaut rien (null), jamais "boni". */
    private static String normBoniMali(Object raw) {
        String s = norm(raw);
        if (s.startsWith("mali") || s.startsWith("perte") || s.startsWith("defic")
                || s.startsWith("neg")) return RESULTAT_MALI;
        if (s.startsWith("boni") || s.startsWith("benef") || s.startsWith("posit")) return RESULTAT_BONI;
        return null;
    }

    // ==================================================================
    // Helpers (coercion / format) \u2014 align\u00e9s sur DissolutionAnnonceVarsBuilder
    // ==================================================================

    private static void put(Map<String, Object> v, String key, String value) {
        v.put(key, value == null ? "" : value);
    }

    private static String normCivilite(Object raw) {
        String s = norm(raw);
        if (s.startsWith("mme") || s.startsWith("madame")) return "Mme";
        if (s.startsWith("mlle") || s.startsWith("mademoiselle")) return "Mlle";
        if (s.startsWith("m")) return "M.";
        return raw == null ? "" : String.valueOf(raw);
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

    private static Object first0(Object... vals) {
        for (Object o : vals) if (o != null && !(o instanceof String s && s.isBlank())) return o;
        return null;
    }

    private static Long firstLong(Object... vals) {
        for (Object o : vals) {
            Long n = absLong(o);
            if (n != null) return n;
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** Montant toujours publi\u00e9 en valeur absolue (le sens est port\u00e9 par le type). */
    private static Long absLong(Object o) {
        Long n = toLong(o);
        return n == null ? null : Math.abs(n);
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(o).trim().replaceAll("[\\s\\u00A0\\u202F_]", "");
            if (s.isEmpty()) return null;
            return new java.math.BigDecimal(s).longValue();
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
}
