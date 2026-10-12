package ma.jurika.ai.workflow.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Builder des variables de l'<b>avis de dissolution anticip\u00e9e</b> (annonce l\u00e9gale) \u2014
 * lot Dissolution 4 \u00e9tapes (2026-08-12).
 *
 * <p>Mod\u00e8les {@code ANNONCE_LEGALE_DISSOLUTION_SARL} / {@code _SARL_AU} : avis <b>lin\u00e9aire</b>
 * (aucune boucle, aucune condition) \u2014 seul le chapeau diff\u00e8re entre les deux formes
 * (\u00ab les associ\u00e9s \u2026 ont d\u00e9cid\u00e9 \u00bb vs \u00ab l'associ\u00e9 unique \u2026 a d\u00e9cid\u00e9 \u00bb), ce qui est port\u00e9 par le
 * mod\u00e8le lui-m\u00eame. Les variables sont donc <b>identiques</b> pour les deux codes.
 *
 * <p>R\u00e9utilise le noyau s\u00e9ance {@link SeancePvVarsBuilder} pour l'identit\u00e9 soci\u00e9t\u00e9
 * (DENOMINATION, CAPITAL_CHIFFRES, SIEGE_SOCIAL, VILLE_GREFFE, RC_NUMERO) \u2014 pr\u00e9-remplie
 * depuis la BD par {@code SocieteIdentityEnricher} c\u00f4t\u00e9 contr\u00f4leur, jamais re-saisie.
 *
 * <p>Variables propres \u00e0 l'avis :
 * <ul>
 *   <li>{@code $DATE_DISSOLUTION} \u2014 date de l'AGE (ou de la d\u00e9cision de l'associ\u00e9 unique)
 *       saisie \u00e0 l'\u00e9tape 1 du workflow ;</li>
 *   <li>{@code $LIQUIDATEUR_CIVILITE / _PRENOM / _NOM / _ADRESSE} \u2014 liquidateur choisi \u00e0
 *       l'\u00e9tape 1 (s\u00e9lection BD parmi les g\u00e9rants/associ\u00e9s, ou saisie externe + OCR CIN) ;</li>
 *   <li>{@code $SIEGE_LIQUIDATION} \u2014 si\u00e8ge de la liquidation (\u00e9tape 1).</li>
 * </ul>
 *
 * <p><b>{@code $DATE_DEPOT_LEGAL} / {@code $DEPOT_LEGAL_NUMERO}</b> : attribu\u00e9s par le greffe
 * <b>APR\u00c8S</b> le d\u00e9p\u00f4t, donc inconnus \u00e0 la g\u00e9n\u00e9ration. Rendus <b>vides</b> si non fournis
 * (aucun marqueur r\u00e9siduel) \u2014 m\u00eame contrat que l'annonce de modification.
 *
 * <p>Le <b>motif</b> de dissolution n'appara\u00eet pas dans l'avis (il reste au PV) : c'est
 * conforme au mod\u00e8le directeur.
 */
public final class DissolutionAnnonceVarsBuilder {


    static final String TPL_SARL = "ANNONCE_LEGALE_DISSOLUTION_SARL";
    static final String TPL_SARL_AU = "ANNONCE_LEGALE_DISSOLUTION_SARL_AU";

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private DissolutionAnnonceVarsBuilder() {}

    static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // 1) Identit\u00e9 soci\u00e9t\u00e9 (pr\u00e9-remplie BD par l'appelant) via le noyau s\u00e9ance.
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> societe = asMap(safe.get("societe"));
        Map<String, Object> dissolution = asMap(safe.get("dissolution"));
        Map<String, Object> liquidateur = asMap(safe.get("liquidateur"));
        Map<String, Object> seance = asMap(safe.get("seance"));

        // 2) Date de dissolution = date de l'AGE / de la d\u00e9cision de l'associ\u00e9 unique.
        put(v, "DATE_DISSOLUTION", dateStr(first0(
                dissolution.get("date"), dissolution.get("dateDissolution"),
                dissolution.get("dateEffet"), safe.get("dateDissolution"),
                safe.get("dateAGE"), seance.get("date"))));

