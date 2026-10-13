package ma.jurika.workflow.domain.strategy;

import ma.jurika.workflow.domain.model.WorkflowType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ma.jurika.workflow.domain.strategy.WorkflowSteps.asList;
import static ma.jurika.workflow.domain.strategy.WorkflowSteps.isTrue;

/**
 * Workflow SUCCURSALE MAROCAINE (5 etapes) — spec directeur, lot DIVERS §B (2026-08-13).
 *
 * <p>Etapes cibles :
 * <ol>
 *   <li><b>Societe mere + assemblee</b> — choix du dossier existant (identite lue en BD,
 *       JAMAIS re-saisie), date de l'assemblee et TYPE (ordinaire / extraordinaire),
 *       convocation OPTIONNELLE (regle DURE des 16 jours calendaires).</li>
 *   <li><b>Saisie de la succursale</b> — enseigne, adresse, ville, activite, date
 *       d'ouverture, <b>ville du greffe de la succursale</b> (distincte de celle du siege :
 *       la succursale s'immatricule a SON greffe), dotation optionnelle, responsable
 *       optionnel (personne physique ou morale, CIN recto-verso + OCR cote front, ou
 *       selection d'un gerant / associe deja en BD).</li>
 *   <li><b>Generation</b> — PV de creation de succursale
 *       ({@code PV_CREATION_SUCCURSALE_MAROC_SARL(/_AU)}) + <b>annonce legale d'ouverture</b>
 *       ({@code ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL(/_AU)}). Feuille de presence et
 *       PV d'incident restent optionnels (non bloquants).</li>
 *   <li><b>Pieces jointes</b> — depot des versions legalisees (OPTIONNELLE, jamais
 *       bloquante : l'etape peut rester vide).</li>
 *   <li><b>Synthese</b> — recap + archivage.</li>
 * </ol>
 *
 * <p><b>Refonte 11 -> 5 etapes.</b> L'ancien parcours eclatait la saisie sur quatre etapes
 * (identite / adresse / directeur) puis alignait quatre etapes de documents (PV, statuts
 * modifies, formulaire RC, annonce JAL) et une saisie du RC de la succursale. Les etapes
 * « statuts modifies » et « formulaire RC » n'avaient aucun modele directeur ; le RC de la
 * succursale est attribue par le greffe APRES le depot, donc hors workflow (il se saisit
 * dans les identifiants du dossier). Ces etapes sont supprimees.
 *
 * <p><b>Zero re-saisie</b> : denomination, capital, siege, RC et ville de greffe de la
 * societe mere viennent du dossier ({@code ctx.existingData().get("dossier")} cote workflow,
 * {@code SocieteIdentityEnricher} cote ai-service). Les donnees des etapes 1 et 2 alimentent
 * le PV ET l'annonce sans double saisie : les deux documents partagent le meme bloc
 * {@code succursale}.
 */
@Component
public class SuccursaleMaWorkflow extends AbstractWorkflow {

    /** Delai minimum convocation -> assemblee (noyau partage). */
    public static final int CONVOCATION_DELAI_JOURS = WorkflowSteps.CONVOCATION_DELAI_JOURS;

    /** Une societe dissoute / liquidee / radiee n'ouvre pas de succursale. */
    private static final Set<String> STATUTS_INCOMPATIBLES =
            Set.of("DISSOUTE", "EN_LIQUIDATION", "LIQUIDEE", "RADIE");

    /** Nature de l'assemblee qui decide l'ouverture. */
    private static final Set<String> TYPES_ASSEMBLEE = Set.of("ordinaire", "extraordinaire");

    /** Origine du responsable : choisi en BD (gerant / associe) ou saisi (externe). */
    private static final Set<String> RESPONSABLE_SOURCES = Set.of("BD", "EXTERNE");

    @Override
    public WorkflowType type() {
        return WorkflowType.SUCCURSALE_MA;
    }

