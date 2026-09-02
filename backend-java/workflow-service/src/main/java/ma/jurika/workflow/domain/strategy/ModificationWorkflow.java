package ma.jurika.workflow.domain.strategy;

import ma.jurika.workflow.domain.model.WorkflowType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ma.jurika.workflow.domain.strategy.WorkflowSteps.asList;
import static ma.jurika.workflow.domain.strategy.WorkflowSteps.isTrue;

/**
 * Workflow MODIFICATION (5 etapes) — voie directeur unifiee (spec 2026-08-11).
 *
 * <p>Etapes cibles :
 * <ol>
 *   <li><b>Selection</b> — societe (dossier), decisions officielles (resolutionType),
 *       type d'assemblee, date du PV, convocation optionnelle (regle des 16 jours).</li>
 *   <li><b>Saisie</b> — nouvelles valeurs par resolution (editeur directeur).</li>
 *   <li><b>Generation</b> — statuts refondus v+1, PV de modification, optionnels.</li>
 *   <li><b>Pieces jointes</b> — depot des versions legalisees (OPTIONNELLE, non bloquante).</li>
 *   <li><b>Synthese / Finalisation</b> — recap + validation finale (PV + statuts).</li>
 * </ol>
 *
 * <p>Selection <b>100% voie directeur</b> : chaque decision porte un
 * {@code resolutionType} snake_case du catalogue officiel
 * ({@code officialModificationDecisions.ts} / {@code ModificationDirecteurMapper}).
 * L'ancienne selection par {@code typeId} UPPERCASE (set {@code TYPES}) a ete
 * <b>retiree</b> (reliquat legacy mort) : elle provoquait un 400
 * « Type de modification non reconnu » quand un {@code resolutionType} snake_case
 * y tombait.
 *
 * <p>Applique : RG-M02/M06 (coherence decisionType/forme), RG-M10 (CAC apport en
 * nature), RG-M11 (cession pluripersonnelle SARL_AU -&gt; SARL), RG-M13/M14 (JAL),
 * RG-M19 (versioning statuts). Les faits dossier (forme, capital, ICE) sont injectes
 * par {@code WorkflowUseCases.executeStep} dans {@code ctx.existingData().get("dossier")}.
 */
@Component
public class ModificationWorkflow extends AbstractWorkflow {

    /**
     * Nombre de jours minimum entre la convocation et la tenue de l'assemblee (RG-M — 16 jours).
     * Constante partagee : voir {@link WorkflowSteps#CONVOCATION_DELAI_JOURS} (reutilisee par le
     * workflow DISSOLUTION).
     */
    public static final int CONVOCATION_DELAI_JOURS = WorkflowSteps.CONVOCATION_DELAI_JOURS;

    /**
     * Types de resolutions geres par le moteur PV directeur — miroir du catalogue
     * front {@code officialModificationDecisions.RESOLUTION_TYPES} et de
     * {@code ModificationDirecteurMapper} / {@code RES_SPECS}.
     */
    public static final Set<String> RESOLUTION_TYPES = Set.of(
            "approbation_comptes", "affectation_resultat", "distribution_dividendes",
            "distribution_reserves", "acompte_dividendes",
            "nomination_gerant", "renouvellement_gerant", "revocation_gerant", "remuneration_gerant",
            "conventions_reglementees", "commissaire_comptes",
            "ratification_actes_formation", "autorisation_gerance", "pouvoirs_formalites",
            "augmentation_capital_numeraire", "augmentation_capital_nature",
            "augmentation_capital_incorporation", "reduction_capital",
            "modification_denomination", "modification_objet", "transfert_siege",
            "prorogation_duree", "modification_exercice", "mise_harmonie_statuts",
            "modification_statuts_autre",
            "agrement_cession", "agrement_transmission", "nantissement_parts",
            "cession_parts_pluripersonnelle",
            "transformation", "designation_commissaire_transformation",
            "operation_restructuration", "capitaux_propres_art86"
    );