        // 3) Liquidateur (personne physique) + si\u00e8ge de la liquidation.
        put(v, "LIQUIDATEUR_CIVILITE", normCivilite(liquidateur.get("civilite")));
        put(v, "LIQUIDATEUR_PRENOM", str(liquidateur.get("prenom")));
        put(v, "LIQUIDATEUR_NOM", str(liquidateur.get("nom")));
        put(v, "LIQUIDATEUR_ADRESSE", str(liquidateur.get("adresse")));
        // L4b (E7) : plus de repli sur le siege social (valeur supposee) ; le modele du corpus
        // demande si le siege de la liquidation est le siege social, et sinon son adresse.
        put(v, "SIEGE_LIQUIDATION", first(str(liquidateur.get("siege")), str(safe.get("siegeLiquidation")),
                str(safe.get("liquidationSiege"))));
        corpus(v, safe, dissolution, liquidateur, seance);

        // 4) D\u00e9p\u00f4t l\u00e9gal : attribu\u00e9 APR\u00c8S d\u00e9p\u00f4t \u2192 vide si non fourni (aucun marqueur r\u00e9siduel).
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

        // Lot L4d (E11) : SARL AU, l'avis imprime $ASSOCIE_UNIQUE comme l'IDENTITE de l'associe
        // unique ("..., <identite>, associe unique de la societe") ; le drapeau "oui" s'y
        // imprimait (avis publie). Meme regle que les PV de la famille (LiquidationCorpusCommun).
        LiquidationCorpusCommun.identiteAssocieUnique(v, safe, templateCode.endsWith("_SARL_AU"));
        return v;
    }

    /** Motifs de dissolution du corpus (valeurs testees par le modele, mot pour mot). */
    static final java.util.Set<String> MOTIFS = java.util.Set.of("volonte_associes", "terme_statutaire",
            "realisation_objet", "extinction_objet", "situation_nette_art86", "cause_statutaire", "autre");

    /**
     * Lot L4b (E7, D5) : variables du modele du corpus. Les selecteurs (motif, siege de la
     * liquidation, forme du liquidateur) viennent de la saisie ; une donnee absente reste
     * absente et la generation la nomme.
     */
    static void corpus(Map<String, Object> v, Map<String, Object> safe, Map<String, Object> dissolution,
                       Map<String, Object> liquidateur, Map<String, Object> seance) {
        Object decision = first0(seance.get("date"), dissolution.get("date"), dissolution.get("dateDissolution"),
                safe.get("dateAGE"));
        putSi(v, "DISSOLUTION_DATE_DECISION", decision == null ? null : dateStr(decision));
        putSi(v, "DISSOLUTION_DATE_EFFET", dateOuNull(dissolution.get("dateEffet")));
        String motif = str(dissolution.get("nature")).trim();
        if (MOTIFS.contains(motif)) v.put("DISSOLUTION_MOTIF", motif);
        putSi(v, "DISSOLUTION_TERME_DATE", dateOuNull(dissolution.get("termeDate")));
        putSi(v, "EXERCICE_CLOS_DATE", dateOuNull(dissolution.get("exerciceClosDate")));
        putSi(v, "DISSOLUTION_CLAUSE_STATUTAIRE_ARTICLE", str(dissolution.get("clauseArticle")));
        if ("autre".equals(motif)) putSi(v, "DISSOLUTION_MOTIF_AUTRE_TEXTE", str(first0(dissolution.get("motif"), safe.get("motif"))));

        String identique = norm(first0(dissolution.get("siegeLiquidationIdentique"), safe.get("siegeLiquidationIdentique")));
        if (identique.equals("oui") || identique.equals("non")) v.put("LIQUIDATION_SIEGE_IDENTIQUE", identique);
        if (identique.equals("non")) {
            putSi(v, "LIQUIDATION_SIEGE_ADRESSE", str(first0(safe.get("siegeLiquidation"), safe.get("liquidationSiege"),
                    liquidateur.get("siege"))));
        }

        liquidateurCorpus(v, liquidateur);
        putSi(v, "LIQUIDATEUR_MANDAT_DATE_EFFET", dateOuNull(liquidateur.get("mandatDateEffet")));
        putSi(v, "LIQUIDATEUR_DUREE_MANDAT", str(liquidateur.get("dureeMandat")));
        Object co = safe.get("coLiquidateurs");
        // Le parcours ne saisit qu'un liquidateur : aucun co-liquidateur saisi.
        v.put("CO_LIQUIDATEURS_EXISTENT", co instanceof java.util.List<?> l && !l.isEmpty() ? "oui" : "non");
        tribunal(v, safe);
    }

    /**
     * Liquidateur : personne physique saisie ; personne morale (forme "morale", lot L4d E5, ou
     * denomination donnee) avec son capital, son ICE et son representant permanent.
     */
    static void liquidateurCorpus(Map<String, Object> v, Map<String, Object> liquidateur) {
        if ("morale".equalsIgnoreCase(str(liquidateur.get("forme")))
                || !str(liquidateur.get("denomination")).isBlank()) {
            v.put("LIQUIDATEUR_FORME", "personne morale");
            putSi(v, "LIQUIDATEUR_DENOMINATION", str(liquidateur.get("denomination")));
            putSi(v, "LIQUIDATEUR_FORME_JURIDIQUE", str(liquidateur.get("formeJuridique")));
            putSi(v, "LIQUIDATEUR_SIEGE_SOCIAL", str(liquidateur.get("siegeSocial")));
            putSi(v, "LIQUIDATEUR_RC_VILLE", str(liquidateur.get("rcVille")));
            putSi(v, "LIQUIDATEUR_RC_NUMERO", str(liquidateur.get("rcNumero")));
            putSi(v, "LIQUIDATEUR_CAPITAL_CHIFFRES", str(liquidateur.get("capital")));
            putSi(v, "LIQUIDATEUR_ICE", str(liquidateur.get("ice")));
            putSi(v, "LIQUIDATEUR_REPRESENTANT_PERMANENT_NOM", str(liquidateur.get("representantNom")));
            putSi(v, "LIQUIDATEUR_REPRESENTANT_PERMANENT_QUALITE", str(liquidateur.get("representantQualite")));
        } else if (!str(liquidateur.get("nom")).isBlank()) {
            v.put("LIQUIDATEUR_FORME", "personne physique");
            putSi(v, "LIQUIDATEUR_PIECE_TYPE", str(liquidateur.get("pieceType")));
            putSi(v, "LIQUIDATEUR_PIECE_NUMERO", str(first0(liquidateur.get("pieceNumero"), liquidateur.get("cin"))));
        }
    }

    /** Tribunal du greffe : ville de la fiche, type deduit de la ville. */
    static void tribunal(Map<String, Object> v, Map<String, Object> safe) {
        Map<String, Object> societe = asMap(safe.get("societe"));
        String ville = first(str(v.get("VILLE_GREFFE")), str(societe.get("villeGreffe")), str(societe.get("rcVille")));
        putSi(v, "TRIBUNAL_VILLE", ville);
        putSi(v, "TRIBUNAL_TYPE", TribunalDerive.type(ville));
    }

    private static String dateOuNull(Object o) {
        if (o == null || String.valueOf(o).isBlank()) return null;
        return dateStr(o);
    }

    /** Pose la valeur seulement si elle existe (une donnee absente reste absente). */
    private static void putSi(Map<String, Object> v, String key, String value) {
        if (value != null && !value.isBlank()) v.put(key, value);
    }

    // ==================================================================
    // Helpers (coercion / format) \u2014 align\u00e9s sur ModificationAnnonceVarsBuilder
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
