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
import java.util.Set;

/**
 * Builder des variables de l'<b>avis de modification</b> (annonce légale) — Phase 2 (2026-08-11).
 *
 * <p>Modèles {@code ANNONCE_LEGALE_MODIFICATION_SARL} / {@code _SARL_AU} : un même avis peut porter
 * plusieurs décisions <b>publiables</b> (boucle {@code ▼ DECISIONS ▲}), chacune sélectionnant, à
 * l'expansion, le bloc {@code ◇ SI : $DECISION_TYPE = « … »} du modèle.
 *
 * <p>Réutilise le noyau séance {@link SeancePvVarsBuilder} pour l'identité société (DENOMINATION,
 * CAPITAL_CHIFFRES, SIEGE_SOCIAL, VILLE_GREFFE, RC_NUMERO) et {@code ASSEMBLEE_DATE}. Traduit chaque
 * {@code resolutionType} (workflow) vers un {@code $DECISION_TYPE} (annonce) — 3 vocabulaires
 * distincts — et pose les sous-variables du dictionnaire des annonces légales (§4).
 *
 * <p><b>Filtre des NON-PUBLIABLES</b> : les décisions purement internes (date de clôture, CAC,
 * nantissement de parts, décisions AGO d'approbation / affectation / distribution / conventions /
 * renouvellement-rémunération du gérant, etc.) ne produisent <b>aucune ligne</b> d'avis. Si aucune
 * décision publiable n'est sélectionnée, la boucle {@code DECISIONS} est vide (le front n'offre
 * alors pas la génération de l'annonce).
 *
 * <p><b>{@code $DEPOT_LEGAL_NUMERO} / {@code $DATE_DEPOT_LEGAL}</b> : attribués par le greffe APRÈS
 * dépôt, donc inconnus à la génération. Rendus avec le marqueur « à compléter après immatriculation » si non fournis.
 */
public final class ModificationAnnonceVarsBuilder {

    /**
     * Marqueur neutre des données attribuées par le greffe APRÈS le dépôt.
     * Même valeur que {@code CreationDirecteurVarsBuilder.POST_IMMAT} : un avis
     * dit ce qui reste à compléter plutôt que de laisser un blanc dans la phrase.
     */
    private static final String POST_IMMAT = "[à compléter après immatriculation]";

    static final String TPL_SARL = "ANNONCE_LEGALE_MODIFICATION_SARL";
    static final String TPL_SARL_AU = "ANNONCE_LEGALE_MODIFICATION_SARL_AU";

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    /**
     * resolutionTypes NON opposables aux tiers → aucune ligne d'avis (spec + notes d'emploi
     * des modèles : types 7, 20, 26 + décisions AGO internes).
     */
    static final Set<String> NON_PUBLIABLES = Set.of(
            "modification_exercice", "commissaire_comptes", "nantissement_parts",
            "approbation_comptes", "affectation_resultat", "distribution_dividendes",
            "distribution_reserves", "acompte_dividendes", "conventions_reglementees",
            "renouvellement_gerant", "remuneration_gerant", "ratification_actes_formation",
            "autorisation_gerance", "designation_commissaire_transformation",
            "pouvoirs_formalites"
    );

    private ModificationAnnonceVarsBuilder() {}