    /** RG-M14 : resolutionTypes declenchant la publication au JAL. */
    public static final Set<String> JAL_TRIGGERS_RT = Set.of(
            "transfert_siege", "transformation",
            "augmentation_capital_numeraire", "augmentation_capital_nature",
            "augmentation_capital_incorporation", "reduction_capital",
            "agrement_cession", "agrement_transmission", "cession_parts_pluripersonnelle",
            "operation_restructuration", "modification_denomination", "modification_objet",
            "prorogation_duree"
    );

    /** RG-M10 : resolutionType imposant un commissaire aux apports. */
    public static final Set<String> CAC_TRIGGERS_RT = Set.of("augmentation_capital_nature");

    /** RG-M11 : resolutionType forcant le passage SARL_AU -> SARL. */
    public static final Set<String> FORME_CHANGE_TRIGGERS_RT = Set.of(
            "cession_parts_pluripersonnelle"
    );

    /** decisionType admis (contrat backend). Le front expose Ordinaire/Extraordinaire
     *  et mappe : SARL ordinaire→AGO, extraordinaire→AGE ; SARL_AU→AU. */
    public static final Set<String> DECISION_TYPES = Set.of(
            "AU", "AGE", "AGO", "CA", "PV"
    );

    @Override
    public WorkflowType type() {
        return WorkflowType.MODIFICATION;
    }

    @Override
    protected StepResult doExecuteStep(StepContext ctx) {
        return switch (ctx.step()) {
            case 1 -> stepChoixTypes(ctx);
            case 2 -> stepSaisie(ctx);
            case 3 -> stepGenerationDocuments(ctx);
            case 4 -> stepPiecesJointes(ctx);
            case 5 -> stepFinalisation(ctx);
            default -> StepResult.blocked("Etape inconnue : " + ctx.step());
        };
    }

