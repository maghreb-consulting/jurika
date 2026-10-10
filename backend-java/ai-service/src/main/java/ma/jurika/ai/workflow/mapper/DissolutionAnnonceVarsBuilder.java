package ma.jurika.ai.workflow.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builder des variables de l'<b>avis de dissolution anticipée</b> (annonce légale) —
 * lot Dissolution 4 étapes (2026-08-12).
 *
 * <p>Modèles {@code ANNONCE_LEGALE_DISSOLUTION_SARL} / {@code _SARL_AU} : avis <b>linéaire</b>
 * (aucune boucle, aucune condition) — seul le chapeau diffère entre les deux formes
 * (« les associés … ont décidé » vs « l'associé unique … a décidé »), ce qui est porté par le
 * modèle lui-même. Les variables sont donc <b>identiques</b> pour les deux codes.
 *
 * <p>Réutilise le noyau séance {@link SeancePvVarsBuilder} pour l'identité société
 * (DENOMINATION, CAPITAL_CHIFFRES, SIEGE_SOCIAL, VILLE_GREFFE, RC_NUMERO) — pré-remplie
 * depuis la BD par {@code SocieteIdentityEnricher} côté contrôleur, jamais re-saisie.
 *
 * <p>Variables propres à l'avis :
 * <ul>
 *   <li>{@code $DATE_DISSOLUTION} — date de l'AGE (ou de la décision de l'associé unique)
 *       saisie à l'étape 1 du workflow ;</li>
 *   <li>{@code $LIQUIDATEUR_CIVILITE / _PRENOM / _NOM / _ADRESSE} — liquidateur choisi à
 *       l'étape 1 (sélection BD parmi les gérants/associés, ou saisie externe + OCR CIN) ;</li>
 *   <li>{@code $SIEGE_LIQUIDATION} — siège de la liquidation (étape 1).</li>
 * </ul>
 *
 * <p><b>{@code $DATE_DEPOT_LEGAL} / {@code $DEPOT_LEGAL_NUMERO}</b> : attribués par le greffe
 * <b>APRÈS</b> le dépôt, donc inconnus à la génération. Rendus <b>vides</b> si non fournis
 * (aucun marqueur résiduel) — même contrat que l'annonce de modification.
 *
 * <p>Le <b>motif</b> de dissolution n'apparaît pas dans l'avis (il reste au PV) : c'est
 * conforme au modèle directeur.
 */
public final class DissolutionAnnonceVarsBuilder {


    static final String TPL_SARL = "ANNONCE_LEGALE_DISSOLUTION_SARL";
    static final String TPL_SARL_AU = "ANNONCE_LEGALE_DISSOLUTION_SARL_AU";

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private DissolutionAnnonceVarsBuilder() {}

    static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // 1) Identité société (pré-remplie BD par l'appelant) via le noyau séance.
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> societe = asMap(safe.get("societe"));
        Map<String, Object> dissolution = asMap(safe.get("dissolution"));
        Map<String, Object> liquidateur = asMap(safe.get("liquidateur"));
        Map<String, Object> seance = asMap(safe.get("seance"));

        // 2) Date de dissolution = date de l'AGE / de la décision de l'associé unique.
        put(v, "DATE_DISSOLUTION", dateStr(first0(
                dissolution.get("date"), dissolution.get("dateDissolution"),
                dissolution.get("dateEffet"), safe.get("dateDissolution"),
                safe.get("dateAGE"), seance.get("date"))));

        // 3) Liquidateur (personne physique) + siège de la liquidation.
        put(v, "LIQUIDATEUR_CIVILITE", normCivilite(liquidateur.get("civilite")));
        put(v, "LIQUIDATEUR_PRENOM", str(liquidateur.get("prenom")));
        put(v, "LIQUIDATEUR_NOM", str(liquidateur.get("nom")));
        put(v, "LIQUIDATEUR_ADRESSE", str(liquidateur.get("adresse")));
        put(v, "SIEGE_LIQUIDATION", first(
                str(liquidateur.get("siege")), str(safe.get("siegeLiquidation")),
                str(safe.get("liquidationSiege")), str(societe.get("siegeSocial")),
                str(societe.get("adresseSiege"))));

        // 4) Dépôt légal : attribué APRÈS dépôt → vide si non fourni (aucun marqueur résiduel).
        Map<String, Object> depot = asMap(safe.get("depotLegal"));
        // Grammaire d'assemblage (2026-08-17) — le greffe n'attribue ces deux valeurs
        // qu'APRÈS le dépôt : au moment de rédiger l'avis, elles sont normalement
        // inconnues. On les rendait vides, d'où « … le  sous le numéro  RC N° 123456 »
        // — une phrase trouée que rien ne signalait. On reprend le marqueur déjà
        // Lot L3 : plus de bouche-trou « [a completer apres immatriculation] ». Date et
        // numero du depot legal sont des donnees EXTERNES (greffe) : absentes, l'acte
        // porte le marqueur « A OBTENIR » et la donnee est reclamee (regle des variables).
        String dateDepotLegal = dateStr(first0(depot.get("date"), safe.get("dateDepotLegal")));
        String numeroDepotLegal = str(first0(depot.get("numero"), safe.get("depotLegalNumero")));
        put(v, "DATE_DEPOT_LEGAL",
                dateDepotLegal);
        put(v, "DEPOT_LEGAL_NUMERO",
                numeroDepotLegal);

        return v;
    }

    // ==================================================================
    // Helpers (coercion / format) — alignés sur ModificationAnnonceVarsBuilder
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

    private static String first(String... vals) {
        for (String s : vals) if (s != null && !s.isBlank()) return s;
        return "";
    }

    private static Object first0(Object... vals) {
        for (Object o : vals) if (o != null && !(o instanceof String s && s.isBlank())) return o;
        return null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
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