    static Map<String, Object> build(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        boolean isAu = templateCode != null && templateCode.toUpperCase(Locale.ROOT).contains("SARL_AU");

        // 1) Identité société + ASSEMBLEE_DATE via le noyau séance (pré-rempli BD par l'appelant).
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> societe = asMap(safe.get("societe"));
        String capitalAncien = amount(first0(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        String siegeActuel = str(first0(societe.get("siegeSocial"), societe.get("adresseSiege")));
        String villeGreffe = str(first0(societe.get("villeGreffe"), societe.get("rcVille")));

        // 2) Dépôt légal : attribué APRÈS dépôt → vide si non fourni (aucun marqueur résiduel).
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

        // 3) Boucle DECISIONS = une itération par décision PUBLIABLE.
        List<Map<String, Object>> resolutions = asListOfMaps(safe.get("resolutions"));
        List<Map<String, Object>> decisions = new ArrayList<>();
        for (Map<String, Object> r : resolutions) {
            if (r == null || r.isEmpty()) continue;
            String type = normType(r.get("type"));
            if (type.isEmpty() || NON_PUBLIABLES.contains(type)) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            String decisionType = decisionTypeFor(type, r, isAu);
            it.put("DECISION_TYPE", decisionType);
            fillDecisionVars(it, decisionType, type, r, capitalAncien, siegeActuel, villeGreffe);
            decisions.add(it);
        }
        v.put("DECISIONS", decisions);
        return v;
    }

    // ==================================================================
    // Correspondance resolutionType (workflow) → $DECISION_TYPE (annonce)
    // ==================================================================
    private static String decisionTypeFor(String type, Map<String, Object> r, boolean isAu) {
        return switch (type) {
            case "modification_denomination" -> "denomination";
            case "modification_objet" -> "objet";
            // Fix M1 (2026-08-16) — même repli « non » que ModificationDirecteurMapper :
            // l'annonce et le PV d'une même séance ne doivent jamais diverger.
            case "transfert_siege" ->
                    "oui".equals(normOuiNon(r.get("siegeMemePrefecture"), "non"))
                            ? "siege_meme_ressort" : "siege_hors_ressort";
            case "prorogation_duree" -> "prorogation_duree";
            case "augmentation_capital_numeraire", "augmentation_capital_nature",
                 "augmentation_capital_incorporation" -> "augmentation_capital";
            case "reduction_capital" -> "reduction_capital";
            case "capitaux_propres_art86" -> "regularisation_capitaux_propres";
            case "agrement_cession" -> "cession_parts";
            case "agrement_transmission" -> "transmission_deces";
            case "cession_parts_pluripersonnelle" ->
                    isAu ? "passage_au_vers_sarl" : "passage_sarl_vers_au";
            case "nomination_gerant" -> "nomination_gerant";
            case "revocation_gerant" -> "cessation_gerant";
            case "transformation" -> "transformation_forme";
            case "mise_harmonie_statuts" -> "refonte_statuts";
            // modification_statuts_autre / operation_restructuration / clauses → texte libre.
            default -> "autre";
        };
    }

    // ==================================================================
    // Sous-variables par $DECISION_TYPE (dictionnaire annonces légales §4)
    // ==================================================================
    private static void fillDecisionVars(Map<String, Object> it, String decisionType, String type,
                                         Map<String, Object> r, String capitalAncien,
                                         String siegeActuel, String villeGreffe) {
        switch (decisionType) {
            case "denomination" ->
                    put(it, "DENOMINATION_NOUVELLE", str(r.get("nouvelleDenomination")));
            case "objet" -> {
                put(it, "OBJET_MODIFICATION_SENS", objetSens(r.get("objetAction")));
                put(it, "OBJET_MODIFICATION_TEXTE",
                        first(str(r.get("objetModification")), str(r.get("objetModificationTexte"))));
            }
            case "siege_meme_ressort" ->
                    put(it, "SIEGE_SOCIAL_NOUVEAU", str(first0(r.get("nouveauSiege"), r.get("siegeSocialNouveau"))));
            case "siege_hors_ressort" -> {
                put(it, "SIEGE_SOCIAL_NOUVEAU", str(first0(r.get("nouveauSiege"), r.get("siegeSocialNouveau"))));
                put(it, "SIEGE_SOCIAL", siegeActuel);
                put(it, "VILLE_GREFFE_DEPART", first(str(r.get("villeGreffeDepart")), villeGreffe));
                put(it, "VILLE_GREFFE_ARRIVEE", str(r.get("villeGreffeArrivee")));
            }
            case "prorogation_duree" -> {
                put(it, "DUREE_SOCIETE_NOUVELLE",
                        str(first0(r.get("prorogationDuree"), r.get("dureeSocieteNouvelle"))));
                put(it, "DUREE_POINT_DEPART",
                        dateStr(first0(r.get("dureePointDepart"), r.get("dateExpirationInitiale"))));
            }
            case "reduction_duree" ->
                    put(it, "DUREE_SOCIETE_NOUVELLE", str(r.get("dureeSocieteNouvelle")));
            case "agrement_nouvel_associe", "passage_au_vers_sarl" ->
                    put(it, "NOUVEL_ASSOCIE_NOM",
                            first(str(r.get("nouvelAssocieNom")), str(r.get("cessionnaireNom"))));
            case "cession_parts" -> {
                put(it, "CESSION_CEDANT_NOM", str(r.get("cedantNom")));
                put(it, "CESSION_CESSIONNAIRE_NOM", str(r.get("cessionnaireNom")));
                String nbParts = str(first0(r.get("cessionNbParts"), r.get("cessionNombreParts")));
                put(it, "CESSION_NOMBRE_PARTS", nbParts);
                put(it, "CESSION_NOMBRE_PARTS_LETTRES", lettres(nbParts));
                putAmount(it, "CESSION_PRIX_PART", first0(r.get("cessionPrixPart"), r.get("cessionPrix")));
                put(it, "CESSION_PRIX_PART_LETTRES",
                        lettres(first0(r.get("cessionPrixPart"), r.get("cessionPrix"))));
            }
            case "transmission_deces" -> {
                String nbParts = str(first0(r.get("cessionNbParts"), r.get("cessionNombreParts")));
                put(it, "CESSION_NOMBRE_PARTS", nbParts);
                put(it, "CESSION_NOMBRE_PARTS_LETTRES", lettres(nbParts));
                put(it, "CESSION_CEDANT_NOM", str(r.get("cedantNom")));
                put(it, "CESSION_CESSIONNAIRE_NOM", str(r.get("cessionnaireNom")));
            }
            case "augmentation_capital" -> {
                putAmount(it, "CAPITAL_AUGMENTATION_CHIFFRES", r.get("augcapMontantChiffres"));
                put(it, "CAPITAL_ANCIEN_CHIFFRES",
                        first(amount(r.get("capitalAncienChiffres")), capitalAncien));
                putAmount(it, "CAPITAL_NOUVEAU_CHIFFRES", r.get("augcapNouveauCapital"));
                put(it, "AUGMENTATION_MODE", augmentationMode(type, r.get("augcapMode")));
                String prime = normOuiNon(first0(r.get("primeEmission"), r.get("primeEmissionPresente")), "non");
                put(it, "PRIME_EMISSION_PRESENTE", prime);
                putAmount(it, "PRIME_EMISSION_CHIFFRES",
                        first0(r.get("primeEmissionChiffres"), r.get("primeEmissionMontant")));
                put(it, "DPS_SUPPRIME", normOuiNon(first0(r.get("dpsSupprime"), r.get("dpsSuppression")), "non"));
                put(it, "DPS_BENEFICIAIRE_NOM", str(r.get("dpsBeneficiaireNom")));
            }
            case "reduction_capital" -> {
                putAmount(it, "CAPITAL_REDUCTION_CHIFFRES", first0(r.get("capitalReductionChiffres"), r.get("redcapMontant")));
                put(it, "CAPITAL_ANCIEN_CHIFFRES",
                        first(amount(r.get("capitalAncienChiffres")), capitalAncien));
                putAmount(it, "CAPITAL_NOUVEAU_CHIFFRES", first0(r.get("capitalNouveauChiffres"), r.get("redcapNouveauCapital")));
                put(it, "REDUCTION_MOTIF", reductionMotif(r.get("redcapMotif")));
            }
            case "regularisation_capitaux_propres", "passage_sarl_vers_au",
                 "modification_pouvoirs_signature", "refonte_statuts" -> {
                /* aucune sous-variable */
            }
            case "nomination_gerant" -> {
                Map<String, Object> g = firstGerant(r);
                put(it, "GERANT_CIVILITE", normCivilite(first0(g.get("civilite"), r.get("gerantCivilite"))));
                put(it, "GERANT_PRENOM", str(first0(g.get("prenom"), r.get("gerantPrenom"))));
                put(it, "GERANT_NOM", str(first0(g.get("nom"), r.get("gerantNom"))));
                put(it, "GERANT_QUALITE_ACCORD", first(str(r.get("gerantQualiteAccord")), "gérant"));
            }
            case "cessation_gerant" -> {
                put(it, "GERANT_SORTANT_NOM",
                        first(str(r.get("gerantSortantNom")), str(r.get("gerantNom"))));
                put(it, "CESSATION_MOTIF", cessationMotif(type, r.get("cessationMotif")));
            }
            case "transformation_forme" ->
                    put(it, "FORME_NOUVELLE",
                            first(str(r.get("formeNouvelle")), str(r.get("transformationForme")), "société anonyme"));
            default -> // "autre"
                    put(it, "DECISION_TEXTE_LIBRE",
                            first(str(r.get("decisionTexteLibre")), str(r.get("statutsNouveauLibelle")),
                                    str(r.get("objet")), "Autre décision de l'assemblée"));
        }
    }

    // ==================================================================
    // Normalisations propres à l'annonce (libellés EXACTS du modèle)
    // ==================================================================

    /** « l'adjonction de » / « la suppression de » / « le remplacement par ». */
    private static String objetSens(Object raw) {
        String s = norm(raw);
        if (s.startsWith("exten") || s.contains("adjon") || s.contains("ajout")) return "l'adjonction de";
        if (s.contains("suppr") || s.contains("retrait")) return "la suppression de";
        return "le remplacement par";
    }

    /** « numeraire » / « nature » / « incorporation » / « compensation » / « elevation_valeur ». */
    private static String augmentationMode(String type, Object rawMode) {
        String s = norm(rawMode);
        if (s.contains("compensation") || s.contains("creance")) return "compensation";
        if (s.contains("elevation") || s.contains("nominal")) return "elevation_valeur";
        return switch (type) {
            case "augmentation_capital_nature" -> "nature";
            case "augmentation_capital_incorporation" -> "incorporation";
            default -> "numeraire";
        };
    }

    /** « pertes » / « non_pertes ». */
    private static String reductionMotif(Object raw) {
        String s = norm(raw);
        return (s.contains("non") || s.contains("rachat")) ? "non_pertes" : "pertes";
    }

    /** « demission » / « revocation » / « cessation ». */
    private static String cessationMotif(String type, Object raw) {
        String s = norm(raw);
        if (s.startsWith("demi")) return "demission";
        if (s.startsWith("revoc")) return "revocation";
        if ("revocation_gerant".equals(type) && s.isEmpty()) return "revocation";
        return s.isEmpty() ? "cessation" : s;
    }

    private static Map<String, Object> firstGerant(Map<String, Object> r) {
        List<Map<String, Object>> gs = asListOfMaps(first0(r.get("gerants"), r.get("gerantsNommes")));
        return gs.isEmpty() ? new LinkedHashMap<>() : gs.get(0);
    }

    private static String normCivilite(Object raw) {
        String s = norm(raw);
        if (s.startsWith("mme") || s.startsWith("madame")) return "Mme";
        if (s.startsWith("mlle") || s.startsWith("mademoiselle")) return "Mlle";
        if (s.startsWith("m")) return "M.";
        return raw == null ? "" : String.valueOf(raw);
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

    // ==================================================================
    // Helpers (coercion / format) — alignés sur ModificationDirecteurMapper
    // ==================================================================

    private static void put(Map<String, Object> v, String key, String value) {
        v.put(key, value == null ? "" : value);
    }

    private static void putAmount(Map<String, Object> v, String key, Object raw) {
        v.put(key, amount(raw));
    }

    private static String amount(Object raw) {
        Long n = toLong(raw);
        return n == null ? "" : String.format(Locale.FRANCE, "%,d", n);
    }

    private static String lettres(Object raw) {
        Long n = toLong(raw);
        return n == null ? "" : FrenchNumberToLetters.numberToLetters(n);
    }

    private static String dateStr(Object o) {
        LocalDate d = toDate(o);
        if (d != null) return d.format(DATE_FR);
        return o == null ? "" : String.valueOf(o);
    }

    static String normType(Object raw) {
        return norm(raw).replace(' ', '_').replace('-', '_').replaceAll("_+", "_");
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
