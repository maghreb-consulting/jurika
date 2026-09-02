package ma.jurika.ai.workflow.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builder des variables de l'<b>avis de clôture de liquidation</b> (annonce légale) —
 * lot Liquidation 4 étapes (2026-08-13).
 *
 * <p>Modèles {@code ANNONCE_LEGALE_LIQUIDATION_SARL} / {@code _SARL_AU} : <b>second</b> des deux
 * avis du cycle (le premier étant l'avis de dissolution, cf.
 * {@link DissolutionAnnonceVarsBuilder}). Avis à <b>condition unique</b>
 * ({@code ◇ SI $RESULTAT_LIQUIDATION_TYPE = « boni » / ◇ SINON}) et sans boucle : seuls le
 * chapeau (« les associés … ont décidé » vs « l'associé unique … a décidé ») et l'attribution
 * du boni diffèrent entre les deux formes, ce qui est porté par le modèle lui-même. Les
 * variables sont donc <b>identiques</b> pour les deux codes.
 *
 * <p>Réutilise le noyau séance {@link SeancePvVarsBuilder} pour l'identité société
 * (DENOMINATION, CAPITAL_CHIFFRES, SIEGE_SOCIAL, VILLE_GREFFE, RC_NUMERO) — pré-remplie
 * depuis la BD par {@code SocieteIdentityEnricher} côté contrôleur, jamais re-saisie.
 *
 * <p>Variables propres à l'avis :
 * <ul>
 *   <li>{@code $DATE_CLOTURE_LIQUIDATION} — date de l'AGE de clôture (ou de la décision de
 *       l'associé unique) saisie à l'étape 1 du workflow ;</li>
 *   <li>{@code $LIQUIDATEUR_CIVILITE / _PRENOM / _NOM} — liquidateur <b>nommé à la
 *       dissolution</b> et relu depuis la BD (jamais re-saisi en liquidation) ;</li>
 *   <li>{@code $RESULTAT_LIQUIDATION_TYPE} (« boni » / « mali ») +
 *       {@code $BONI_LIQUIDATION_CHIFFRES} / {@code $MALI_LIQUIDATION_CHIFFRES} — <b>dérivés
 *       des comptes finaux</b> (total actif − total passif), jamais saisis séparément.</li>
 * </ul>
 *
 * <p><b>{@code $DATE_DEPOT_LEGAL} / {@code $DEPOT_LEGAL_NUMERO}</b> : attribués par le greffe
 * <b>APRÈS</b> le dépôt, donc inconnus à la génération. Rendus <b>vides</b> si non fournis
 * (aucun marqueur résiduel) — même contrat que les annonces de modification et de dissolution.
 *
 * <p>Le calcul boni/mali est aligné sur {@link DissolutionLiquidationMapper#rapportVars(Map)}
 * (mêmes variables, mêmes clés de payload) pour que l'avis et le rapport de liquidation
 * affichent toujours le <b>même</b> résultat : {@code cloture.resultatType} / {@code resultatSens}
 * s'il est fourni, sinon le sens est déduit du signe de {@code actifRealise − passifRegle}.
 */
public final class LiquidationAnnonceVarsBuilder {

    /**
     * Marqueur neutre des données attribuées par le greffe APRÈS le dépôt.
     * Même valeur que {@code CreationDirecteurVarsBuilder.POST_IMMAT} : un avis
     * dit ce qui reste à compléter plutôt que de laisser un blanc dans la phrase.
     */
    private static final String POST_IMMAT = "[à compléter après immatriculation]";

    static final String TPL_SARL = "ANNONCE_LEGALE_LIQUIDATION_SARL";
    static final String TPL_SARL_AU = "ANNONCE_LEGALE_LIQUIDATION_SARL_AU";

    /** Libellés EXACTS attendus par la condition {@code ◇ SI … = « boni »} du modèle. */
    static final String RESULTAT_BONI = "boni";
    static final String RESULTAT_MALI = "mali";

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private LiquidationAnnonceVarsBuilder() {}

    static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // 1) Identité société (pré-remplie BD par l'appelant) via le noyau séance.
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> cloture = asMap(safe.get("cloture"));
        Map<String, Object> liquidateur = asMap(safe.get("liquidateur"));
        Map<String, Object> seance = asMap(safe.get("seance"));

        // 2) Date de clôture = date de l'AGE de clôture / de la décision de l'associé unique.
        put(v, "DATE_CLOTURE_LIQUIDATION", dateStr(first0(
                cloture.get("dateClotureLiquidation"), cloture.get("dateCloture"),
                safe.get("dateClotureLiquidation"), safe.get("dateCloture"),
                seance.get("date"))));

        // 3) Liquidateur (personne physique) — nommé à la dissolution, relu en BD.
        put(v, "LIQUIDATEUR_CIVILITE", normCivilite(liquidateur.get("civilite")));
        put(v, "LIQUIDATEUR_PRENOM", str(liquidateur.get("prenom")));
        put(v, "LIQUIDATEUR_NOM", str(liquidateur.get("nom")));

        // 4) Résultat de liquidation : dérivé des comptes finaux (jamais re-saisi).
        String type = resolveResultatType(cloture);
        put(v, "RESULTAT_LIQUIDATION_TYPE", type);
        Long montant = absLong(first0(cloture.get("resultatMontant"), cloture.get("boniMali")));
        Long boni = firstLong(cloture.get("boniMontant"),
                RESULTAT_BONI.equals(type) ? montant : null);
        Long mali = firstLong(cloture.get("maliMontant"),
                RESULTAT_MALI.equals(type) ? montant : null);
        // Repli : si le montant n'est pas fourni, il se déduit des comptes finaux.
        if (boni == null && RESULTAT_BONI.equals(type)) boni = absLong(soldeComptes(cloture));
        if (mali == null && RESULTAT_MALI.equals(type)) mali = absLong(soldeComptes(cloture));
        put(v, "BONI_LIQUIDATION_CHIFFRES", boni == null ? "" : formatAmount(boni));
        put(v, "MALI_LIQUIDATION_CHIFFRES", mali == null ? "" : formatAmount(mali));

        // 5) Dépôt légal : attribué APRÈS dépôt → vide si non fourni (aucun marqueur résiduel).
        Map<String, Object> depot = asMap(safe.get("depotLegal"));
        // Grammaire d'assemblage (2026-08-17) — le greffe n'attribue ces deux valeurs
        // qu'APRÈS le dépôt : au moment de rédiger l'avis, elles sont normalement
        // inconnues. On les rendait vides, d'où « … le  sous le numéro  RC N° 123456 »
        // — une phrase trouée que rien ne signalait. On reprend le marqueur déjà
        // employé par la CRÉATION (CreationDirecteurVarsBuilder.POST_IMMAT) : l'avis
        // dit explicitement ce qui reste à compléter au lieu de laisser un blanc.
        String dateDepotLegal = dateStr(first0(depot.get("date"), safe.get("dateDepotLegal")));
        String numeroDepotLegal = str(first0(depot.get("numero"), safe.get("depotLegalNumero")));
        put(v, "DATE_DEPOT_LEGAL",
                dateDepotLegal == null || dateDepotLegal.isBlank() ? POST_IMMAT : dateDepotLegal);
        put(v, "DEPOT_LEGAL_NUMERO",
                numeroDepotLegal == null || numeroDepotLegal.isBlank() ? POST_IMMAT : numeroDepotLegal);

        return v;
    }

    // ==================================================================
    // Résultat de liquidation (boni / mali)
    // ==================================================================

    /**
     * Sens du résultat : le libellé explicite s'il est fourni, sinon le <b>signe du solde</b>
     * des comptes finaux ({@code actifRealise − passifRegle}). Un solde nul ou positif est un
     * boni (cohérent avec {@code DissolutionLiquidationMapper.normBoniMali}, dont le défaut
     * est « boni »).
     */
    private static String resolveResultatType(Map<String, Object> cloture) {
        Object explicite = first0(cloture.get("resultatType"), cloture.get("resultatSens"),
                cloture.get("sens"));
        if (explicite != null) return normBoniMali(explicite);
        Long solde = soldeComptes(cloture);
        if (solde != null) return solde < 0 ? RESULTAT_MALI : RESULTAT_BONI;
        return RESULTAT_BONI;
    }

    /** Solde des comptes finaux : {@code actifRealise − passifRegle} ({@code null} si absent). */
    private static Long soldeComptes(Map<String, Object> cloture) {
        Long actif = toLong(first0(cloture.get("actifRealise"), cloture.get("actifRealiseChiffres"),
                cloture.get("totalActif")));
        Long passif = toLong(first0(cloture.get("passifRegle"), cloture.get("passifRegleChiffres"),
                cloture.get("totalPassif")));
        if (actif == null || passif == null) return null;
        return actif - passif;
    }

    private static String normBoniMali(Object raw) {
        String s = norm(raw);
        if (s.startsWith("mali") || s.startsWith("perte") || s.startsWith("defic")
                || s.startsWith("neg")) return RESULTAT_MALI;
        return RESULTAT_BONI;
    }

    // ==================================================================
    // Helpers (coercion / format) — alignés sur DissolutionAnnonceVarsBuilder
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

    /** Montant toujours publié en valeur absolue (le sens est porté par le type). */
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