    // ------------------------------------------------------------------
    //  Step 1 : selection dossier + decisions (resolutionType) + convocation
    // ------------------------------------------------------------------
    private StepResult stepChoixTypes(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        // La societe a modifier est explicite (choisie a l'etape 1).
        String dossierIdStr = strOrNull(p.get("dossierId"));
        if (dossierIdStr == null) {
            return StepResult.blocked(
                    "Selectionnez la societe a modifier avant de continuer.");
        }
        java.util.UUID dossierId;
        try {
            dossierId = java.util.UUID.fromString(dossierIdStr);
        } catch (IllegalArgumentException ex) {
            return StepResult.blocked(
                    "Identifiant de societe invalide. Selectionnez la societe dans la liste.");
        }
        // Defense-in-depth : WorkflowUseCases a deja recharge les faits du dossier
        // choisi dans existingData.dossier (loadDossierFactsById).
        Map<String, Object> dossierFacts = readStep(ctx, "dossier");
        if (dossierFacts.isEmpty()) {
            return StepResult.blocked(
                    "La societe selectionnee est introuvable dans votre workspace.");
        }
        Object factsId = dossierFacts.get("dossierId");
        if (factsId != null && !dossierIdStr.equalsIgnoreCase(factsId.toString())) {
            return StepResult.blocked(
                    "Incoherence dossier : verifiez la societe selectionnee.");
        }

        // RG-M02 : decisionType obligatoire + format
        String decisionType = strOrNull(p.get("decisionType"));
        if (decisionType == null || !DECISION_TYPES.contains(decisionType)) {
            return StepResult.blocked(
                    "Type d'assemblee invalide. Choisissez une decision ordinaire ou "
                            + "extraordinaire (l'application la traduit en AGO/AGE ou en "
                            + "decision de l'associe unique selon la forme).");
        }
        // RG-M02 : date PV obligatoire + coherente
        String datePV = strOrNull(p.get("datePV"));
        if (datePV == null) {
            return StepResult.blocked("La date du PV ou de la decision est obligatoire.");
        }
        LocalDate parsedDate = parseDateOrNull(datePV);
        if (parsedDate == null) {
            return StepResult.blocked(
                    "La date du PV est invalide. Format attendu : AAAA-MM-JJ.");
        }
        if (parsedDate.isAfter(LocalDate.now().plusDays(30))) {
            return StepResult.blocked(
                    "La date du PV ne peut pas etre dans le futur (> 30 jours).");
        }

        // RG-M06 : coherence decisionType vs formeJuridique du dossier (best-effort)
        String formeJuridique = readDossierForme(ctx);
        if (formeJuridique != null) {
            if ("SARL_AU".equals(formeJuridique) && !"AU".equals(decisionType)) {
                return StepResult.blocked(
                        "La societe est une SARL a associe unique : les decisions sont "
                                + "prises par l'associe unique (choisissez « decision de "
                                + "l'associe unique »).");
            }
            if ("SARL".equals(formeJuridique) && "AU".equals(decisionType)) {
                return StepResult.blocked(
                        "La societe est une SARL pluri-associes : utilisez une assemblee "
                                + "generale (AGO/AGE) et non une decision de l'associe unique.");
            }
        }
        boolean isAU = "SARL_AU".equals(formeJuridique);

        // Convocation OPTIONNELLE — regle DURE des 16 jours (RG-M).
        StepResult convocationError = validateConvocation(p, parsedDate);
        if (convocationError != null) return convocationError;

        // ── Selection (voie directeur unifiee) ────────────────────────────
        // Prefere `selectedDecisions` (id + resolutionType) ; retombe sur
        // `selectedTypes` = liste de resolutionTypes snake_case (le front envoie
        // les deux). Plus AUCUN chemin legacy UPPERCASE (retire — cause du 400).
        List<Map<String, Object>> decisions = new ArrayList<>();
        List<String> resolutionTypes = new ArrayList<>();
        Set<String> seenRt = new LinkedHashSet<>();

        List<?> decisionsRaw = asList(p, "selectedDecisions");
        if (!decisionsRaw.isEmpty()) {
            for (Object o : decisionsRaw) {
                if (!(o instanceof Map<?, ?> dm)) {
                    return StepResult.blocked(
                            "Format de selection invalide. Rechargez la page et reessayez.");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> d = (Map<String, Object>) dm;
                String rt = strOrNull(d.get("resolutionType"));
                if (rt == null || !RESOLUTION_TYPES.contains(rt)) {
                    return StepResult.blocked(
                            "Type de resolution non reconnu : « " + rt
                                    + " ». Verifiez la selection des modifications.");
                }
                decisions.add(d);
                if (seenRt.add(rt)) resolutionTypes.add(rt);
            }
        } else {
            // Fallback : selectedTypes = resolutionTypes snake_case directs.
            List<?> selectedRaw = asList(p, "selectedTypes");
            for (Object t : selectedRaw) {
                String rt = strOrNull(t);
                if (rt == null) continue;
                if (!RESOLUTION_TYPES.contains(rt)) {
                    return StepResult.blocked(
                            "Type de resolution non reconnu : « " + rt
                                    + " ». Verifiez la selection des modifications.");
                }
                if (seenRt.add(rt)) resolutionTypes.add(rt);
            }
        }
        if (resolutionTypes.isEmpty()) {
            return StepResult.blocked(
                    "Selectionnez au moins une modification a apporter.");
        }

        // Indicateurs RG derives des resolutionTypes.
        Set<String> typesJal = new LinkedHashSet<>();
        Set<String> typesCac = new LinkedHashSet<>();
        boolean formeChangeRequis = false;
        List<Map<String, Object>> articles = new ArrayList<>();
        for (String rt : resolutionTypes) {
            articles.add(Map.of("type", rt, "articles", articlesForResolution(rt)));
            if (JAL_TRIGGERS_RT.contains(rt)) typesJal.add(rt);
            if (CAC_TRIGGERS_RT.contains(rt)) typesCac.add(rt);
            if (FORME_CHANGE_TRIGGERS_RT.contains(rt) && isAU) formeChangeRequis = true;
        }

        Map<String, Object> out = new HashMap<>();
        out.put("dossierId", dossierId.toString());
        out.put("decisionType", decisionType);
        out.put("datePV", datePV);
        if (!decisions.isEmpty()) out.put("selectedDecisions", decisions);
        // selectedTypes = resolutionTypes (vocabulaire directeur, consomme par step 2/3).
        out.put("selectedTypes", resolutionTypes);
        // Convocation persistee telle quelle (date/heure/associes) pour la synthese.
        Object convocation = p.get("convocation");
        if (convocation != null) out.put("convocation", convocation);
        out.put("articlesImpactes", articles);
        out.put("jalRequis", !typesJal.isEmpty());
        out.put("typesNecessitantJal", new ArrayList<>(typesJal));
        out.put("cacRequis", !typesCac.isEmpty());
        out.put("typesNecessitantCac", new ArrayList<>(typesCac));
        out.put("formeChangeRequis", formeChangeRequis);
        if (formeChangeRequis) {
            out.put("formeAvant", "SARL_AU");
            out.put("formeApres", "SARL");
            out.put("formeChangeRaison",
                    "RG-M11 : la cession de parts d'une SARL AU entraine "
                            + "l'adaptation des statuts en SARL pluri-associes.");
        }
        if (formeJuridique != null) out.put("formeJuridique", formeJuridique);
        return StepResult.ok(out);
    }

    /**
     * Regle DURE des 16 jours : si une convocation est fournie avec une date, l'ecart
     * entre la date de convocation et la date du PV/assemblee doit etre &ge; 16 jours.
     * Delegue au noyau partage {@link WorkflowSteps#convocationDelaiError} (calcul sur
     * {@code LocalDate} = jours calendaires, insensible au fuseau).
     * Retourne un {@link StepResult#blocked} en cas de violation, sinon {@code null}.
     */
    private StepResult validateConvocation(Map<String, Object> p, LocalDate datePV) {
        String error = WorkflowSteps.convocationDelaiError(p.get("convocation"), datePV);
        return error == null ? null : StepResult.blocked(error);
    }

    // ------------------------------------------------------------------
    //  Step 2 : saisie des nouvelles valeurs (editeur de resolutions)
    // ------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private StepResult stepSaisie(StepContext ctx) {
        // Voie directeur : la saisie detaillee est portee par l'editeur de resolutions
        // cote front (validation front). On persiste les resolutions typees telles
        // quelles pour la generation (etape 3) et la synthese.
        Map<String, Object> out = new HashMap<>();
        Object vRaw = ctx.payload().get("valeurs");
        if (vRaw instanceof Map<?, ?> vMap) {
            out.put("valeurs", (Map<String, Object>) vMap);
        }
        Object resolutions = ctx.payload().get("resolutions");
        if (resolutions != null) out.put("resolutions", resolutions);
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 3 : generation documents + flags JAL / versioning RG-M19
    // ------------------------------------------------------------------
    private StepResult stepGenerationDocuments(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        Map<String, Object> step1 = readStep(ctx, "step1");
        boolean jalRequis = Boolean.TRUE.equals(step1.get("jalRequis"));
        // RG-M19 : version statuts V+1 (par defaut V2)
        Integer previous = asPositiveInt(p.get("previousVersionStatuts"));
        int nouvelleVersionStatuts = (previous == null ? 1 : previous) + 1;
        Map<String, Object> out = new HashMap<>();
        out.put("pvId", p.get("pvId"));
        out.put("statutsNouveauId", p.get("statutsNouveauId"));
        out.put("annonceJalId", p.get("annonceJalId"));
        out.put("nouvelleVersionStatuts", nouvelleVersionStatuts);
        out.put("jalRequis", jalRequis);
        // PV de Modification directeur a resolutions typees. Le code du modele est
        // choisi selon la forme juridique du dossier (mirror Phase C/D).
        out.put("pvTemplate", isAssocieUnique(readDossierForme(ctx))
                ? "PV_MODIFICATION_SARL_AU" : "PV_MODIFICATION_SARL");
        out.put("genereLe", java.time.Instant.now().toString());
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 4 : pieces jointes (OPTIONNELLE — jamais bloquante)
    // ------------------------------------------------------------------
    private StepResult stepPiecesJointes(StepContext ctx) {
        // Etape entierement optionnelle : l'employe peut ne rien deposer et avancer.
        // Les uploads sont deposes en Data Room versionnee cote front ; on ne persiste
        // ici qu'un compteur pour la synthese.
        List<?> pieces = asList(ctx.payload(), "piecesJointes");
        Map<String, Object> out = new HashMap<>();
        out.put("piecesJointes", pieces);
        out.put("piecesJointesCount", pieces.size());
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 5 : synthese + finalisation (PV + statuts valides)
    // ------------------------------------------------------------------
    private StepResult stepFinalisation(StepContext ctx) {
        if (!isTrue(ctx.payload(), "pvValide")) {
            return StepResult.blocked(
                    "Le proces-verbal (ou la decision) doit etre valide avant la finalisation.");
        }
        if (!isTrue(ctx.payload(), "statutsValides")) {
            return StepResult.blocked(
                    "Les statuts mis a jour doivent etre valides avant la finalisation.");
        }
        // RG-M13/M14 (Phase 2) : si une decision publiable est presente, l'annonce legale de
        // modification doit etre validee avant la finalisation.
        Map<String, Object> step3 = readStep(ctx, "step3");
        boolean jalRequis = Boolean.TRUE.equals(step3.get("jalRequis"));
        if (jalRequis && !isTrue(ctx.payload(), "jalValide")) {
            return StepResult.blocked(
                    "RG-M13 : l'annonce legale de modification doit etre validee (publication "
                            + "au Journal d'Annonces Legales obligatoire pour cette modification).");
        }
        return StepResult.ok(Map.of("finalise", true));
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /** Articles de statuts impactes par un {@code resolutionType} (voie directeur). */
    private List<String> articlesForResolution(String rt) {
        return switch (rt) {
            case "modification_denomination" -> List.of("Article 2");
            case "modification_objet" -> List.of("Article 3");
            case "transfert_siege" -> List.of("Article 4");
            case "prorogation_duree" -> List.of("Article 5");
            case "modification_exercice" -> List.of("Article 24");
            case "augmentation_capital_numeraire", "augmentation_capital_nature",
                 "augmentation_capital_incorporation", "reduction_capital" ->
                    List.of("Article 6", "Article 7");
            case "agrement_cession", "agrement_transmission", "nantissement_parts",
                 "cession_parts_pluripersonnelle" -> List.of("Article 6", "Article 7", "Article 8");
            case "nomination_gerant", "renouvellement_gerant", "revocation_gerant",
                 "remuneration_gerant", "autorisation_gerance" -> List.of("Article 12");
            case "commissaire_comptes" -> List.of("Article 15");
            case "transformation", "designation_commissaire_transformation" ->
                    List.of("Statuts complets");
            case "operation_restructuration" -> List.of("Projet annexe");
            case "capitaux_propres_art86" -> List.of("Loi 5-96 art. 86");
            case "mise_harmonie_statuts", "modification_statuts_autre" -> List.of("Statuts");
            case "pouvoirs_formalites" -> List.of("Decision standard");
            default -> List.of();
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readStep(StepContext ctx, String key) {
        Object v = ctx.existingData() == null ? null : ctx.existingData().get(key);
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    private String readDossierForme(StepContext ctx) {
        Map<String, Object> dossier = readStep(ctx, "dossier");
        if (dossier.isEmpty()) {
            // Accepte aussi que le payload step 1 transporte directement formeJuridique.
            return strOrNull(ctx.payload().get("formeJuridique"));
        }
        return strOrNull(dossier.get("formeJuridique"));
    }

    private static String strOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /** SARL AU si la forme juridique contient « AU » ou « UNIQUE » (mirror Phase C/D). */
    private static boolean isAssocieUnique(String forme) {
        if (forme == null) return false;
        String f = forme.toUpperCase(java.util.Locale.ROOT);
        return f.contains("AU") || f.contains("UNIQUE");
    }

    private static LocalDate parseDateOrNull(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }

    private static Integer asPositiveInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) {
            long l = n.longValue();
            return (l > 0 && l <= Integer.MAX_VALUE) ? (int) l : null;
        }
        try {
            int i = Integer.parseInt(v.toString().trim());
            return i > 0 ? i : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