    @Override
    protected StepResult doExecuteStep(StepContext ctx) {
        return switch (ctx.step()) {
            case 1 -> stepSocieteMere(ctx);
            case 2 -> stepSuccursale(ctx);
            case 3 -> stepGeneration(ctx);
            case 4 -> stepPiecesJointes(ctx);
            case 5 -> stepSynthese(ctx);
            default -> StepResult.blocked("\u00c9tape inconnue : " + ctx.step());
        };
    }

    // ------------------------------------------------------------------
    //  Step 1 : societe mere (BD) + date/type d'assemblee + convocation
    // ------------------------------------------------------------------
    private StepResult stepSocieteMere(StepContext ctx) {
        Map<String, Object> p = ctx.payload();

        String dossierIdRaw = strOrNull(first0(p.get("dossierId"), p.get("societeMereId")));
        if (dossierIdRaw == null) {
            return StepResult.blocked("S\u00e9lectionnez la soci\u00e9t\u00e9 m\u00e8re.");
        }
        try {
            UUID.fromString(dossierIdRaw);
        } catch (IllegalArgumentException e) {
            return StepResult.blocked(
                    "Identifiant de soci\u00e9t\u00e9 invalide. S\u00e9lectionnez la soci\u00e9t\u00e9 dans la liste.");
        }

        Map<String, Object> dossier = readDossier(ctx);
        String statut = strOrNull(dossier.get("statut"));
        if (statut != null && STATUTS_INCOMPATIBLES.contains(statut)) {
            return StepResult.blocked(
                    "Cette soci\u00e9t\u00e9 est au statut \u00ab " + statut + " \u00bb : elle ne peut plus "
                            + "ouvrir de succursale.");
        }

        // Date de l'assemblee (ou de la decision de l'associe unique en SARL AU).
        String dateRaw = strOrNull(first0(p.get("dateAG"), p.get("dateAssemblee"), p.get("dateAGE")));
        if (dateRaw == null) {
            return StepResult.blocked(
                    "La date de l\u2019assembl\u00e9e qui d\u00e9cide l\u2019ouverture est obligatoire.");
        }
        LocalDate dateAg = parseDateOrNull(dateRaw);
        if (dateAg == null) {
            return StepResult.blocked("Date d\u2019assembl\u00e9e invalide. Format attendu : AAAA-MM-JJ.");
        }
        if (dateAg.isAfter(LocalDate.now().plusDays(30))) {
            return StepResult.blocked(
                    "La date d\u2019assembl\u00e9e ne peut pas \u00eatre dans le futur (> 30 jours).");
        }
        if (dateAg.isBefore(LocalDate.now().minusYears(2))) {
            return StepResult.blocked(
                    "La date d\u2019assembl\u00e9e est trop ancienne (> 2 ans). V\u00e9rifiez la saisie.");
        }

        // Type d'assemblee : contrairement a la dissolution, l'ouverture d'une succursale
        // peut relever d'une AGO comme d'une AGE selon les statuts -> le choix est offert.
        String typeAg = strOrNull(first0(p.get("typeAssemblee"), p.get("assembleeNature")));
        if (typeAg == null) {
            return StepResult.blocked(
                    "Pr\u00e9cisez le type d\u2019assembl\u00e9e : ordinaire ou extraordinaire.");
        }
        String typeNorm = typeAg.toLowerCase(Locale.ROOT);
        if (!TYPES_ASSEMBLEE.contains(typeNorm)) {
            return StepResult.blocked(
                    "Type d\u2019assembl\u00e9e invalide : attendu \u00ab ordinaire \u00bb ou \u00ab extraordinaire \u00bb.");
        }

        // Convocation OPTIONNELLE — regle DURE des 16 jours (jours calendaires).
        String convocationError =
                WorkflowSteps.convocationDelaiError(p.get("convocation"), dateAg);
        if (convocationError != null) return StepResult.blocked(convocationError);

        Map<String, Object> out = new HashMap<>();
        out.put("dossierId", dossierIdRaw);
        // Conserve pour compatibilite avec l'ancien payload front / les scripts e2e.
        out.put("societeMereId", dossierIdRaw);
        out.put("dateAG", dateRaw);
        out.put("assembleeNature", typeNorm);
        Object convocation = p.get("convocation");
        if (convocation != null) out.put("convocation", convocation);

        String forme = strOrNull(dossier.get("formeJuridique"));
        boolean isAu = isAssocieUnique(forme);
        if (forme != null) {
            out.put("formeJuridique", forme);
            out.put("associeUnique", isAu);
        }
        putIfPresent(out, "denomination", dossier.get("denomination"));
        putIfPresent(out, "ice", dossier.get("ice"));
        out.put("decisionType", isAu ? "AU" : (typeNorm.equals("ordinaire") ? "AGO" : "AGE"));
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 2 : saisie des donnees de la succursale
    //  Noyau PARTAGE avec SUCCURSALE_ETR (spec §C : « saisie identique au §B
    //  etape 2 ») — une seule implementation, donc aucune derive possible.
    // ------------------------------------------------------------------
    private StepResult stepSuccursale(StepContext ctx) {
        return SuccursaleSaisieStep.execute(ctx.payload(), RESPONSABLE_SOURCES);
    }

    // ------------------------------------------------------------------
    //  Step 3 : generation (PV d'ouverture + annonce legale)
    // ------------------------------------------------------------------
    private StepResult stepGeneration(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        if (!isTrue(p, "pvValide")) {
            return StepResult.blocked(
                    "Le PV de cr\u00e9ation de la succursale doit \u00eatre valide avant de poursuivre.");
        }
        if (!isTrue(p, "annonceValide")) {
            return StepResult.blocked(
                    "L\u2019annonce l\u00e9gale d\u2019ouverture doit \u00eatre valid\u00e9e : sa publication au "
                            + "Journal d\u2019Annonces L\u00e9gales est obligatoire (les num\u00e9ro et date "
                            + "de d\u00e9p\u00f4t l\u00e9gal, attribu\u00e9s par le greffe apr\u00e8s d\u00e9p\u00f4t, restent "
                            + "facultatifs \u00e0 ce stade).");
        }

        Map<String, Object> dossier = readDossier(ctx);
        String forme = strOrNull(dossier.get("formeJuridique"));
        boolean isAu = isAssocieUnique(forme);

        Map<String, Object> out = new HashMap<>();
        out.put("pvValide", true);
        out.put("annonceValide", true);
        out.put("pvTemplate", isAu
                ? "PV_CREATION_SUCCURSALE_MAROC_SARL_AU"
                : "PV_CREATION_SUCCURSALE_MAROC_SARL");
        out.put("annonceTemplate", isAu
                ? "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU"
                : "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL");
        if (forme != null) {
            out.put("formeJuridique", forme);
            out.put("associeUnique", isAu);
        }
        // Depot legal : attribue par le greffe APRES depot -> facultatif ici.
        Object depotLegal = p.get("depotLegal");
        if (depotLegal != null) out.put("depotLegal", depotLegal);
        out.put("genereLe", java.time.Instant.now().toString());
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 4 : pieces jointes (OPTIONNELLE — jamais bloquante)
    // ------------------------------------------------------------------
    private StepResult stepPiecesJointes(StepContext ctx) {
        List<?> pieces = asList(ctx.payload(), "piecesJointes");
        Map<String, Object> out = new HashMap<>();
        out.put("piecesJointes", pieces);
        out.put("piecesJointesCount", pieces.size());
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 5 : synthese + archivage
    // ------------------------------------------------------------------
    private StepResult stepSynthese(StepContext ctx) {
        Map<String, Object> out = new HashMap<>();
        out.put("succursaleStatut", "ACTIVE");
        out.put("archiveLe", java.time.Instant.now().toString());
        out.put("finalise", true);
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readDossier(StepContext ctx) {
        Object v = ctx.existingData() == null ? null : ctx.existingData().get("dossier");
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    /** {@code true} si la forme designe une SARL a associe unique (AU / unique). */
    static boolean isAssocieUnique(String forme) {
        if (forme == null) return false;
        String f = forme.toUpperCase(Locale.ROOT);
        return f.contains("AU") || f.contains("UNIQUE");
    }

    private static void putIfPresent(Map<String, Object> out, String key, Object value) {
        if (value != null) out.put(key, value);
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
