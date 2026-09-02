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
 * Workflow FERMETURE DE SUCCURSALE (4 etapes) — spec directeur, lot DIVERS §D (2026-08-13).
 *
 * <p>Etapes cibles :
 * <ol>
 *   <li><b>Selection</b> — la succursale a fermer est CHOISIE dans la liste des succursales
 *       ACTIVE de la societe mere (table {@code succursales}) : enseigne, adresse, ville,
 *       activite et <b>{@code SUCCURSALE_RC_NUMERO}</b> sont REPRIS DE LA BD, jamais
 *       re-saisis. S'ajoutent la date d'assemblee + son TYPE, la convocation OPTIONNELLE
 *       (regle DURE des 16 jours), et les deux donnees propres a la fermeture :
 *       <b>{@code SUCCURSALE_DATE_FERMETURE}</b> (date d'effet) et
 *       <b>{@code SUCCURSALE_MOTIF}</b> — ce dernier OBLIGATOIRE car publie dans l'avis.</li>
 *   <li><b>Generation</b> — PV de fermeture ({@code PV_FERMETURE_SUCCURSALE_SARL(/_AU)})
 *       + <b>annonce legale de fermeture</b>
 *       ({@code ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL(/_AU)}) ; optionnels non bloquants.</li>
 *   <li><b>Pieces jointes</b> — depot des versions legalisees (OPTIONNELLE).</li>
 *   <li><b>Synthese</b> — recap ; a la finalisation la succursale passe FERMEE en base
 *       avec sa date d'effet et son motif (migration V17).</li>
 * </ol>
 *
 * <p><b>Refonte du parcours.</b> L'ancien decoupage separait « PV » (etape 2) et
 * « validation PV + JAL » (etape 3) : deux ecrans pour un seul acte, et l'annonce s'y
 * reduisait a un identifiant libre sans modele. Les deux fusionnent en une etape de
 * generation, ce qui libere une etape pour les pieces jointes.
 *
 * <p><b>RC de la succursale.</b> Depuis la refonte des workflows d'ouverture (§B/§C), le
 * RC n'y est plus saisi : il est attribue par le greffe APRES le depot. Il peut donc etre
 * absent en base. Dans ce cas SEULEMENT, l'etape 1 accepte de le saisir une fois — et
 * {@code WorkflowUseCases} le persiste sur la ligne {@code succursales} pour qu'il ne soit
 * plus jamais redemande. Quand il est en base, il est repris tel quel.
 */
@Component
public class FermetureSuccursaleWorkflow extends AbstractWorkflow {

    /** Delai minimum convocation -> assemblee (noyau partage). */
    public static final int CONVOCATION_DELAI_JOURS = WorkflowSteps.CONVOCATION_DELAI_JOURS;

    /** Longueur minimale du motif : il est PUBLIE, une mention laconique ne suffit pas. */
    static final int MOTIF_MIN_LENGTH = 10;

    private static final Set<String> TYPES_ASSEMBLEE = Set.of("ordinaire", "extraordinaire");

    @Override
    public WorkflowType type() {
        return WorkflowType.FERMETURE_SUCCURSALE;
    }

    @Override
    protected StepResult doExecuteStep(StepContext ctx) {
        return switch (ctx.step()) {
            case 1 -> stepSelection(ctx);
            case 2 -> stepGeneration(ctx);
            case 3 -> stepPiecesJointes(ctx);
            case 4 -> stepSynthese(ctx);
            default -> StepResult.blocked("Etape inconnue : " + ctx.step());
        };
    }

    // ------------------------------------------------------------------
    //  Step 1 : selection BD + date/type d'assemblee + convocation + fermeture
    // ------------------------------------------------------------------
    private StepResult stepSelection(StepContext ctx) {
        Map<String, Object> p = ctx.payload();

        // Societe mere : dossier existant (identite lue en BD).
        String dossierIdRaw = strOrNull(first0(p.get("dossierId"), p.get("societeMereId")));
        if (dossierIdRaw == null) {
            return StepResult.blocked("Selectionnez la societe mere.");
        }
        try {
            UUID.fromString(dossierIdRaw);
        } catch (IllegalArgumentException e) {
            return StepResult.blocked(
                    "Identifiant de societe invalide. Selectionnez la societe dans la liste.");
        }

        Map<String, Object> succ = buildSuccursale(p);

        // Succursale a fermer : soit CHOISIE dans la liste des ACTIVE (identifiant BD), soit
        // SAISIE a la main lorsqu'elle est absente du referentiel (exploitee avant que la
        // table `succursales` n'existe). Dans ce second cas, WorkflowUseCases la CREE en base
        // des la validation de cette etape : sa fermeture sera REELLE et non seulement
        // documentaire — c'est la fin de la « fermeture fantome », ou le PV et l'annonce
        // etaient produits sans qu'aucune ligne ne passe jamais FERMEE.
        String succursaleDbId = strOrNull(p.get("succursaleDbId"));
        String succursaleId = strOrNull(p.get("succursaleId"));
        if (succursaleDbId != null) {
            try {
                UUID.fromString(succursaleDbId);
            } catch (IllegalArgumentException e) {
                return StepResult.blocked(
                        "Identifiant de succursale invalide. Selectionnez-la dans la liste.");
            }
        } else if (succursaleId == null) {
            // Saisie manuelle : on exige le minimum permettant de creer la ligne (l'enseigne
            // l'identifie, la ville designe le greffe de sa radiation).
            if (strOrNull(succ.get("enseigne")) == null || strOrNull(succ.get("ville")) == null) {
                return StepResult.blocked(
                        "Selectionnez la succursale a fermer dans la liste de cette societe. "
                                + "Si elle n'y figure pas encore, saisissez au minimum son "
                                + "enseigne et sa ville : elle sera enregistree puis fermee.");
            }
        }

        // RC de la succursale : repris de la BD. Depuis §B/§C il peut etre absent
        // (attribue par le greffe APRES l'ouverture) -> saisie de secours acceptee ici,
        // puis persistee : il ne sera plus jamais redemande.
        String rcNumero = strOrNull(first0(succ.get("rcNumero"), succ.get("rc")));
        if (rcNumero == null) {
            return StepResult.blocked(
                    "Le numero RC de la succursale est obligatoire : il est publie dans "
                            + "l'annonce de fermeture (« immatriculee sous le n° … »). S'il n'est "
                            + "pas encore enregistre, saisissez-le une fois — il sera conserve.");
        }
        succ.put("rcNumero", rcNumero);

        // Date d'EFFET de la fermeture (distincte de la date d'assemblee).
        String dateFermetureRaw = strOrNull(first0(succ.get("dateFermeture"), succ.get("dateEffet")));
        if (dateFermetureRaw == null) {
            return StepResult.blocked("La date d'effet de la fermeture est obligatoire.");
        }
        LocalDate dateFermeture = parseDateOrNull(dateFermetureRaw);
        if (dateFermeture == null) {
            return StepResult.blocked("Date de fermeture invalide. Format attendu : AAAA-MM-JJ.");
        }
        succ.put("dateFermeture", dateFermetureRaw);

        // Motif : OBLIGATOIRE, il est publie dans l'avis.
        String motif = strOrNull(first0(succ.get("motif"), p.get("motifFermeture")));
        if (motif == null) {
            return StepResult.blocked(
                    "Le motif de la fermeture est obligatoire : il est publie dans l'annonce "
                            + "legale (« Cette fermeture est motivee par … »).");
        }
        if (motif.length() < MOTIF_MIN_LENGTH) {
            return StepResult.blocked(
                    "Le motif est trop court — precisez la cause (cessation de l'activite, "
                            + "reorganisation, transfert au siege...). Minimum "
                            + MOTIF_MIN_LENGTH + " caracteres.");
        }
        succ.put("motif", motif);

        // Date de l'assemblee qui decide la fermeture + son type.
        String dateRaw = strOrNull(first0(p.get("dateAG"), p.get("dateAssemblee"), p.get("dateAGE")));
        if (dateRaw == null) {
            return StepResult.blocked(
                    "La date de l'assemblee qui decide la fermeture est obligatoire.");
        }
        LocalDate dateAg = parseDateOrNull(dateRaw);
        if (dateAg == null) {
            return StepResult.blocked("Date d'assemblee invalide. Format attendu : AAAA-MM-JJ.");
        }
        if (dateAg.isAfter(LocalDate.now().plusDays(30))) {
            return StepResult.blocked(
                    "La date d'assemblee ne peut pas etre dans le futur (> 30 jours).");
        }
        if (dateAg.isBefore(LocalDate.now().minusYears(2))) {
            return StepResult.blocked(
                    "La date d'assemblee est trop ancienne (> 2 ans). Verifiez la saisie.");
        }

        String typeAg = strOrNull(first0(p.get("typeAssemblee"), p.get("assembleeNature")));
        if (typeAg == null) {
            return StepResult.blocked(
                    "Precisez le type d'assemblee : ordinaire ou extraordinaire.");
        }
        String typeNorm = typeAg.toLowerCase(Locale.ROOT);
        if (!TYPES_ASSEMBLEE.contains(typeNorm)) {
            return StepResult.blocked(
                    "Type d'assemblee invalide : attendu « ordinaire » ou « extraordinaire ».");
        }

        // Convocation OPTIONNELLE — regle DURE des 16 jours (jours calendaires).
        String convocationError =
                WorkflowSteps.convocationDelaiError(p.get("convocation"), dateAg);
        if (convocationError != null) return StepResult.blocked(convocationError);

        Map<String, Object> dossier = readDossier(ctx);
        String forme = strOrNull(dossier.get("formeJuridique"));
        boolean isAu = isAssocieUnique(forme);

        Map<String, Object> out = new HashMap<>();
        out.put("dossierId", dossierIdRaw);
        out.put("societeMereId", dossierIdRaw);
        if (succursaleDbId != null) out.put("succursaleDbId", succursaleDbId);
        if (succursaleId != null) out.put("succursaleId", succursaleId);
        out.put("succursale", succ);
        // Champs plats conserves pour la persistance et la retro-compatibilite.
        out.put("motif", motif);
        out.put("dateFermeture", dateFermetureRaw);
        out.put("rcNumero", rcNumero);
        out.put("dateAG", dateRaw);
        out.put("assembleeNature", typeNorm);
        Object convocation = p.get("convocation");
        if (convocation != null) out.put("convocation", convocation);
        if (forme != null) {
            out.put("formeJuridique", forme);
            out.put("associeUnique", isAu);
        }
        putIfPresent(out, "denomination", dossier.get("denomination"));
        putIfPresent(out, "ice", dossier.get("ice"));
        putIfPresent(out, "formalitesMandataireNom", p.get("formalitesMandataireNom"));
        out.put("decisionType", isAu ? "AU" : (typeNorm.equals("ordinaire") ? "AGO" : "AGE"));
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 2 : generation (PV de fermeture + annonce legale)
    // ------------------------------------------------------------------
    private StepResult stepGeneration(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        if (!isTrue(p, "pvValide")) {
            return StepResult.blocked(
                    "Le PV de fermeture doit etre valide avant de poursuivre.");
        }
        if (!isTrue(p, "annonceValide")) {
            return StepResult.blocked(
                    "L'annonce legale de fermeture doit etre validee : sa publication au "
                            + "Journal d'Annonces Legales fonde la radiation de la succursale "
                            + "(les numero et date de depot legal, attribues par le greffe apres "
                            + "depot, restent facultatifs a ce stade).");
        }

        Map<String, Object> dossier = readDossier(ctx);
        String forme = strOrNull(dossier.get("formeJuridique"));
        boolean isAu = isAssocieUnique(forme);

        Map<String, Object> out = new HashMap<>();
        out.put("pvValide", true);
        out.put("annonceValide", true);
        out.put("pvTemplate", isAu
                ? "PV_FERMETURE_SUCCURSALE_SARL_AU"
                : "PV_FERMETURE_SUCCURSALE_SARL");
        out.put("annonceTemplate", isAu
                ? "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL_AU"
                : "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL");
        if (forme != null) {
            out.put("formeJuridique", forme);
            out.put("associeUnique", isAu);
        }
        Object depotLegal = p.get("depotLegal");
        if (depotLegal != null) out.put("depotLegal", depotLegal);
        out.put("genereLe", java.time.Instant.now().toString());
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 3 : pieces jointes (OPTIONNELLE — jamais bloquante)
    // ------------------------------------------------------------------
    private StepResult stepPiecesJointes(StepContext ctx) {
        List<?> pieces = asList(ctx.payload(), "piecesJointes");
        Map<String, Object> out = new HashMap<>();
        out.put("piecesJointes", pieces);
        out.put("piecesJointesCount", pieces.size());
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 4 : synthese + cloture de la succursale
    // ------------------------------------------------------------------
    private StepResult stepSynthese(StepContext ctx) {
        Map<String, Object> out = new HashMap<>();
        out.put("succursaleStatut", "FERMEE");
        out.put("archiveLe", java.time.Instant.now().toString());
        out.put("finalise", true);
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /**
     * Assemble le bloc {@code succursale} : bloc deja structure (front refondu) ou champs
     * plats (retro-compatibilite scripts / anciens payloads).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildSuccursale(Map<String, Object> p) {
        Object nested = p.get("succursale");
        Map<String, Object> succ = nested instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        for (String k : new String[]{
                "enseigne", "adresse", "ville", "villeGreffe", "activite",
                "rcNumero", "dateFermeture", "dateEffet", "motif",
                "responsablePresent", "responsable"}) {
            if (!succ.containsKey(k) && p.get(k) != null) {
                succ.put(k, p.get(k));
            }
        }
        // Alias historiques du front (saisie manuelle legacy).
        if (!succ.containsKey("rcNumero") && p.get("rcSecondaire") != null) {
            succ.put("rcNumero", p.get("rcSecondaire"));
        }
        if (!succ.containsKey("adresse") && p.get("adresseSuccursale") != null) {
            succ.put("adresse", p.get("adresseSuccursale"));
        }
        if (!succ.containsKey("ville") && p.get("villeSuccursale") != null) {
            succ.put("ville", p.get("villeSuccursale"));
        }
        if (!succ.containsKey("enseigne") && p.get("succursaleDenomination") != null) {
            succ.put("enseigne", p.get("succursaleDenomination"));
        }
        if (!succ.containsKey("motif") && p.get("motifFermeture") != null) {
            succ.put("motif", p.get("motifFermeture"));
        }
        return succ;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readDossier(StepContext ctx) {
        Object v = ctx.existingData() == null ? null : ctx.existingData().get("dossier");
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

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
