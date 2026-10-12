package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import ma.jurika.ai.document.GenerationRefuseeException.DonneeManquante;
import ma.jurika.ai.document.format.FrenchNumberToLetters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Noyau de mapping du proces-verbal de modification : assemblee generale des associes (SARL) ou
 * decisions de l'associe unique (SARL AU), a resolutions typees.
 *
 * <p><b>Lot L4d (E11c)</b> : le proces-verbal est rendu par les modeles du corpus du cabinet,
 * {@code PV_AG_SARL} et {@code PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU} (voir {@link #gabarit}). Les
 * codes logiques {@code PV_MODIFICATION_SARL(_AU)} restent ceux de l'ecran et de la Data Room.
 * Chaque resolution porte {@code $RESOLUTION_TYPE}, valeur du modele du corpus (table explicite
 * {@link #TYPES_SARL} / {@link #typeAu}) : les codes de la plateforme ne sont jamais devines.
 *
 * <p><b>Regle des variables</b> : une donnee interne absente reste vide (le moteur la nomme et
 * la generation est refusee) ; une donnee qui PILOTE une branche du modele est exigee
 * ({@link SaisieExigee}) ; une donnee calculable est derivee (capital nouveau, numeros des
 * parts, valeur nominale...) et une saisie incoherente avec le calcul est refusee. Une decision
 * que le modele du corpus ne sait pas rediger est refusee, donnees nommees.
 *
 * <p><b>Boucles imbriquees</b> ({@code GERANTS}, {@code AFFECTATIONS}, {@code BENEFICIAIRES_DPS},
 * {@code AUGMENTATION_MODALITES}, {@code REPARTITION_NOUVELLE}, {@code CONVENTIONS_REGLEMENTEES}) :
 * le moteur les developpe au scope GLOBAL avant la boucle {@code RESOLUTIONS} ; une seule
 * resolution par proces-verbal peut donc les porter. Une seconde est refusee (elle imprimerait
 * les lignes de la premiere).
 */
public final class ModificationDirecteurMapper {

    static final String TPL_SARL = "PV_MODIFICATION_SARL";
    static final String TPL_SARL_AU = "PV_MODIFICATION_SARL_AU";
    /** Modeles du corpus qui rendent le proces-verbal de modification. */
    static final String CORPUS_SARL = "PV_AG_SARL";
    static final String CORPUS_SARL_AU = "PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU";

    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);

    private static final String[] ORDINALES_F = {"", "PREMI\u00c8RE", "DEUXI\u00c8ME", "TROISI\u00c8ME",
            "QUATRI\u00c8ME", "CINQUI\u00c8ME", "SIXI\u00c8ME", "SEPTI\u00c8ME", "HUITI\u00c8ME", "NEUVI\u00c8ME",
            "DIXI\u00c8ME", "ONZI\u00c8ME", "DOUZI\u00c8ME", "TREIZI\u00c8ME", "QUATORZI\u00c8ME", "QUINZI\u00c8ME",
            "SEIZI\u00c8ME", "DIX-SEPTI\u00c8ME", "DIX-HUITI\u00c8ME", "DIX-NEUVI\u00c8ME", "VINGTI\u00c8ME"};

    /**
     * Type de decision de la plateforme -> valeur de {@code $RESOLUTION_TYPE} dans PV_AG_SARL
     * (branches lues dans le modele du corpus 2026-10-11). Les trois augmentations de capital de
     * la plateforme sont une seule resolution du modele, distinguee par sa modalite.
     */
    static final Map<String, String> TYPES_SARL = Map.ofEntries(
            Map.entry("approbation_comptes", "approbation_comptes"),
            Map.entry("affectation_resultat", "affectation_resultat"),
            Map.entry("distribution_dividendes", "distribution_dividendes"),
            Map.entry("distribution_reserves", "distribution_reserves"),
            Map.entry("acompte_dividendes", "acompte_dividendes"),
            Map.entry("nomination_gerant", "nomination_gerant"),
            Map.entry("renouvellement_gerant", "renouvellement_gerant"),
            Map.entry("revocation_gerant", "revocation_gerant"),
            Map.entry("remuneration_gerant", "remuneration_gerant"),
            Map.entry("conventions_reglementees", "conventions_reglementees"),
            Map.entry("commissaire_comptes", "commissaire_comptes"),
            Map.entry("ratification_actes_formation", "ratification_actes_formation"),
            Map.entry("autorisation_gerance", "autorisation_gerance"),
            Map.entry("augmentation_capital_numeraire", "augmentation_capital"),
            Map.entry("augmentation_capital_nature", "augmentation_capital"),
            Map.entry("augmentation_capital_incorporation", "augmentation_capital"),
            Map.entry("reduction_capital", "reduction_capital"),
            Map.entry("capitaux_propres_art86", "capitaux_propres_art86"),
            Map.entry("modification_denomination", "modification_denomination"),
            Map.entry("modification_objet", "modification_objet"),
            Map.entry("transfert_siege", "transfert_siege"),
            Map.entry("prorogation_duree", "prorogation_duree"),
            Map.entry("modification_exercice", "modification_exercice"),
            Map.entry("mise_harmonie_statuts", "mise_harmonie_statuts"),
            Map.entry("modification_statuts_autre", "modification_statuts_autre"),
            Map.entry("agrement_cession", "agrement_cession"),
            Map.entry("agrement_transmission", "agrement_transmission"),
            Map.entry("nantissement_parts", "nantissement_parts"),
            Map.entry("transformation", "transformation"),
            Map.entry("designation_commissaire_transformation", "designation_commissaire_transformation"),
            Map.entry("operation_restructuration", "operation_restructuration"),
            Map.entry("pouvoirs_formalites", "pouvoirs_formalites"));

    /**
     * Type de la plateforme -> valeur de {@code $RESOLUTION_TYPE} dans
     * PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU (codes de la matrice des decisions, differents de ceux
     * du PV d'assemblee). Le commissaire aux comptes se resout selon l'action (voir
     * {@link #typeAu}).
     */
    static final Map<String, String> TYPES_AU = Map.ofEntries(
            Map.entry("approbation_comptes", "approbation_comptes"),
            // Lot L5 (E5) : l'affectation d'une perte est "affectation_resultat_deficitaire" (voir typeAu).
            Map.entry("affectation_resultat", "affectation_resultat"),
            Map.entry("distribution_dividendes", "distribution_dividendes"),
            Map.entry("distribution_reserves", "distribution_reserves"),
            Map.entry("nomination_gerant", "nomination_gerant"),
            Map.entry("renouvellement_gerant", "renouvellement_gerant"),
            Map.entry("revocation_gerant", "revocation_gerant"),
            Map.entry("remuneration_gerant", "remuneration_gerant"),
            Map.entry("conventions_reglementees", "conventions_reglementees"),
            Map.entry("ratification_actes_formation", "ratification_actes_formation"),
            Map.entry("autorisation_gerance", "autorisation_gerance"),
            Map.entry("augmentation_capital_numeraire", "augmentation_capital"),
            Map.entry("augmentation_capital_nature", "augmentation_capital"),
            Map.entry("augmentation_capital_incorporation", "augmentation_capital"),
            Map.entry("reduction_capital", "reduction_capital"),
            Map.entry("capitaux_propres_art86", "regularisation_capitaux_propres"),
            Map.entry("modification_denomination", "modification_denomination"),
            Map.entry("modification_objet", "modification_objet"),
            Map.entry("transfert_siege", "transfert_siege"),
            Map.entry("prorogation_duree", "prorogation_duree"),
            Map.entry("modification_exercice", "modification_exercice"),
            Map.entry("mise_harmonie_statuts", "refonte_statuts"),
            Map.entry("modification_statuts_autre", "modification_clauses_statutaires"),
            Map.entry("transformation", "transformation_societe"),
            Map.entry("designation_commissaire_transformation", "designation_commissaire_transformation"),
            Map.entry("cession_parts_pluripersonnelle", "passage_au_vers_sarl"),
            Map.entry("pouvoirs_formalites", "pouvoirs_formalites"));

    /**
     * Decisions que le modele de l'associe unique ne redige pas : refusees, donnees nommees.
     * Lot L5 (E5) : l'affectation du resultat et les distributions sont desormais saisies ;
     * reste l'acompte, que le modele exclut ("Aucun acompte sur dividendes n'est organise par la
     * reglementation marocaine").
     */
    static final Map<String, List<DonneeManquante>> NON_COUVERTS_AU = Map.of(
            "acompte_dividendes", List.of(
                    dm("RESOLUTION_TYPE", "Le mod\u00e8le des d\u00e9cisions de l'associ\u00e9 unique ne pr\u00e9voit pas d'acompte sur dividendes")));

    private ModificationDirecteurMapper() {}

    // ==================================================================
    // Points d'entree
    // ==================================================================

    /** Lot L4d (E11c) : modele du corpus qui rend le proces-verbal de modification. */
    static String gabarit(String templateCode) {
        if (TPL_SARL.equals(templateCode)) return CORPUS_SARL;
        if (TPL_SARL_AU.equals(templateCode)) return CORPUS_SARL_AU;
        return templateCode;
    }

    static Map<String, Object> pvVars(String templateCode, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        boolean isAu = templateCode != null && templateCode.toUpperCase(Locale.ROOT).contains("SARL_AU");

        // 1) Noyau seance (identite societe, presence et quorum, ordre du jour, associes).
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(templateCode, safe));

        Map<String, Object> societe = asMap(safe.get("societe"));
        Map<String, Object> seance = asMap(safe.get("seance"));
        Map<String, Object> cac = asMap(safe.get("commissaireComptes"));

        // 2) Variables globales.
        //    Lot L3 (D6) : le quorum est SAISI. Il pilote une condition du PV d'assemblee :
        //    absent, la branche "quorum atteint" s'imprimait en silence ; la generation est refusee.
        String quorum = normOuiNon(first0(safe.get("quorumAtteint"), seance.get("quorumAtteint")), "");
        //    Le PV de l'associe unique n'a pas de quorum (aucune condition ne le lit).
        if (quorum.isEmpty() && !isAu) {
            throw new GenerationRefuseeException(templateCode, "Quorum non indiqu\u00e9",
                    List.of(new DonneeManquante("QUORUM_ATTEINT",
                            "Quorum atteint (oui ou non)", "Constat du quorum en t\u00eate du proc\u00e8s-verbal")));
        }
        put(v, "QUORUM_ATTEINT", quorum);
        String cacExiste = normOuiNon(first0(cac.get("existe"), safe.get("cacExiste")), "non");
        put(v, "CAC_EXISTE", cacExiste);
        put(v, "CAC_PRESENT", normOuiNon(first0(cac.get("present"), safe.get("cacPresent")),
                "oui".equals(cacExiste) ? "oui" : "non"));
        put(v, "COMMISSAIRE_COMPTES_NOM",
                first(str(cac.get("nom")), str(safe.get("commissaireComptesNom"))));
        put(v, "GERANT_EST_ASSOCIE",
                normOuiNon(first0(safe.get("gerantEstAssocie"), seance.get("gerantEstAssocie")),
                        isAu ? "oui" : "non"));
        // Noms du corpus (dictionnaire unique) de la seance.
        put(v, "ASSEMBLEE_DATE_PREMIERE", str(v.get("DATE_AG_PREMIERE")));
        Map<String, Object> signature = asMap(safe.get("signature"));
        // L4d : nombre d'originaux PROPOSE a l'ecran (usage du cabinet), confirme par l'employe.
        put(v, "NOMBRE_ORIGINAUX", first(str(safe.get("nombreOriginaux")), str(signature.get("nombreOriginaux"))));

        Long capital = toLong(first0(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        Long nbParts = toLong(societe.get("nombreParts"));
        if (nbParts != null) put(v, "NOMBRE_PARTS", formatAmount(nbParts));
        Long vnActuelle = (capital != null && nbParts != null && nbParts > 0 && capital % nbParts == 0)
                ? capital / nbParts : null;
        put(v, "VALEUR_NOMINALE_PART", vnActuelle == null ? "" : formatAmount(vnActuelle));
        String villeGreffe = first(str(v.get("VILLE_GREFFE")), str(societe.get("rcVille")));
        LocalDate dateActe = toDate(first0(seance.get("date"), seance.get("assembleeDate")));

        if (isAu) {
            globalesAssocieUnique(v, safe, societe);
        }

        Ctx ctx = new Ctx(templateCode, isAu, capital, nbParts, vnActuelle, villeGreffe, dateActe,
                associeUniqueNom(safe), associeUniqueMorale(safe));

        // 3) Boucle RESOLUTIONS + boucles imbriquees (une resolution porteuse par proces-verbal).
        List<Map<String, Object>> resolutions = asListOfMaps(safe.get("resolutions"));
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> boucles = new LinkedHashMap<>();
        for (String b : List.of("GERANTS", "AFFECTATIONS", "BENEFICIAIRES_DPS", "AUGMENTATION_MODALITES",
                "REPARTITION_NOUVELLE", "CONVENTIONS_REGLEMENTEES", "MOTIFS_REFUS", "AFFECTATION_RESULTAT",
                "RESERVES_FACULTATIVES_DOTATIONS", "RESERVES_PRELEVEES", "AYANTS_DROIT")) {
            boucles.put(b, List.of());
        }
        Map<String, String> porteuses = new LinkedHashMap<>();

        VoteSaisi vote = VoteSaisi.de(safe);
        int n = 1;
        for (Map<String, Object> r : resolutions) {
            if (r == null || r.isEmpty()) continue;
            String type = normType(r.get("type"));
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("RESOLUTION_TYPE", isAu ? typeAu(templateCode, type, r) : typeSarl(templateCode, type));
            it.put("RESOLUTION_ORDINAL", first(str(r.get("ordinal")), ordinalF(n)));
            it.put("RESOLUTION_OBJET", first(str(r.get("objet")), defaultObjet(type)));
            // Lot L3 (D6) : la majorite est saisie ("a l'unanimite" n'est plus suppose).
            it.put("RESOLUTION_RESULTAT", first(str(r.get("resultat")), vote.majorite));
            fillTypeVars(it, type, r, ctx, boucles, porteuses);
            out.add(it);
            n++;
        }
        v.put("RESOLUTIONS", out);
        v.putAll(boucles);
        return v;
    }

    // ==================================================================
    // Types : plateforme -> modele du corpus
    // ==================================================================

    private static String typeSarl(String templateCode, String type) {
        if ("capitaux_propres_art86".equals(type)) {
            // PV_AG_SARL imprime dans l'acte une note de redaction : "Le cas ou l'assemblee decide
            // au contraire la dissolution anticipee est traite par le modele PV_DISSOLUTION_LIQUIDATION_SARL."
            throw new GenerationRefuseeException(templateCode,
                    "Capitaux propres inf\u00e9rieurs au quart du capital : le mod\u00e8le PV_AG_SARL imprime une note de r\u00e9daction (renvoi au mod\u00e8le PV_DISSOLUTION_LIQUIDATION_SARL)",
                    List.of(dm("RESOLUTION_TYPE",
                            "Capitaux propres (art. 86) : r\u00e9daction du mod\u00e8le PV_AG_SARL \u00e0 faire corriger par le cabinet")));
        }
        String code = TYPES_SARL.get(type);
        if (code == null) {
            throw new GenerationRefuseeException(templateCode,
                    "D\u00e9cision non pr\u00e9vue par le proc\u00e8s-verbal d'assembl\u00e9e du cabinet : " + type,
                    List.of(new DonneeManquante("RESOLUTION_TYPE", "Type de d\u00e9cision (" + type + ")",
                            "Mod\u00e8le PV_AG_SARL")));
        }
        return code;
    }

    private static String typeAu(String templateCode, String type, Map<String, Object> r) {
        List<DonneeManquante> manquantes = NON_COUVERTS_AU.get(type);
        if (manquantes != null) {
            throw new GenerationRefuseeException(templateCode, defaultObjet(type)
                    + " : le mod\u00e8le des d\u00e9cisions de l'associ\u00e9 unique exige des donn\u00e9es que cet \u00e9cran ne recueille pas",
                    manquantes);
        }
        if ("affectation_resultat".equals(type)) {
            // "affecter le benefice net" ou "affecter la perte nette" : deux branches du modele.
            String sens = choix(templateCode, r.get("resultatSens"), "RESULTAT_NET_SENS",
                    "R\u00e9sultat de l'exercice (b\u00e9n\u00e9fice ou perte)", ModificationDirecteurMapper::normResultatSens);
            return "perte".equals(sens) ? "affectation_resultat_deficitaire" : "affectation_resultat";
        }
        if ("commissaire_comptes".equals(type)) {
            String action = choix(templateCode, r.get("cacAction"), "CAC_ACTION",
                    "Action sur le commissaire aux comptes", ModificationDirecteurMapper::normCacAction);
            return switch (action) {
                case "renouvellement" -> "renouvellement_cac";
                case "fin de mandat" -> "cessation_cac";
                default -> "nomination_cac";
            };
        }
        String code = TYPES_AU.get(type);
        if (code == null) {
            throw new GenerationRefuseeException(templateCode,
                    "D\u00e9cision non pr\u00e9vue par le proc\u00e8s-verbal des d\u00e9cisions de l'associ\u00e9 unique : " + type,
                    List.of(new DonneeManquante("RESOLUTION_TYPE", "Type de d\u00e9cision (" + type + ")",
                            "Mod\u00e8le PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU")));
        }
        return code;
    }

    // ==================================================================
    // Variables propres a chaque type de resolution
    // ==================================================================

    /** Donnees communes a toutes les resolutions d'un proces-verbal. */
    private record Ctx(String templateCode, boolean isAu, Long capital, Long nbParts, Long vnActuelle,
                       String villeGreffe, LocalDate dateActe, String associeUniqueNom,
                       Boolean associeUniqueMorale) {}

    private static void fillTypeVars(Map<String, Object> it, String type, Map<String, Object> r, Ctx c,
                                     Map<String, Object> boucles, Map<String, String> porteuses) {
        String tc = c.templateCode();
        switch (type) {
            case "approbation_comptes" -> {
                putDate(it, "EXERCICE_CLOS_DATE", r.get("exerciceClosDate"));
                String sens = choix(tc, r.get("resultatSens"), "RESULTAT_SENS",
                        "R\u00e9sultat de l'exercice (b\u00e9n\u00e9fice ou perte)", ModificationDirecteurMapper::normResultatSens);
                if (c.isAu()) {
                    put(it, "RESULTAT_NET_SENS", sens);
                    putAmount(it, "RESULTAT_NET_CHIFFRES", r.get("resultatMontant"));
                    put(it, "RESULTAT_NET_LETTRES", lettres(r.get("resultatMontant")));
                    // Le depot des etats de synthese se fait au greffe du tribunal de commerce du
                    // registre : deduit de la ville du greffe (rien deduit hors tribunal de commerce).
                    put(it, "TRIBUNAL_VILLE", tribunalCommerce(c));
                    // "dans les trente jours qui suivent la presente approbation, soit au plus tard le".
                    put(it, "DEPOT_GREFFE_DATE_LIMITE", c.dateActe() == null ? "" : c.dateActe().plusDays(30).format(DATE_FR));
                } else {
                    put(it, "RESULTAT_SENS", sens);
                    putAmount(it, "RESULTAT_MONTANT", r.get("resultatMontant"));
                }
            }
            case "affectation_resultat" -> {
                if (c.isAu()) {
                    affectationAu(it, r, c, boucles, porteuses);
                    return;
                }
                porteuse(tc, porteuses, "AFFECTATIONS", type);
                putDate(it, "EXERCICE_CLOS_DATE", r.get("exerciceClosDate"));
                putAmount(it, "RESULTAT_MONTANT", r.get("resultatMontant"));
                boucles.put("AFFECTATIONS", buildAffectations(r.get("affectations")));
            }
            case "distribution_dividendes" -> {
                if (c.isAu()) {
                    dividendesAu(it, r, c);
                    return;
                }
                putAmount(it, "DIVIDENDE_TOTAL", r.get("dividendeTotal"));
                putAmount(it, "DIVIDENDE_PAR_PART", r.get("dividendeParPart"));
                putDate(it, "DATE_MISE_PAIEMENT", r.get("dateMisePaiement"));
            }
            case "distribution_reserves" -> {
                if (c.isAu()) {
                    reservesAu(it, r, c, boucles, porteuses);
                    return;
                }
                putAmount(it, "PRELEVEMENT_MONTANT", r.get("prelevementMontant"));
                put(it, "PRELEVEMENT_POSTE", str(r.get("prelevementPoste")));
                putAmount(it, "DIVIDENDE_PAR_PART", r.get("dividendeParPart"));
                putDate(it, "DATE_MISE_PAIEMENT", r.get("dateMisePaiement"));
            }
            case "acompte_dividendes" -> {
                putAmount(it, "ACOMPTE_MONTANT", r.get("acompteMontant"));
                putAmount(it, "ACOMPTE_PAR_PART", r.get("acompteParPart"));
            }
            case "nomination_gerant" -> {
                porteuse(tc, porteuses, "GERANTS", type);
                boolean sortant = "oui".equals(normOuiNon(first0(r.get("gerantSortant"),
                        !str(r.get("gerantSortantNom")).isBlank()), "non"));
                put(it, "GERANT_SORTANT", sortant ? "oui" : "non");
                put(it, "GERANT_SORTANT_NOM", str(r.get("gerantSortantNom")));
                put(it, "GERANT_SORTANT_MOTIF", str(r.get("gerantSortantMotif")));
                putDate(it, "GERANT_SORTANT_DATE_EFFET", r.get("gerantSortantDateEffet"));
                put(it, "DUREE_GERANCE", str(r.get("dureeGerance")));
                boucles.put("GERANTS", buildGerants(first0(r.get("gerants"), r.get("gerantsNommes"))));
            }
            case "renouvellement_gerant" -> {
                put(it, "GERANT_NOM", str(r.get("gerantNom")));
                put(it, "DUREE_GERANCE", str(r.get("dureeGerance")));
            }
            case "revocation_gerant" -> {
                put(it, "GERANT_SORTANT_NOM", first(str(r.get("gerantSortantNom")), str(r.get("gerantNom"))));
                if (c.isAu()) {
                    // "a effet du $GERANT_SORTANT_DATE_EFFET" ; le motif est facultatif ("existe").
                    putDate(it, "GERANT_SORTANT_DATE_EFFET", r.get("gerantSortantDateEffet"));
                    put(it, "GERANT_SORTANT_MOTIF", str(r.get("gerantSortantMotif")));
                }
            }
            case "remuneration_gerant" -> {
                put(it, "GERANT_NOM", str(r.get("gerantNom")));
                put(it, "REMUNERATION_PERIODICITE", str(r.get("remunerationPeriodicite")));
                putAmount(it, "REMUNERATION_MONTANT", r.get("remunerationMontant"));
                putDate(it, "REMUNERATION_DATE_EFFET", r.get("remunerationDateEffet"));
            }
            case "conventions_reglementees" -> {
                if (c.isAu()) {
                    porteuse(tc, porteuses, "CONVENTIONS_REGLEMENTEES", type);
                    List<Map<String, Object>> conventions = buildConventions(r.get("conventions"));
                    if (conventions.isEmpty()) {
                        throw new GenerationRefuseeException(tc, "Conventions approuv\u00e9es non indiqu\u00e9es",
                                List.of(dm("CONVENTIONS_REGLEMENTEES",
                                        "Conventions approuv\u00e9es (date, partie int\u00e9ress\u00e9e, objet)")));
                    }
                    boucles.put("CONVENTIONS_REGLEMENTEES", conventions);
                }
            }
            case "commissaire_comptes" -> {
                String action = choix(tc, r.get("cacAction"), "CAC_ACTION",
                        "Action sur le commissaire aux comptes", ModificationDirecteurMapper::normCacAction);
                put(it, "CAC_ACTION", action);
                put(it, "COMMISSAIRE_COMPTES_NOM", str(r.get("commissaireComptesNom")));
                if (!"fin de mandat".equals(action)) {
                    put(it, "DUREE_MANDAT_CAC", str(r.get("dureeMandatCac")));
                    putDate(it, "CAC_FIN_MANDAT_DATE", r.get("cacFinMandatDate"));
                }
                if (c.isAu()) {
                    if ("nomination".equals(action)) {
                        put(it, "COMMISSAIRE_COMPTES_ADRESSE", str(r.get("commissaireComptesAdresse")));
                    }
                    if ("fin de mandat".equals(action)) {
                        putDate(it, "CAC_CESSATION_DATE_EFFET", r.get("cacCessationDateEffet"));
                        put(it, "CAC_CESSATION_MOTIF", str(r.get("cacCessationMotif")));
                    } else {
                        // Facultatif dans le modele ("existe") : la phrase n'est imprimee que saisie.
                        putAmount(it, "CAC_HONORAIRES", r.get("cacHonoraires"));
                    }
                }
            }
            case "ratification_actes_formation" ->
                put(it, "ENGAGEMENTS_MANDAT", str(r.get("engagementsMandat")));
            case "autorisation_gerance" ->
                put(it, "AUTORISATION_OBJET", str(r.get("autorisationObjet")));
            case "augmentation_capital_numeraire", "augmentation_capital_nature",
                 "augmentation_capital_incorporation" -> augmentation(it, type, r, c, boucles, porteuses);
            case "reduction_capital" -> reduction(it, r, c);
            case "capitaux_propres_art86" -> {
                putDate(it, "EXERCICE_CLOS_DATE", r.get("exerciceClosDate"));
                if (c.isAu()) {
                    // "statuant ... dans les trois mois qui suivent l'approbation, le $..._DATE" :
                    // une date d'approbation hors de ce delai rendrait la phrase fausse.
                    LocalDate approbation = toDate(r.get("approbationComptesDate"));
                    if (approbation != null && c.dateActe() != null
                            && (c.dateActe().isBefore(approbation) || c.dateActe().isAfter(approbation.plusMonths(3)))) {
                        refusIncoherence(tc, "ASSEMBLEE_APPROBATION_COMPTES_DATE", "Date d'approbation des comptes",
                                "la d\u00e9cision du " + c.dateActe().format(DATE_FR)
                                        + " n'est pas dans les trois mois qui suivent l'approbation du "
                                        + approbation.format(DATE_FR));
                    }
                    putDate(it, "ASSEMBLEE_APPROBATION_COMPTES_DATE", r.get("approbationComptesDate"));
                    putAmount(it, "SITUATION_NETTE_MONTANT", r.get("situationNetteMontant"));
                }
            }
            case "modification_denomination" -> {
                put(it, "DENOMINATION_NOUVELLE", str(r.get("nouvelleDenomination")));
                putDate(it, "DENOMINATION_DATE_EFFET", r.get("dateEffet"));
                // Facultatif dans le modele ("existe") : la phrase du certificat negatif n'est
                // imprimee que si son numero est saisi ; sa date est alors exigee par le moteur.
                put(it, "CERTIFICAT_NEGATIF_NUMERO", str(r.get("certificatNegatifNumero")));
                putDate(it, "CERTIFICAT_NEGATIF_DATE", r.get("certificatNegatifDate"));
                put(it, "DENOMINATION_ENSEIGNE_MODIFIEE", SaisieExigee.ouiNon(tc, r.get("enseigneModifiee"),
                        "DENOMINATION_ENSEIGNE_MODIFIEE",
                        "Mandat \u00e0 la g\u00e9rance d'harmoniser enseigne, papiers et comptes (oui ou non)"));
                articles(it, r, c);
            }
            case "modification_objet" -> {
                put(it, "OBJET_MODIFICATION_SENS", choix(tc, first0(r.get("objetSens"), r.get("objetAction")),
                        "OBJET_MODIFICATION_SENS", "Sens de la modification de l'objet (extension, suppression ou modification)",
                        ModificationDirecteurMapper::normObjetSens));
                put(it, "OBJET_MODIFICATION_TEXTE", str(r.get("objetModification")));
                putDate(it, "DATE_EFFET", r.get("dateEffet"));
                articles(it, r, c);
            }
            case "transfert_siege" -> {
                // Fix M1 (2026-08-16) : jamais de "meme prefecture" par defaut (elle dispense des
                // formalites au greffe du nouveau ressort) ; la donnee est exigee.
                String meme = SaisieExigee.ouiNon(tc, r.get("siegeMemePrefecture"), "SIEGE_MEME_PREFECTURE",
                        "Nouveau si\u00e8ge dans la m\u00eame pr\u00e9fecture (oui ou non)");
                put(it, "SIEGE_MEME_PREFECTURE", meme);
                put(it, "SIEGE_SOCIAL_NOUVEAU", first(str(r.get("nouveauSiege")), str(r.get("siegeSocialNouveau"))));
                putDate(it, "DATE_EFFET", r.get("dateEffet"));
                if ("non".equals(meme)) {
                    // Greffe de depart : celui du registre actuel de la societe (deduit).
                    put(it, "VILLE_GREFFE_DEPART", c.villeGreffe());
                    put(it, "VILLE_GREFFE_ARRIVEE", str(r.get("villeGreffeArrivee")));
                }
                articles(it, r, c);
            }
            case "prorogation_duree" -> {
                putDate(it, "DATE_EXPIRATION_INITIALE", r.get("dateExpirationInitiale"));
                put(it, "DUREE_SOCIETE_NOUVELLE", str(r.get("prorogationDuree")));
                putDate(it, "NOUVELLE_DUREE_ECHEANCE", r.get("nouvelleDureeEcheance"));
                articles(it, r, c);
            }
            case "modification_exercice" -> {
                put(it, "NOUVELLE_DATE_CLOTURE", str(r.get("nouvelleDateCloture")));
                put(it, "EXERCICE_TRANSITOIRE_DUREE", str(r.get("exerciceTransitoireDuree")));
                putDate(it, "EXERCICE_TRANSITOIRE_DEBUT", r.get("exerciceTransitoireDebut"));
                putDate(it, "EXERCICE_TRANSITOIRE_FIN", r.get("exerciceTransitoireFin"));
                articles(it, r, c);
            }
            case "mise_harmonie_statuts" -> {
                if (c.isAu()) put(it, "REFONTE_MOTIF", str(r.get("refonteMotif")));
            }
            case "modification_statuts_autre" -> {
                put(it, "ARTICLES_MODIFIES", str(r.get("articlesModifies")));
                put(it, "STATUTS_NOUVEAU_LIBELLE", str(r.get("statutsNouveauLibelle")));
            }
            case "agrement_cession" -> {
                // L'agrement (art. 58) ne vise qu'un cessionnaire TIERS : le modele "agree ce
                // dernier en qualite de nouvel associe". Lot L4a (E2) : la donnee est exigee.
                String tiers = SaisieExigee.ouiNon(tc, r.get("cessionnaireTiers"),
                        "CESSIONNAIRE_TIERS", "Cessionnaire tiers \u00e0 la soci\u00e9t\u00e9 (oui ou non)");
                if (!"oui".equals(tiers)) {
                    throw new GenerationRefuseeException(tc,
                            "Agr\u00e9ment d'une cession : le cessionnaire n'est pas un tiers (une cession entre associ\u00e9s n'est pas soumise \u00e0 l'agr\u00e9ment)",
                            List.of(dm("CESSIONNAIRE_TIERS", "Cessionnaire tiers \u00e0 la soci\u00e9t\u00e9 : le mod\u00e8le ne r\u00e9dige l'agr\u00e9ment que pour un tiers")));
                }
                cession(it, r);
            }
            case "agrement_transmission" -> {
                put(it, "CESSION_NOMBRE_PARTS", str(r.get("cessionNbParts")));
                put(it, "TRANSMISSION_ORIGINE", str(r.get("transmissionOrigine")));
                put(it, "CESSION_CESSIONNAIRE_NOM", str(r.get("cessionnaireNom")));
                articles(it, r, c);
            }
            case "nantissement_parts" -> {
                put(it, "NANTISSEMENT_NB_PARTS", str(r.get("nantissementNbParts")));
                put(it, "NANTISSEMENT_CONSTITUANT_NOM", str(r.get("nantissementConstituantNom")));
                put(it, "NANTISSEMENT_CREANCIER_NOM", str(r.get("nantissementCreancierNom")));
                put(it, "NANTISSEMENT_CREANCE_OBJET", str(r.get("nantissementCreanceObjet")));
            }
            case "transformation" -> {
                put(it, "COMMISSAIRE_TRANSFORMATION_EXISTE", SaisieExigee.ouiNon(tc,
                        first0(r.get("commissaireTransformationExiste"),
                                str(r.get("commissaireTransformationNom")).isBlank() ? null : "oui"),
                        "COMMISSAIRE_TRANSFORMATION_EXISTE", "Commissaire \u00e0 la transformation (oui ou non)"));
                put(it, "COMMISSAIRE_TRANSFORMATION_NOM", str(r.get("commissaireTransformationNom")));
                put(it, "FORME_NOUVELLE", str(r.get("transformationForme")));
                put(it, "TRANSFORMATION_ORGANES", str(r.get("transformationOrganes")));
            }
            case "designation_commissaire_transformation" ->
                put(it, "COMMISSAIRE_TRANSFORMATION_NOM", str(r.get("commissaireTransformationNom")));
            case "operation_restructuration" -> {
                put(it, "RESTRUCTURATION_NATURE", natureRestructuration(r.get("restructurationNature")));
                put(it, "RESTRUCTURATION_MODALITES", str(r.get("restructurationModalites")));
            }
            case "cession_parts_pluripersonnelle" -> passageAuVersSarl(it, r, c, boucles, porteuses);
            case "pouvoirs_formalites" -> {
                // SARL AU : mandataire nomme facultatif ("existe"), sinon le porteur d'un original.
                if (c.isAu()) put(it, "POUVOIR_MANDATAIRE_NOM", str(r.get("pouvoirMandataireNom")));
            }
            default -> { /* type refuse en amont (typeSarl / typeAu) */ }
        }
    }

    /** SARL : "L'article $ARTICLES_MODIFIES des statuts est modifie en consequence." (absent du PV de l'AU). */
    private static void articles(Map<String, Object> it, Map<String, Object> r, Ctx c) {
        if (!c.isAu()) put(it, "ARTICLES_MODIFIES", str(r.get("articlesModifies")));
    }

    private static void cession(Map<String, Object> it, Map<String, Object> r) {
        Long nb = toLong(r.get("cessionNbParts"));
        put(it, "CESSION_NOMBRE_PARTS", nb == null ? "" : formatAmount(nb));
        put(it, "CESSION_NOMBRE_PARTS_LETTRES", lettres(nb));
        put(it, "CESSION_PARTS_DE", str(r.get("cessionPartsDe")));
        put(it, "CESSION_PARTS_A", str(r.get("cessionPartsA")));
        put(it, "CESSION_CEDANT_NOM", str(r.get("cedantNom")));
        put(it, "CESSION_CESSIONNAIRE_NOM", str(r.get("cessionnaireNom")));
        putAmount(it, "CESSION_PRIX_TOTAL", r.get("cessionPrix"));
    }

    // ------------------------------------------------------------------
    // SARL AU : affectation du resultat et distributions (lot L5, E5)
    // ------------------------------------------------------------------

    /**
     * PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU, branches "affectation_resultat" (benefice) et
     * "affectation_resultat_deficitaire" (perte, choisie par {@link #typeAu}). La reserve legale
     * (applicable, quotite, plafond, montant) est SAISIE : la loi n 5-96 ne la regle pas en SARL
     * et le corpus ne fixe aucun chiffre (dictionnaire). Le total affecte doit egaler le benefice.
     */
    private static void affectationAu(Map<String, Object> it, Map<String, Object> r, Ctx c,
                                      Map<String, Object> boucles, Map<String, String> porteuses) {
        String tc = c.templateCode();
        putDate(it, "EXERCICE_CLOS_DATE", r.get("exerciceClosDate"));
        Long resultat = toLong(r.get("resultatMontant"));
        put(it, "RESULTAT_NET_CHIFFRES", resultat == null ? "" : formatAmount(resultat));
        put(it, "RESULTAT_NET_LETTRES", lettres(resultat));
        List<Map<String, Object>> saisies = asListOfMaps(r.get("affectations"));
        if ("affectation_resultat_deficitaire".equals(it.get("RESOLUTION_TYPE"))) {
            if (!saisies.isEmpty()) {
                refusIncoherence(tc, "AFFECTATION_RESULTAT", "Affectations",
                        "la perte est affect\u00e9e en totalit\u00e9 au report \u00e0 nouveau par le mod\u00e8le : retirez les lignes d'affectation");
            }
            put(it, "CAPITAUX_PROPRES_SOUS_QUART", SaisieExigee.ouiNon(tc, r.get("capitauxPropresSousQuart"),
                    "CAPITAUX_PROPRES_SOUS_QUART",
                    "Situation nette devenue inf\u00e9rieure au quart du capital social (oui ou non)"));
            return;
        }
        porteuse(tc, porteuses, "AFFECTATION_RESULTAT", "affectation_resultat");
        String reserve = SaisieExigee.ouiNon(tc, r.get("reserveLegaleApplicable"), "RESERVE_LEGALE_APPLICABLE",
                "Dotation \u00e0 la r\u00e9serve l\u00e9gale due au titre de l'exercice (oui ou non)");
        put(it, "RESERVE_LEGALE_APPLICABLE", reserve);
        Long dotation = null;
        if ("oui".equals(reserve)) {
            put(it, "RESERVE_LEGALE_DOTATION_TAUX", str(r.get("reserveLegaleDotationTaux")).trim());
            put(it, "RESERVE_LEGALE_PLAFOND", str(r.get("reserveLegalePlafond")).trim());
            dotation = toLong(r.get("reserveLegaleDotationMontant"));
            put(it, "RESERVE_LEGALE_DOTATION_MONTANT", dotation == null ? "" : formatAmount(dotation));
        }
        List<Map<String, Object>> lignes = new ArrayList<>();
        Long total = "oui".equals(reserve) ? dotation : Long.valueOf(0L);
        for (Map<String, Object> a : saisies) {
            if (a == null || a.isEmpty()) continue;
            String poste = str(a.get("poste")).trim();
            if ("oui".equals(reserve) && norm(poste).contains("reserve legale")) {
                refusIncoherence(tc, "RESERVE_LEGALE_APPLICABLE", "R\u00e9serve l\u00e9gale",
                        "la dotation \u00e0 la r\u00e9serve l\u00e9gale se saisit dans ses propres champs, pas dans la liste des affectations");
            }
            Long montant = toLong(first0(a.get("montant"), a.get("montantChiffres")));
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("AFFECTATION_LIBELLE", poste);
            l.put("AFFECTATION_MONTANT_CHIFFRES", montant == null ? "" : formatAmount(montant));
            l.put("AFFECTATION_MONTANT_LETTRES", lettres(montant));
            lignes.add(l);
            total = (total == null || montant == null) ? null : total + montant;
        }
        if (lignes.isEmpty() && dotation == null) {
            throw new GenerationRefuseeException(tc, "Affectation du b\u00e9n\u00e9fice non indiqu\u00e9e",
                    List.of(dm("AFFECTATION_RESULTAT", "Affectations du b\u00e9n\u00e9fice (poste, montant)")));
        }
        if (total != null && resultat != null && !total.equals(resultat)) {
            refusIncoherence(tc, "AFFECTATION_RESULTAT", "Total des affectations",
                    "affect\u00e9 " + formatAmount(total) + " dirhams pour un b\u00e9n\u00e9fice de " + formatAmount(resultat) + " dirhams");
        }
        boucles.put("AFFECTATION_RESULTAT", lignes);
    }

    /** PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU, branche "distribution_dividendes". */
    private static void dividendesAu(Map<String, Object> it, Map<String, Object> r, Ctx c) {
        String tc = c.templateCode();
        putDate(it, "EXERCICE_CLOS_DATE", r.get("exerciceClosDate"));
        LocalDate clos = toDate(r.get("exerciceClosDate"));
        LocalDate approbation = toDate(r.get("approbationComptesDate"));
        putDate(it, "ASSEMBLEE_APPROBATION_COMPTES_DATE", r.get("approbationComptesDate"));
        if (clos != null && approbation != null && !approbation.isAfter(clos)) {
            refusIncoherence(tc, "ASSEMBLEE_APPROBATION_COMPTES_DATE", "Date d'approbation des comptes",
                    "les comptes de l'exercice clos le " + clos.format(DATE_FR) + " ne peuvent \u00eatre approuv\u00e9s le "
                            + approbation.format(DATE_FR));
        }
        if (approbation != null && c.dateActe() != null && c.dateActe().isBefore(approbation)) {
            refusIncoherence(tc, "ASSEMBLEE_APPROBATION_COMPTES_DATE", "Date d'approbation des comptes",
                    "la distribution du " + c.dateActe().format(DATE_FR) + " pr\u00e9c\u00e8de l'approbation du "
                            + approbation.format(DATE_FR));
        }
        Long benefice = toLong(r.get("beneficeDistribuable"));
        put(it, "BENEFICE_DISTRIBUABLE_CHIFFRES", benefice == null ? "" : formatAmount(benefice));
        put(it, "BENEFICE_DISTRIBUABLE_LETTRES", lettres(benefice));
        Long montant = toLong(r.get("dividendeTotal"));
        put(it, "DISTRIBUTION_MONTANT_CHIFFRES", montant == null ? "" : formatAmount(montant));
        put(it, "DISTRIBUTION_MONTANT_LETTRES", lettres(montant));
        if (montant != null && benefice != null && montant > benefice) {
            refusIncoherence(tc, "DISTRIBUTION_MONTANT_CHIFFRES", "Montant distribu\u00e9",
                    formatAmount(montant) + " dirhams d\u00e9passent le b\u00e9n\u00e9fice distribuable de "
                            + formatAmount(benefice) + " dirhams");
        }
        parPart(it, tc, montant, c, r.get("dividendeParPart"));

        // Article 83 bis : mise en paiement dans les neuf mois de la cloture (date deduite).
        LocalDate limite = clos == null ? null : clos.plusMonths(9);
        put(it, "DIVIDENDES_DATE_LIMITE_PAIEMENT", limite == null ? "" : limite.format(DATE_FR));
        String prolongation = SaisieExigee.ouiNon(tc, r.get("prolongationOrdonnance"),
                "DISTRIBUTION_PROLONGATION_ORDONNANCE",
                "D\u00e9lai de mise en paiement prolong\u00e9 par ordonnance du pr\u00e9sident du tribunal (oui ou non)");
        put(it, "DISTRIBUTION_PROLONGATION_ORDONNANCE", prolongation);
        LocalDate echeance = limite;
        if ("oui".equals(prolongation)) {
            // L'ordonnance est rendue par le president du tribunal de commerce du siege (deduit).
            put(it, "TRIBUNAL_VILLE", tribunalCommerce(c));
            putDate(it, "DISTRIBUTION_PROLONGATION_ORDONNANCE_DATE", r.get("prolongationOrdonnanceDate"));
            putDate(it, "DISTRIBUTION_PROLONGATION_DATE_NOUVELLE", r.get("prolongationDateNouvelle"));
            LocalDate nouvelle = toDate(r.get("prolongationDateNouvelle"));
            if (nouvelle != null && limite != null && !nouvelle.isAfter(limite)) {
                refusIncoherence(tc, "DISTRIBUTION_PROLONGATION_DATE_NOUVELLE", "Date prolong\u00e9e",
                        "le " + nouvelle.format(DATE_FR) + " ne prolonge pas le d\u00e9lai l\u00e9gal du " + limite.format(DATE_FR));
            }
            echeance = nouvelle;
        }
        putDate(it, "DIVIDENDE_DATE_MISE_EN_PAIEMENT", r.get("dateMisePaiement"));
        LocalDate mise = toDate(r.get("dateMisePaiement"));
        if (mise != null && echeance != null && mise.isAfter(echeance)) {
            refusIncoherence(tc, "DIVIDENDE_DATE_MISE_EN_PAIEMENT", "Date de mise en paiement",
                    "le " + mise.format(DATE_FR) + " d\u00e9passe la date limite du " + echeance.format(DATE_FR));
        }
        retenueSource(it, tc, montant, r, c);
    }

    /** PV_DECISIONS_ASSOCIE_UNIQUE_SARL_AU, branche "distribution_reserves" (boucle RESERVES_PRELEVEES). */
    private static void reservesAu(Map<String, Object> it, Map<String, Object> r, Ctx c,
                                   Map<String, Object> boucles, Map<String, String> porteuses) {
        String tc = c.templateCode();
        porteuse(tc, porteuses, "RESERVES_PRELEVEES", "distribution_reserves");
        List<Map<String, Object>> lignes = new ArrayList<>();
        Long total = 0L;
        for (Map<String, Object> p : asListOfMaps(r.get("reservesPrelevees"))) {
            if (p == null || p.isEmpty()) continue;
            String libelle = str(p.get("libelle")).trim();
            if (norm(libelle).contains("reserve legale")) {
                // "La reserve legale, indisponible, n'est pas mise en distribution."
                refusIncoherence(tc, "RESERVE_POSTE_LIBELLE", "Poste de r\u00e9serves pr\u00e9lev\u00e9",
                        "la r\u00e9serve l\u00e9gale est indisponible et ne peut \u00eatre distribu\u00e9e");
            }
            Long prelevement = toLong(p.get("prelevement"));
            Long avant = toLong(p.get("soldeAvant"));
            if (prelevement != null && avant != null && prelevement > avant) {
                refusIncoherence(tc, "RESERVE_POSTE_PRELEVEMENT", "Pr\u00e9l\u00e8vement sur \u00ab " + libelle + " \u00bb",
                        formatAmount(prelevement) + " dirhams d\u00e9passent le solde du poste, " + formatAmount(avant) + " dirhams");
            }
            Long apres = (prelevement == null || avant == null) ? null : avant - prelevement;
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("RESERVE_POSTE_LIBELLE", libelle);
            l.put("RESERVE_POSTE_PRELEVEMENT", prelevement == null ? "" : formatAmount(prelevement));
            l.put("RESERVE_POSTE_SOLDE_AVANT", avant == null ? "" : formatAmount(avant));
            l.put("RESERVE_POSTE_SOLDE_APRES", apres == null ? "" : formatAmount(apres));
            lignes.add(l);
            total = (total == null || prelevement == null) ? null : total + prelevement;
        }
        if (lignes.isEmpty()) {
            throw new GenerationRefuseeException(tc, "Postes de r\u00e9serves pr\u00e9lev\u00e9s non indiqu\u00e9s",
                    List.of(dm("RESERVES_PRELEVEES",
                            "Postes de r\u00e9serves pr\u00e9lev\u00e9s (intitul\u00e9, pr\u00e9l\u00e8vement, solde avant)")));
        }
        boucles.put("RESERVES_PRELEVEES", lignes);
        put(it, "RESERVES_PRELEVEMENT_TOTAL", total == null ? "" : formatAmount(total));
        parPart(it, tc, total, c, r.get("dividendeParPart"));
        putDate(it, "DIVIDENDE_DATE_MISE_EN_PAIEMENT", r.get("dateMisePaiement"));
        retenueSource(it, tc, total, r, c);
    }

    /**
     * "soit $DISTRIBUTION_MONTANT_PAR_PART dirhams par part sociale" : deduit du montant et du
     * nombre de parts (l'associe unique les detient toutes). Un montant qui ne se repartit pas
     * au centime pres est refuse (aucun arrondi imprime comme exact).
     */
    private static void parPart(Map<String, Object> it, String tc, Long montant, Ctx c, Object saisi) {
        if (montant == null || c.nbParts() == null || c.nbParts() <= 0) {
            put(it, "DISTRIBUTION_MONTANT_PAR_PART", "");
            return;
        }
        BigDecimal parPart = BigDecimal.valueOf(montant)
                .divide(BigDecimal.valueOf(c.nbParts()), 6, RoundingMode.HALF_UP).stripTrailingZeros();
        if (parPart.scale() > 2) {
            refusIncoherence(tc, "DISTRIBUTION_MONTANT_PAR_PART", "Montant par part",
                    formatAmount(montant) + " dirhams ne se r\u00e9partissent pas au centime pr\u00e8s entre "
                            + formatAmount(c.nbParts()) + " parts");
        }
        BigDecimal s = toDecimal(saisi);
        if (s != null && s.compareTo(parPart) != 0) {
            refusIncoherence(tc, "DISTRIBUTION_MONTANT_PAR_PART", "Montant par part",
                    "saisi " + formatDecimal(s) + ", calcul\u00e9 " + formatDecimal(parPart));
        }
        put(it, "DISTRIBUTION_MONTANT_PAR_PART", formatDecimal(parPart));
    }

    /**
     * Retenue a la source : taux SAISI (jamais ecrit en dur, dictionnaire), montant deduit
     * (montant distribue x taux, arrondi au centime), exercice d'origine saisi, exoneration
     * exigee. Le modele ne vise l'exoneration que pour un associe unique SOCIETE soumise a l'IS :
     * elle est refusee si l'associe unique n'est pas connu comme personne morale.
     */
    private static void retenueSource(Map<String, Object> it, String tc, Long montant, Map<String, Object> r, Ctx c) {
        Object brut = r.get("retenueSourceTaux");
        BigDecimal taux = toDecimal(brut);
        if (!str(brut).isBlank()
                && (taux == null || taux.signum() <= 0 || taux.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw SaisieExigee.refus(tc, "RETENUE_SOURCE_TAUX",
                    "Taux de la retenue \u00e0 la source (valeur \u00ab " + brut + " \u00bb non reconnue)");
        }
        put(it, "RETENUE_SOURCE_TAUX", taux == null ? "" : formatDecimal(taux) + " %");
        BigDecimal retenue = (taux == null || montant == null) ? null
                : BigDecimal.valueOf(montant).multiply(taux).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        put(it, "RETENUE_SOURCE_MONTANT", retenue == null ? "" : formatDecimal(retenue));
        put(it, "DISTRIBUTION_EXERCICE_ORIGINE", str(r.get("distributionExerciceOrigine")).trim());
        String exoneration = SaisieExigee.ouiNon(tc, r.get("retenueSourceExoneration"),
                "RETENUE_SOURCE_EXONERATION_APPLICABLE",
                "Associ\u00e9 unique soci\u00e9t\u00e9 r\u00e9sidente soumise \u00e0 l'IS, exon\u00e9r\u00e9e de la retenue (oui ou non)");
        if ("oui".equals(exoneration) && !Boolean.TRUE.equals(c.associeUniqueMorale())) {
            refusIncoherence(tc, "RETENUE_SOURCE_EXONERATION_APPLICABLE", "Exon\u00e9ration de la retenue \u00e0 la source",
                    "elle ne vise qu'un associ\u00e9 unique soci\u00e9t\u00e9 soumise \u00e0 l'imp\u00f4t sur les soci\u00e9t\u00e9s");
        }
        put(it, "RETENUE_SOURCE_EXONERATION_APPLICABLE", exoneration);
    }

    /** Tribunal de commerce du registre de la societe (rien deduit hors ville de tribunal de commerce). */
    private static String tribunalCommerce(Ctx c) {
        return "Tribunal de commerce".equals(TribunalDerive.type(c.villeGreffe())) ? c.villeGreffe() : "";
    }

    // ------------------------------------------------------------------
    // Augmentation de capital (une resolution "augmentation_capital", une modalite)
    // ------------------------------------------------------------------

    private static void augmentation(Map<String, Object> it, String type, Map<String, Object> r, Ctx c,
                                     Map<String, Object> boucles, Map<String, String> porteuses) {
        String tc = c.templateCode();
        String modele = c.isAu() ? CORPUS_SARL_AU : CORPUS_SARL;
        // Le modele du cabinet ne sait pas rediger ces cas sans phrase fausse : refus nomme
        // (non resolu, a faire corriger par le cabinet). PV_AG_SARL imprime toujours le droit
        // preferentiel de souscription et "la souscription integrale des parts nouvelles" ; le
        // PV de l'associe unique, "l'associe unique souscrira seul la totalite des parts nouvelles".
        if ("augmentation_capital_incorporation".equals(type)) {
            throw new GenerationRefuseeException(tc,
                    "Augmentation par incorporation de r\u00e9serves : le mod\u00e8le du cabinet (" + modele
                            + ") la r\u00e9dige avec une souscription des parts nouvelles, sans objet pour une incorporation",
                    List.of(dm("AUGMENTATION_MODALITE_TYPE",
                            "Incorporation de r\u00e9serves : r\u00e9daction du mod\u00e8le " + modele + " \u00e0 faire corriger par le cabinet")));
        }
        porteuse(tc, porteuses, "AUGMENTATION_MODALITES", type);
        boolean nature = "augmentation_capital_nature".equals(type);

        Long montant = toLong(r.get("augcapMontantChiffres"));
        put(it, "CAPITAL_AUGMENTATION_CHIFFRES", montant == null ? "" : formatAmount(montant));
        put(it, "CAPITAL_AUGMENTATION_LETTRES", lettres(montant));
        put(it, "CAPITAL_ANCIEN_CHIFFRES", c.capital() == null ? "" : formatAmount(c.capital()));
        Long nouveau = (c.capital() == null || montant == null) ? null : c.capital() + montant;
        coherent(tc, "CAPITAL_NOUVEAU_CHIFFRES", "Nouveau capital", nouveau, toLong(r.get("augcapNouveauCapital")));
        put(it, "CAPITAL_NOUVEAU_CHIFFRES", nouveau == null ? "" : formatAmount(nouveau));

        // Une modalite par resolution : la phrase "selon la modalite suivante".
        put(it, "AUGMENTATION_MODALITES_NOMBRE", "une");
        String modaliteType;
        if (nature) {
            modaliteType = "nature";
        } else {
            String liberation = choix(tc, r.get("augcapLiberationMode"), "AUGCAP_LIBERATION_MODE",
                    "Mode de lib\u00e9ration", ModificationDirecteurMapper::normLiberationMode);
            modaliteType = "compensation".equals(liberation) ? "compensation" : "numeraire";
        }
        Map<String, Object> modalite = new LinkedHashMap<>();
        modalite.put("AUGMENTATION_MODALITE_TYPE", modaliteType);
        modalite.put("AUGMENTATION_MODALITE_MONTANT", montant == null ? "" : formatAmount(montant));
        modalite.put("AUGMENTATION_MODALITE_DESCRIPTION", str(r.get("apportNatureDescription")));
        modalite.put("APPORTEUR_NOM", str(r.get("apporteurNom")));
        modalite.put("INCORPORATION_POSTE", "");
        modalite.put("AUGMENTATION_MODALITE_LIBELLE", "");
        boucles.put("AUGMENTATION_MODALITES", List.of(modalite));

        // Realisation : seule la creation de parts nouvelles se redige sans phrase fausse
        // (l'elevation du nominal imprimerait la souscription de "parts nouvelles" qui n'existent pas).
        String realisation = choix(tc, r.get("augcapMode"), "AUGMENTATION_REALISATION",
                "R\u00e9alisation de l'augmentation (cr\u00e9ation de parts nouvelles ou \u00e9l\u00e9vation du nominal)",
                ModificationDirecteurMapper::normRealisation);
        if (!"creation_parts".equals(realisation)) {
            throw new GenerationRefuseeException(tc,
                    "Augmentation par \u00e9l\u00e9vation du nominal : le mod\u00e8le du cabinet (" + modele
                            + ") imprime la souscription de parts nouvelles qui n'existent pas",
                    List.of(dm("AUGMENTATION_REALISATION",
                            "\u00c9l\u00e9vation du nominal : r\u00e9daction du mod\u00e8le " + modele + " \u00e0 faire corriger par le cabinet")));
        }
        put(it, "AUGMENTATION_REALISATION", realisation);
        Long nbNouvelles = toLong(r.get("augcapNbPartsNouvelles"));
        Long vn = first0(toLong(r.get("valeurNominalePart")), c.vnActuelle()) instanceof Long l ? l : null;
        if (vn != null && nbNouvelles != null && montant != null && vn * nbNouvelles != montant) {
            refusIncoherence(tc, "AUGMENTATION_PARTS_NOUVELLES", "Nombre de parts nouvelles",
                    nbNouvelles + " parts de " + formatAmount(vn) + " dirhams ne font pas "
                            + formatAmount(montant) + " dirhams");
        }
        put(it, "AUGMENTATION_PARTS_NOUVELLES", nbNouvelles == null ? "" : formatAmount(nbNouvelles));
        put(it, "VALEUR_NOMINALE_PART", vn == null ? "" : formatAmount(vn));
        // Numerotation : a la suite des parts existantes (deduite) ; une saisie differente est refusee.
        Long de = (c.nbParts() == null) ? null : c.nbParts() + 1;
        Long a = (c.nbParts() == null || nbNouvelles == null) ? null : c.nbParts() + nbNouvelles;
        coherent(tc, "AUGMENTATION_PARTS_DE", "Num\u00e9ro de la premi\u00e8re part nouvelle", de, toLong(r.get("augcapPartsDe")));
        coherent(tc, "AUGMENTATION_PARTS_A", "Num\u00e9ro de la derni\u00e8re part nouvelle", a, toLong(r.get("augcapPartsA")));
        put(it, "AUGMENTATION_PARTS_DE", de == null ? "" : formatAmount(de));
        put(it, "AUGMENTATION_PARTS_A", a == null ? "" : formatAmount(a));

        put(it, "AUGMENTATION_APPORT_NATURE", nature ? "oui" : "non");
        put(it, "COMMISSAIRE_APPORTS_NOM", nature ? str(r.get("commissaireApportsNom")) : "");
        put(it, "FONDS_COMMERCE", nature ? SaisieExigee.ouiNon(tc, r.get("fondsCommerce"), "FONDS_COMMERCE",
                "Apport d'un fonds de commerce (oui ou non)") : "non");

        String prime = SaisieExigee.ouiNon(tc, r.get("primeEmission"),
                "PRIME_EMISSION_PRESENTE", "Prime d'\u00e9mission (oui ou non)");
        put(it, "PRIME_EMISSION_PRESENTE", prime);
        if ("oui".equals(prime)) {
            Long primePart = toLong(r.get("primeEmissionMontant"));
            putAmount(it, "PRIME_EMISSION_PAR_PART", primePart);
            Long primeTotale = (primePart == null || nbNouvelles == null) ? null : primePart * nbNouvelles;
            coherent(tc, "PRIME_EMISSION_CHIFFRES", "Prime d'\u00e9mission globale", primeTotale, toLong(r.get("primeEmissionTotal")));
            putAmount(it, "PRIME_EMISSION_CHIFFRES", primeTotale);
        }

        if (c.isAu()) {
            // L'associe unique souscrit-il seul ? La donnee pilote la perte du caractere unipersonnel.
            String tiers = SaisieExigee.ouiNon(tc, r.get("souscriptionTiers"),
                    "ETAT_REPARTITION_EFFET_UNIPERSONNALITE",
                    "Parts nouvelles souscrites par une autre personne que l'associ\u00e9 unique (oui ou non)");
            put(it, "ETAT_REPARTITION_EFFET_UNIPERSONNALITE", "oui".equals(tiers) ? "sortie_associe_unique" : "sans_effet");
        } else {
            String dps = SaisieExigee.ouiNon(tc, r.get("dpsSuppression"), "DPS_SUPPRIME",
                    "Suppression du droit pr\u00e9f\u00e9rentiel de souscription (oui ou non)");
            put(it, "DPS_SUPPRIME", dps);
            if ("oui".equals(dps)) {
                List<Map<String, Object>> beneficiaires = buildBeneficiaires(
                        first0(r.get("beneficiairesDps"), r.get("beneficiaires")));
                if (beneficiaires.isEmpty() && nature && !str(r.get("apporteurNom")).isBlank() && nbNouvelles != null) {
                    // Apport en nature : les parts nouvelles reviennent a l'apporteur (deduit).
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("DPS_BENEFICIAIRE_NOM", str(r.get("apporteurNom")));
                    b.put("DPS_BENEFICIAIRE_NOMBRE_PARTS", formatAmount(nbNouvelles));
                    beneficiaires = List.of(b);
                }
                if (beneficiaires.isEmpty()) {
                    throw new GenerationRefuseeException(tc, "B\u00e9n\u00e9ficiaires de la souscription non indiqu\u00e9s",
                            List.of(dm("BENEFICIAIRES_DPS",
                                    "B\u00e9n\u00e9ficiaires de la souscription r\u00e9serv\u00e9e (nom, nombre de parts)")));
                }
                boucles.put("BENEFICIAIRES_DPS", beneficiaires);
            }
            articles(it, r, c);
        }
    }

    // ------------------------------------------------------------------
    // Reduction de capital
    // ------------------------------------------------------------------

    private static void reduction(Map<String, Object> it, Map<String, Object> r, Ctx c) {
        String tc = c.templateCode();
        String motif = choix(tc, r.get("redcapMotif"), "REDUCTION_MOTIF",
                "Motif de la r\u00e9duction du capital", ModificationDirecteurMapper::normReductionMotif);
        put(it, "REDUCTION_MOTIF", motif);
        String modalite = choix(tc, r.get("redcapMode"), "REDUCTION_MODALITE",
                "Mode de r\u00e9duction du capital", ModificationDirecteurMapper::normReductionModalite);
        put(it, "REDUCTION_MODALITE", modalite);
        if (!c.isAu() && "non_pertes".equals(motif) && !"annulation_parts".equals(modalite)) {
            // PV_AG_SARL : la reduction non motivee par des pertes s'y redige "par voie de rachat
            // par la societe de ses propres parts en vue de leur annulation".
            throw new GenerationRefuseeException(tc,
                    "R\u00e9duction non motiv\u00e9e par des pertes : le mod\u00e8le du cabinet la r\u00e9dige par rachat et annulation de parts",
                    List.of(dm("REDUCTION_MODALITE", "Mode de r\u00e9duction : annulation de parts rachet\u00e9es")));
        }

        Long montant = toLong(r.get("redcapMontant"));
        put(it, "CAPITAL_REDUCTION_CHIFFRES", montant == null ? "" : formatAmount(montant));
        put(it, "CAPITAL_REDUCTION_LETTRES", lettres(montant));
        put(it, "CAPITAL_ANCIEN_CHIFFRES", c.capital() == null ? "" : formatAmount(c.capital()));
        Long nouveau = (c.capital() == null || montant == null) ? null : c.capital() - montant;
        coherent(tc, "CAPITAL_NOUVEAU_CHIFFRES", "Nouveau capital", nouveau, toLong(r.get("redcapNouveauCapital")));
        put(it, "CAPITAL_NOUVEAU_CHIFFRES", nouveau == null ? "" : formatAmount(nouveau));

        Long annulees = null;
        if ("annulation_parts".equals(modalite)) {
            Long vn = first0(toLong(r.get("valeurNominalePart")), c.vnActuelle()) instanceof Long l ? l : null;
            annulees = (vn == null || montant == null || vn == 0 || montant % vn != 0) ? null : montant / vn;
            Long apres = (annulees == null || c.nbParts() == null) ? null : c.nbParts() - annulees;
            coherent(tc, "NOMBRE_PARTS_NOUVEAU", "Nombre de parts apr\u00e8s la r\u00e9duction", apres,
                    toLong(r.get("redcapNbPartsNouvelles")));
            put(it, "VALEUR_NOMINALE_PART", vn == null ? "" : formatAmount(vn));
            put(it, "RETRAIT_NOMBRE_PARTS", annulees == null ? "" : formatAmount(annulees));
            put(it, "NOMBRE_PARTS_NOUVEAU", apres == null ? "" : formatAmount(apres));
        } else {
            put(it, "VALEUR_NOMINALE_PART_ANCIENNE", c.vnActuelle() == null ? "" : formatAmount(c.vnActuelle()));
            Long vnNouvelle = (nouveau == null || c.nbParts() == null || c.nbParts() == 0 || nouveau % c.nbParts() != 0)
                    ? null : nouveau / c.nbParts();
            coherent(tc, "VALEUR_NOMINALE_PART", "Nouvelle valeur nominale", vnNouvelle, toLong(r.get("nouvelleValeurNominale")));
            put(it, "VALEUR_NOMINALE_PART", vnNouvelle == null ? "" : formatAmount(vnNouvelle));
        }

        Long prix = toLong(r.get("rachatPrixPart"));
        if (c.isAu()) {
            // "\u25c7 SI : $RETRAIT_PRIX_PART existe" : parts rachetees a l'associe unique.
            putAmount(it, "RETRAIT_PRIX_PART", prix);
            if (prix != null) put(it, "RETRAIT_PRIME_RACHAT_IMPUTATION", str(r.get("primeRachatImputation")));
            put(it, "REDUCTION_SUIVIE_AUGMENTATION", SaisieExigee.ouiNon(tc, r.get("reductionSuivieAugmentation"),
                    "REDUCTION_SUIVIE_AUGMENTATION",
                    "R\u00e9duction indissociable d'une augmentation de capital d\u00e9cid\u00e9e ensuite (oui ou non)"));
        } else {
            if ("non_pertes".equals(motif)) {
                putAmount(it, "RETRAIT_PRIX_PART", prix);
                putDate(it, "RACHAT_DATE_LIMITE", r.get("rachatDateLimite"));
            }
            // Prime de rachat : difference entre le prix et le nominal des parts annulees (deduite).
            Long vn = first0(toLong(r.get("valeurNominalePart")), c.vnActuelle()) instanceof Long l ? l : null;
            boolean primeRachat = "non_pertes".equals(motif) && prix != null && vn != null && prix > vn && annulees != null;
            put(it, "RETRAIT_PRIME_RACHAT", primeRachat ? "oui" : "non");
            if (primeRachat) {
                put(it, "RETRAIT_PRIME_RACHAT_MONTANT", formatAmount((prix - vn) * annulees));
                put(it, "RETRAIT_PRIME_RACHAT_IMPUTATION", str(r.get("primeRachatImputation")));
            }
            articles(it, r, c);
        }
    }

    // ------------------------------------------------------------------
    // SARL AU -> SARL : cession partielle des parts de l'associe unique
    // ------------------------------------------------------------------

    private static void passageAuVersSarl(Map<String, Object> it, Map<String, Object> r, Ctx c,
                                          Map<String, Object> boucles, Map<String, String> porteuses) {
        String tc = c.templateCode();
        porteuse(tc, porteuses, "REPARTITION_NOUVELLE", "cession_parts_pluripersonnelle");
        Long nb = toLong(r.get("cessionNbParts"));
        Long prix = toLong(r.get("cessionPrix"));
        putDate(it, "CESSION_DATE", r.get("cessionDate"));
        put(it, "CESSION_NOMBRE_PARTS", nb == null ? "" : formatAmount(nb));
        put(it, "CESSION_NOMBRE_PARTS_LETTRES", lettres(nb));
        put(it, "CESSION_CESSIONNAIRE_NOM", str(r.get("cessionnaireNom")));
        put(it, "CESSION_PRIX_TOTAL", prix == null ? "" : formatAmount(prix));
        put(it, "CESSION_PRIX_TOTAL_LETTRES", lettres(prix));
        // Prix par part deduit du prix global (seulement s'il tombe juste).
        Long prixPart = (prix == null || nb == null || nb == 0 || prix % nb != 0) ? null : prix / nb;
        put(it, "CESSION_PRIX_PART", prixPart == null ? "" : formatAmount(prixPart));
        put(it, "CESSION_PRIX_PART_LETTRES", lettres(prixPart));

        Long de = toLong(r.get("cessionPartsDe"));
        Long a = toLong(r.get("cessionPartsA"));
        put(it, "CESSION_PARTS_DE", de == null ? "" : formatAmount(de));
        put(it, "CESSION_PARTS_A", a == null ? "" : formatAmount(a));
        if (de != null && a != null && nb != null && a - de + 1 != nb) {
            refusIncoherence(tc, "CESSION_PARTS_A", "Num\u00e9ros des parts c\u00e9d\u00e9es",
                    "les parts n\u00b0 " + de + " \u00e0 " + a + " ne sont pas " + nb + " parts");
        }
        // Nouvelle repartition : l'associe unique conserve les parts hors du bloc cede.
        Long total = c.nbParts();
        List<Map<String, Object>> repartition = new ArrayList<>();
        if (de != null && a != null && nb != null && total != null) {
            Map<String, Object> cedant = null;
            if (de == 1 && a < total) {
                cedant = ligneRepartition(c.associeUniqueNom(), total - nb, a + 1, total);
            } else if (a.equals(total) && de > 1) {
                cedant = ligneRepartition(c.associeUniqueNom(), total - nb, 1, de - 1);
            }
            if (cedant == null) {
                refusIncoherence(tc, "CESSION_PARTS_DE", "Num\u00e9ros des parts c\u00e9d\u00e9es",
                        "le bloc c\u00e9d\u00e9 doit commencer \u00e0 la premi\u00e8re part ou finir \u00e0 la derni\u00e8re (n\u00b0 " + total + ")");
            }
            repartition.add(cedant);
            repartition.add(ligneRepartition(str(r.get("cessionnaireNom")), nb, de, a));
        }
        boucles.put("REPARTITION_NOUVELLE", repartition);
    }

    private static Map<String, Object> ligneRepartition(String nom, long parts, long de, long a) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("REPARTITION_ASSOCIE_NOM", nom == null ? "" : nom);
        l.put("REPARTITION_NOMBRE_PARTS", formatAmount(parts));
        l.put("REPARTITION_PARTS_DE", formatAmount(de));
        l.put("REPARTITION_PARTS_A", formatAmount(a));
        return l;
    }

    // ==================================================================
    // Variables globales du PV de l'associe unique
    // ==================================================================

    private static void globalesAssocieUnique(Map<String, Object> v, Map<String, Object> safe,
                                              Map<String, Object> societe) {
        // Le parcours de modification vise une societe en activite : une societe en liquidation
        // releve du parcours de dissolution et liquidation (decision L4d, a revoir).
        put(v, "SOCIETE_EN_LIQUIDATION", "non");
        // "La gerance est exercee par $GERANT_NOM, $GERANT_QUALITE." : les gerants en fonction.
        List<String> noms = new ArrayList<>();
        for (Map<String, Object> g : asListOfMaps(safe.get("gerants"))) {
            String nom = String.join(" ", List.of(normCivilite(g.get("civilite")), str(g.get("prenom")),
                    str(g.get("nom")))).trim().replaceAll("\\s+", " ");
            if (!nom.isBlank() && !noms.contains(nom)) noms.add(nom);
        }
        if (!noms.isEmpty()) {
            String liste = noms.size() == 1 ? noms.get(0)
                    : String.join(", ", noms.subList(0, noms.size() - 1)) + " et " + noms.get(noms.size() - 1);
            put(v, "GERANT_NOM", liste);
            put(v, "GERANT_QUALITE", noms.size() == 1 ? "g\u00e9rant" : "cog\u00e9rants");
        } else {
            put(v, "GERANT_QUALITE", "");
        }
        // Registre des decisions de l'associe unique (art. 76 et 112) : numero et feuillet saisis.
        Map<String, Object> registre = asMap(safe.get("registre"));
        put(v, "REGISTRE_PV_NUMERO", first(str(registre.get("numero")), str(safe.get("registrePvNumero"))));
        put(v, "REGISTRE_PV_FEUILLET", first(str(registre.get("feuillet")), str(safe.get("registrePvFeuillet"))));
    }

    /** Associe unique personne morale ? (null : inconnu). */
    private static Boolean associeUniqueMorale(Map<String, Object> safe) {
        List<Map<String, Object>> associes = asListOfMaps(safe.get("associes"));
        Map<String, Object> a = associes.isEmpty() ? asMap(safe.get("associeUnique")) : associes.get(0);
        String type = norm(a.get("typePersonne"));
        if (type.startsWith("moral")) return true;
        if (type.startsWith("physi")) return false;
        if (!str(a.get("denomination")).isBlank()) return true;
        if (!str(a.get("prenom")).isBlank() || !str(a.get("nom")).isBlank()) return false;
        return null;
    }

    private static String associeUniqueNom(Map<String, Object> safe) {
        List<Map<String, Object>> associes = asListOfMaps(safe.get("associes"));
        Map<String, Object> a = associes.isEmpty() ? asMap(safe.get("associeUnique")) : associes.get(0);
        String denomination = str(a.get("denomination"));
        if (!denomination.isBlank()) return denomination;
        return String.join(" ", List.of(normCivilite(a.get("civilite")), str(a.get("prenom")), str(a.get("nom"))))
                .trim().replaceAll("\\s+", " ");
    }

    // ==================================================================
    // Boucles imbriquees
    // ==================================================================

    /** Une boucle imbriquee n'a qu'une occurrence par proces-verbal : une seconde porteuse est refusee. */
    private static void porteuse(String templateCode, Map<String, String> porteuses, String boucle, String type) {
        String deja = porteuses.putIfAbsent(boucle, type);
        if (deja != null) {
            throw new GenerationRefuseeException(templateCode,
                    "Une seule r\u00e9solution \u00ab " + defaultObjet(type) + " \u00bb par proc\u00e8s-verbal : regroupez-les",
                    List.of(dm(boucle, "Deux r\u00e9solutions portent la liste " + boucle
                            + " (" + defaultObjet(deja) + ", " + defaultObjet(type) + ")")));
        }
    }

    private static List<Map<String, Object>> buildAffectations(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> a : asListOfMaps(raw)) {
            if (a == null || a.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("AFFECTATION_POSTE", str(a.get("poste")));
            it.put("AFFECTATION_MONTANT", amount(first0(a.get("montant"), a.get("montantChiffres"))));
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> buildGerants(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> g : asListOfMaps(raw)) {
            if (g == null || g.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("GERANT_CIVILITE", normCivilite(g.get("civilite")));
            it.put("GERANT_PRENOM", str(g.get("prenom")));
            it.put("GERANT_NOM", str(g.get("nom")));
            it.put("GERANT_NATIONALITE", str(g.get("nationalite"))); // L4a (E2) : plus de "marocaine"
            it.put("GERANT_DATE_NAISSANCE", dateStr(g.get("dateNaissance")));
            it.put("GERANT_ADRESSE", str(g.get("adresse")));
            it.put("GERANT_PIECE_TYPE", str(g.get("pieceType"))); // L3 (D6) : plus de "CIN" par defaut
            it.put("GERANT_PIECE_NUMERO", first(str(g.get("pieceNumero")), str(g.get("cin"))));
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> buildBeneficiaires(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> b : asListOfMaps(raw)) {
            if (b == null || b.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("DPS_BENEFICIAIRE_NOM", str(b.get("nom")));
            Long parts = toLong(first0(b.get("nbParts"), b.get("nombreParts")));
            it.put("DPS_BENEFICIAIRE_NOMBRE_PARTS", parts == null ? "" : formatAmount(parts));
            out.add(it);
        }
        return out;
    }

    private static List<Map<String, Object>> buildConventions(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> cv : asListOfMaps(raw)) {
            if (cv == null || cv.isEmpty()) continue;
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("CONVENTION_DATE", dateStr(cv.get("date")));
            it.put("CONVENTION_PARTIE_INTERESSEE", str(cv.get("partieInteressee")));
            it.put("CONVENTION_OBJET", str(cv.get("objet")));
            out.add(it);
        }
        return out;
    }

    // ==================================================================
    // Objets par defaut ("PREMIERE RESOLUTION -- <objet>")
    // ==================================================================

    private static String defaultObjet(String type) {
        return switch (type) {
            case "approbation_comptes" -> "Approbation des comptes de l'exercice";
            case "affectation_resultat" -> "Affectation du r\u00e9sultat";
            case "distribution_dividendes" -> "Distribution de dividendes";
            case "distribution_reserves" -> "Distribution de r\u00e9serves";
            case "acompte_dividendes" -> "Distribution d'un acompte sur dividendes";
            case "nomination_gerant" -> "Nomination d'un g\u00e9rant";
            case "renouvellement_gerant" -> "Renouvellement du mandat de g\u00e9rant";
            case "revocation_gerant" -> "R\u00e9vocation d'un g\u00e9rant";
            case "remuneration_gerant" -> "R\u00e9mun\u00e9ration de la g\u00e9rance";
            case "conventions_reglementees" -> "Approbation des conventions r\u00e9glement\u00e9es";
            case "commissaire_comptes" -> "Commissaire aux comptes";
            case "ratification_actes_formation" -> "Ratification des actes de la p\u00e9riode de formation";
            case "autorisation_gerance" -> "Autorisation donn\u00e9e \u00e0 la g\u00e9rance";
            case "augmentation_capital_numeraire" -> "Augmentation de capital en num\u00e9raire";
            case "augmentation_capital_nature" -> "Augmentation de capital par apport en nature";
            case "augmentation_capital_incorporation" -> "Augmentation de capital par incorporation de r\u00e9serves";
            case "reduction_capital" -> "R\u00e9duction de capital";
            case "modification_denomination" -> "Modification de la d\u00e9nomination sociale";
            case "modification_objet" -> "Modification de l'objet social";
            case "transfert_siege" -> "Transfert du si\u00e8ge social";
            case "prorogation_duree" -> "Prorogation de la dur\u00e9e de la soci\u00e9t\u00e9";
            case "modification_exercice" -> "Modification de la date de cl\u00f4ture de l'exercice";
            case "mise_harmonie_statuts" -> "Mise en harmonie des statuts";
            case "modification_statuts_autre" -> "Modification statutaire";
            case "agrement_cession" -> "Agr\u00e9ment d'une cession de parts sociales";
            case "agrement_transmission" -> "Agr\u00e9ment d'une transmission de parts sociales";
            case "nantissement_parts" -> "Consentement \u00e0 un nantissement de parts sociales";
            case "transformation" -> "Transformation de la soci\u00e9t\u00e9";
            case "designation_commissaire_transformation" -> "D\u00e9signation du commissaire \u00e0 la transformation";
            case "capitaux_propres_art86" -> "Capitaux propres inf\u00e9rieurs au quart du capital (art. 86)";
            case "operation_restructuration" -> "Op\u00e9ration de restructuration";
            case "cession_parts_pluripersonnelle" -> "Cession de parts et passage en SARL pluripersonnelle";
            case "pouvoirs_formalites" -> "Pouvoirs pour les formalit\u00e9s";
            default -> "R\u00e9solution";
        };
    }

    // ==================================================================
    // Normalisations (valeurs EXACTES comparees par les conditions du modele)
    // ==================================================================

    /** Normalise le type de resolution en snake_case (tolere accents / espaces / casse). */
    static String normType(Object raw) {
        return norm(raw).replace(' ', '_').replace('-', '_').replaceAll("_+", "_");
    }

    private static String normResultatSens(Object raw) {
        String s = norm(raw);
        if (s.startsWith("perte") || s.startsWith("defic") || s.startsWith("mali")) return "perte";
        if (s.startsWith("benef") || s.startsWith("profit")) return "b\u00e9n\u00e9fice";
        return null;
    }

    /** "nomination" / "renouvellement" / "fin de mandat". */
    private static String normCacAction(Object raw) {
        String s = norm(raw);
        if (s.startsWith("renouv")) return "renouvellement";
        if (s.startsWith("fin") || s.contains("cessation")) return "fin de mandat";
        if (s.startsWith("nomin")) return "nomination";
        return null;
    }

    /** "creation_parts" / "elevation_valeur" (valeurs de $AUGMENTATION_REALISATION). */
    private static String normRealisation(Object raw) {
        String s = norm(raw);
        if (s.contains("elevation") || s.contains("nominal")) return "elevation_valeur";
        if (s.contains("creation") || s.contains("parts nouvelles") || s.equals("creation_parts")) return "creation_parts";
        return null;
    }

    /** "numeraire" / "compensation". */
    private static String normLiberationMode(Object raw) {
        String s = norm(raw);
        if (s.contains("compensation") || s.contains("creance")) return "compensation";
        if (s.contains("numeraire")) return "numeraire";
        return null;
    }

    /** "pertes" / "non_pertes" (valeurs de $REDUCTION_MOTIF). */
    private static String normReductionMotif(Object raw) {
        String s = norm(raw);
        if (s.contains("non") || s.contains("rachat")) return "non_pertes";
        if (s.contains("perte")) return "pertes";
        return null;
    }

    /** "annulation_parts" / "diminution_valeur" (valeurs de $REDUCTION_MODALITE). */
    private static String normReductionModalite(Object raw) {
        String s = norm(raw);
        if (s.contains("annul") || s.contains("rachat") || s.contains("nombre")) return "annulation_parts";
        if (s.contains("diminution") || s.contains("nominal") || s.contains("valeur")) return "diminution_valeur";
        return null;
    }

    /** "extension" / "suppression" / "modification" (valeurs de $OBJET_MODIFICATION_SENS). */
    private static String normObjetSens(Object raw) {
        String s = norm(raw);
        if (s.startsWith("exten") || s.contains("ajout")) return "extension";
        if (s.startsWith("suppr") || s.contains("retranch")) return "suppression";
        if (s.startsWith("modif") || s.contains("remplac") || s.contains("changement")) return "modification";
        return null;
    }

    /**
     * Nature de la restructuration, imprimee apres "connaissance prise du projet de" : l'apport
     * partiel d'actif prend son article ("du projet de l'apport partiel d'actif") ; les valeurs
     * du dictionnaire ("fusion", "scission") s'impriment telles quelles.
     */
    private static String natureRestructuration(Object raw) {
        String s = norm(raw);
        if (s.startsWith("fusion")) return "fusion";
        if (s.startsWith("scission")) return "scission";
        if (s.contains("apport")) return "l'apport partiel d'actif";
        return str(raw);
    }

    /** Lot L4a (E2) : un choix vide ou illisible ne prend jamais la valeur par defaut d'une normalisation. */
    private static String choix(String templateCode, Object raw, String variable, String libelle,
                                java.util.function.Function<Object, String> norm) {
        if (raw == null || String.valueOf(raw).isBlank()) throw SaisieExigee.refus(templateCode, variable, libelle);
        String v = norm.apply(raw);
        if (v == null) throw SaisieExigee.refus(templateCode, variable, libelle + " (valeur \u00ab " + raw + " \u00bb non reconnue)");
        return v;
    }

    /** Une valeur saisie qui contredit le calcul est refusee (jamais imprimee). */
    private static void coherent(String templateCode, String variable, String libelle, Long calcule, Long saisi) {
        if (calcule != null && saisi != null && !calcule.equals(saisi)) {
            refusIncoherence(templateCode, variable, libelle,
                    "saisi " + formatAmount(saisi) + ", calcul\u00e9 " + formatAmount(calcule));
        }
    }

    private static void refusIncoherence(String templateCode, String variable, String libelle, String detail) {
        throw new GenerationRefuseeException(templateCode, libelle + " incoh\u00e9rent : " + detail,
                List.of(new DonneeManquante(variable, libelle + " (" + detail + ")", null)));
    }

    private static DonneeManquante dm(String variable, String libelle) {
        return new DonneeManquante(variable, libelle, null);
    }

    private static String ordinalF(int n) {
        return (n >= 1 && n < ORDINALES_F.length) ? ORDINALES_F[n] : (n + "E");
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

    // ==================================================================
    // Helpers (coercion / format) -- alignes sur SeancePvVarsBuilder
    // ==================================================================

    private static void put(Map<String, Object> v, String key, String value) {
        v.put(key, value == null ? "" : value);
    }

    private static void putAmount(Map<String, Object> v, String key, Object raw) {
        v.put(key, amount(raw));
    }

    private static void putDate(Map<String, Object> v, String key, Object raw) {
        v.put(key, dateStr(raw));
    }

    /** Montant en chiffres formate (separateur de milliers), "" si absent. */
    private static String amount(Object raw) {
        Long n = toLong(raw);
        return n == null ? "" : formatAmount(n);
    }

    /** Montant en toutes lettres, "" si absent. */
    private static String lettres(Object raw) {
        Long n = toLong(raw);
        return n == null ? "" : FrenchNumberToLetters.numberToLetters(n);
    }

    private static String formatAmount(long n) {
        return String.format(Locale.FRANCE, "%,d", n);
    }

    /** Montant decimal : entier sans decimales, sinon deux decimales ("3 712,50"). */
    private static String formatDecimal(BigDecimal d) {
        BigDecimal s = d.stripTrailingZeros();
        if (s.scale() <= 0) return formatAmount(s.longValueExact());
        return String.format(Locale.FRANCE, "%,.2f", d.setScale(2, RoundingMode.HALF_UP));
    }

    /** "10", "11,25", "11.25", "10 %" : nombre ; null si absent ou illisible. */
    private static BigDecimal toDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        String s = String.valueOf(o).replaceAll("[\\s\\u00A0\\u202F%]", "").replace(',', '.');
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return null;
        }
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
