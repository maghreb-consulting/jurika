package ma.jurika.workflow.domain.strategy;

import ma.jurika.workflow.domain.model.WorkflowType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ma.jurika.workflow.domain.strategy.WorkflowSteps.asList;
import static ma.jurika.workflow.domain.strategy.WorkflowSteps.isTrue;

/**
 * Workflow DISSOLUTION (4 etapes) — spec directeur 2026-08-12.
 *
 * <p>Etapes cibles :
 * <ol>
 *   <li><b>Saisie</b> — societe (dossier), motif, date de l'AGE (assemblee generale
 *       EXTRAORDINAIRE uniquement, ou decision de l'associe unique en SARL AU),
 *       liquidateur (selectionne parmi les gerants/associes en BD, ou externe +
 *       OCR CIN cote front), siege de la liquidation, convocation OPTIONNELLE
 *       (regle des 16 jours).</li>
 *   <li><b>Generation</b> — PV de dissolution ({@code PV_DISSOLUTION_LIQUIDATION_*},
 *       etape « dissolution ») + <b>annonce legale de dissolution</b>
 *       ({@code ANNONCE_LEGALE_DISSOLUTION_*}). Feuille de presence et PV d'incident
 *       restent optionnels (non bloquants).</li>
 *   <li><b>Pieces jointes</b> — depot des versions legalisees (OPTIONNELLE, jamais
 *       bloquante : l'etape peut rester vide).</li>
 *   <li><b>Synthese</b> — recap + archivage.</li>
 * </ol>
 *
 * <p><b>Zero re-saisie</b> : l'identite societe (denomination, capital, siege, RC, ville
 * de greffe) n'est JAMAIS saisie ici — elle est lue en BD ({@code ctx.existingData().get("dossier")}
 * cote workflow, {@code SocieteIdentityEnricher} cote ai-service). Les donnees de l'etape 1
 * (date, liquidateur, siege de liquidation) alimentent le PV ET l'annonce sans double saisie.
 *
 * <p>Conforme RG-DI01..05. La regle des 16 jours reutilise le noyau partage
 * {@link WorkflowSteps#convocationDelaiError} (calcul sur {@code LocalDate} = jours
 * calendaires, insensible au fuseau / aux changements d'heure).
 */
@Component
public class DissolutionWorkflow extends AbstractWorkflow {

    private static final int MOTIF_MIN_LENGTH = 20;
    private static final Set<String> STATUTS_DEJA_TERMINES =
            Set.of("DISSOUTE", "LIQUIDEE", "RADIE");

    /** Delai minimum convocation -> assemblee (partage avec la Modification). */
    public static final int CONVOCATION_DELAI_JOURS = WorkflowSteps.CONVOCATION_DELAI_JOURS;

    /** Origine du liquidateur : choisi en BD (gerant / associe) ou saisi (externe). */
    private static final Set<String> LIQUIDATEUR_SOURCES = Set.of("BD", "EXTERNE");

    @Override
    public WorkflowType type() {
        return WorkflowType.DISSOLUTION;
    }

    @Override
    protected StepResult doExecuteStep(StepContext ctx) {
        return switch (ctx.step()) {
            case 1 -> stepSaisie(ctx);
            case 2 -> stepGeneration(ctx);
            case 3 -> stepPiecesJointes(ctx);
            case 4 -> stepSynthese(ctx);
            default -> StepResult.blocked("\u00c9tape inconnue : " + ctx.step());
        };
    }

    // ------------------------------------------------------------------
    //  Step 1 : saisie complete (societe, motif, date AGE, liquidateur,
    //           siege de liquidation, convocation optionnelle)
    // ------------------------------------------------------------------
    private StepResult stepSaisie(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        // dossierId : UUID strict
        String dossierIdRaw = strOrNull(p.get("dossierId"));
        if (dossierIdRaw == null) {
            return StepResult.blocked(
                    "S\u00e9lectionnez la soci\u00e9t\u00e9 \u00e0 dissoudre.");
        }
        try {
            UUID.fromString(dossierIdRaw);
        } catch (IllegalArgumentException e) {
            return StepResult.blocked(
                    "Identifiant de soci\u00e9t\u00e9 invalide. S\u00e9lectionnez la soci\u00e9t\u00e9 dans la liste.");
        }
        // Motif >= 20 chars
        String motif = strOrNull(p.get("motifDissolution"));
        if (motif == null) {
            return StepResult.blocked("Le motif de la dissolution est obligatoire.");
        }
        if (motif.length() < MOTIF_MIN_LENGTH) {
            return StepResult.blocked(
                    "Le motif est trop court \u2014 pr\u00e9cisez la cause (cessation d\u2019activit\u00e9, "
                            + "m\u00e9sentente entre associ\u00e9s, etc.). Minimum "
                            + MOTIF_MIN_LENGTH + " caract\u00e8res.");
        }
        // Date AGE — la dissolution se decide TOUJOURS en assemblee generale
        // EXTRAORDINAIRE (ou par decision de l'associe unique en SARL AU) : aucun
        // choix ordinaire/extraordinaire n'est offert.
        String dateRaw = strOrNull(p.get("dateAGE"));
        if (dateRaw == null) {
            return StepResult.blocked(
                    "La date de l\u2019assembl\u00e9e g\u00e9n\u00e9rale extraordinaire (AGE) de dissolution "
                            + "est obligatoire.");
        }
        LocalDate dateAge = parseDateOrNull(dateRaw);
        if (dateAge == null) {
            return StepResult.blocked(
                    "Date AGE invalide. Format attendu : AAAA-MM-JJ.");
        }
        if (dateAge.isAfter(LocalDate.now().plusDays(30))) {
            return StepResult.blocked(
                    "La date AGE ne peut pas \u00eatre dans le futur (> 30 jours).");
        }
        if (dateAge.isBefore(LocalDate.now().minusYears(2))) {
            return StepResult.blocked(
                    "La date AGE est trop ancienne (> 2 ans). V\u00e9rifiez la saisie.");
        }
        // Statut dossier (best-effort via injection en existingData)
        Map<String, Object> dossier = readDossier(ctx);
        String statut = strOrNull(dossier.get("statut"));
        if (statut != null && STATUTS_DEJA_TERMINES.contains(statut)) {
            return StepResult.blocked(
                    "Cette soci\u00e9t\u00e9 est d\u00e9j\u00e0 au statut \u00ab " + statut + " \u00bb : la dissolution "
                            + "n\u2019est pas applicable.");
        }

        // Liquidateur : nomme des l'etape 1 (il alimente le PV ET l'annonce legale).
        Map<String, Object> liquidateur = asMap(p.get("liquidateur"));
        // Lot L4d (E5) : personne physique (nom) ou personne morale (denomination).
        boolean liquidateurMorale = WorkflowSteps.liquidateurPersonneMorale(liquidateur);
        String liquidateurNom = WorkflowSteps.identiteLiquidateur(liquidateur);
        if (liquidateurNom == null) {
            return StepResult.blocked(liquidateurMorale
                    ? "Indiquez la d\u00e9nomination du liquidateur personne morale."
                    : "D\u00e9signez le liquidateur : choisissez un g\u00e9rant ou un associ\u00e9 existant, "
                            + "ou saisissez un liquidateur externe.");
        }
        String source = strOrNull(liquidateur.get("source"));
        if (source != null && !LIQUIDATEUR_SOURCES.contains(source.toUpperCase(java.util.Locale.ROOT))) {
            return StepResult.blocked(
                    "Origine du liquidateur invalide : attendu \u00ab BD \u00bb (g\u00e9rant / associ\u00e9 du "
                            + "dossier) ou « EXTERNE » (saisie manuelle).");
        }
        String liquidateurAdresse = strOrNull(liquidateur.get("adresse"));
        if (liquidateurAdresse == null) {
            return StepResult.blocked(
                    "L\u2019adresse du liquidateur est obligatoire : elle figure dans l\u2019annonce "
                            + "l\u00e9gale de dissolution.");
        }

        // Siege de la liquidation (variable $SIEGE_LIQUIDATION de l'annonce).
        // Lot L4d (E11d) : l'ecran demande d'abord si le siege de la liquidation est le siege
        // social (siegeIdentique, lot L4b) ; dans ce cas aucune adresse n'est saisie. Le choix
        // est conserve avec le liquidateur (les PV du corpus le lisent, cloture comprise).
        String siegeIdentique = strOrNull(first0(p.get("siegeIdentique"), liquidateur.get("siegeIdentique")));
        String siegeLiquidation = strOrNull(first0(p.get("siegeLiquidation"),
                liquidateur.get("siege")));
        if (siegeLiquidation == null && !"oui".equals(siegeIdentique)) {
            return StepResult.blocked("Le si\u00e8ge de la liquidation est obligatoire.");
        }

        // Convocation OPTIONNELLE — regle DURE des 16 jours (jours calendaires).
        String convocationError =
                WorkflowSteps.convocationDelaiError(p.get("convocation"), dateAge);
        if (convocationError != null) return StepResult.blocked(convocationError);

        Map<String, Object> out = new HashMap<>();
        out.put("dossierId", dossierIdRaw);
        out.put("motif", motif);
        out.put("dateAGE", dateRaw);
        // Propage les faits dossier pour ai-service / front
        if (!dossier.isEmpty()) {
            out.put("formeJuridique", strOrNull(dossier.get("formeJuridique")));
            out.put("denomination", strOrNull(dossier.get("denomination")));
            out.put("ice", strOrNull(dossier.get("ice")));
        }
        // Liquidateur normalise (source + identite) — consomme tel quel par les mappers.
        Map<String, Object> liq = new LinkedHashMap<>(liquidateur);
        if (!liquidateurMorale) liq.put("nom", liquidateurNom);
        liq.put("adresse", liquidateurAdresse);
        if (siegeLiquidation != null) liq.put("siege", siegeLiquidation);
        else liq.remove("siege");
        if ("oui".equals(siegeIdentique) || "non".equals(siegeIdentique)) liq.put("siegeIdentique", siegeIdentique);
        if (source != null) liq.put("source", source.toUpperCase(java.util.Locale.ROOT));
        out.put("liquidateur", liq);
        if (siegeLiquidation != null) out.put("siegeLiquidation", siegeLiquidation);
        Object convocation = p.get("convocation");
        if (convocation != null) out.put("convocation", convocation);

        // Templates selectionnes pour ai-service.
        // PV directeur unifie dissolution/liquidation (couvre les 3 etapes du cycle via
        // $PV_ETAPE ; ici etape « dissolution ») + annonce legale de dissolution.
        String forme = strOrNull(out.get("formeJuridique"));
        boolean isAu = "SARL_AU".equals(forme);
        out.put("pvTemplate", isAu
                ? "PV_DISSOLUTION_LIQUIDATION_SARL_AU"
                : "PV_DISSOLUTION_LIQUIDATION_SARL");
        out.put("pvEtape", "dissolution");
        out.put("annonceTemplate", isAu
                ? "ANNONCE_LEGALE_DISSOLUTION_SARL_AU"
                : "ANNONCE_LEGALE_DISSOLUTION_SARL");
        // La dissolution est toujours extraordinaire (AGE) / decision de l'associe unique.
        out.put("assembleeNature", "extraordinaire");
        out.put("decisionType", isAu ? "AU" : "AGE");
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 2 : generation (PV de dissolution + annonce legale)
    // ------------------------------------------------------------------
    private StepResult stepGeneration(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        if (!isTrue(p, "pvValide")) {
            return StepResult.blocked(
                    "Le PV de dissolution doit \u00eatre valide avant de poursuivre.");
        }
        if (!isTrue(p, "annonceValide")) {
            return StepResult.blocked(
                    "L\u2019annonce l\u00e9gale de dissolution doit \u00eatre valid\u00e9e : sa publication au "
                            + "Journal d\u2019Annonces L\u00e9gales est obligatoire (les num\u00e9ro et date "
                            + "de d\u00e9p\u00f4t l\u00e9gal, attribu\u00e9s par le greffe apr\u00e8s d\u00e9p\u00f4t, restent "
                            + "facultatifs \u00e0 ce stade).");
        }
        Map<String, Object> out = new HashMap<>();
        out.put("pvValide", true);
        out.put("annonceValide", true);
        // Depot legal : attribue par le greffe APRES depot -> facultatif ici.
        Object depotLegal = p.get("depotLegal");
        if (depotLegal != null) out.put("depotLegal", depotLegal);
        out.put("genereLe", java.time.Instant.now().toString());
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 3 : pieces jointes (OPTIONNELLE — jamais bloquante)
    // ------------------------------------------------------------------
    private StepResult stepPiecesJointes(StepContext ctx) {
        // Etape entierement optionnelle : l'employe peut ne rien deposer et avancer.
        // Les uploads partent en Data Room versionnee cote front ; on ne persiste ici
        // qu'un compteur pour la synthese.
        List<?> pieces = asList(ctx.payload(), "piecesJointes");
        Map<String, Object> out = new HashMap<>();
        out.put("piecesJointes", pieces);
        out.put("piecesJointesCount", pieces.size());
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 4 : synthese + archivage
    // ------------------------------------------------------------------
    private StepResult stepSynthese(StepContext ctx) {
        Map<String, Object> out = new HashMap<>();
        out.put("societeStatut", "DISSOUTE");
        out.put("archiveLe", java.time.Instant.now().toString());
        out.put("finalise", true);
        // Suggestion utilisateur : ouvrir le ticket Liquidation
        out.put("nextSuggestion", "Ouvrir un ticket Liquidation lorsque le d\u00e9lai l\u00e9gal "
                + "de 16 jours est \u00e9coul\u00e9.");
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private Map<String, Object> readDossier(StepContext ctx) {
        Object v = ctx.existingData() == null ? null : ctx.existingData().get("dossier");
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    private static Object first0(Object... vals) {
        for (Object o : vals) {
            if (o != null && !(o instanceof String s && s.isBlank())) return o;
        }
        return null;
    }

    private static String strOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDate parseDateOrNull(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }
}
