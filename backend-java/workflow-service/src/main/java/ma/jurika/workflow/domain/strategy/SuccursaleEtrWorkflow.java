package ma.jurika.workflow.domain.strategy;

import ma.jurika.workflow.domain.model.WorkflowType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
 * Workflow SUCCURSALE ETRANGERE (5 etapes) — spec directeur, lot DIVERS §C (2026-08-13).
 *
 * <p>Etapes cibles :
 * <ol>
 *   <li><b>Societe mere etrangere</b> — saisie complete
 *       ({@code SOCIETE_MERE_{DENOMINATION, FORME, PAYS, CAPITAL, SIEGE, REGISTRE,
 *       REGISTRE_NUMERO, LOI_APPLICABLE}}) ou <b>selection</b> d'une mere deja
 *       enregistree ; date de la decision de l'organe + TYPE (ordinaire /
 *       extraordinaire) ; convocation OPTIONNELLE (regle DURE des 16 jours) ; et
 *       l'ecran de <b>conformite bloquant</b> (6 controles) conserve de l'ancien
 *       parcours — il porte sur les pieces de la mere, donc sa place est ici.</li>
 *   <li><b>Saisie de la succursale</b> — IDENTIQUE au workflow marocain (§B etape 2) :
 *       enseigne, adresse, ville, activite, date d'ouverture, greffe propre a la
 *       succursale, dotation optionnelle, representant resident optionnel (personne
 *       physique / morale, CIN recto-verso + OCR cote front).</li>
 *   <li><b>Generation</b> — PV de creation
 *       ({@code PV_CREATION_SUCCURSALE_ETRANGERE_SARL(/_AU)}) + <b>annonce legale
 *       d'ouverture, variante ETRANGERE</b>
 *       ({@code ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL(/_AU)}).
 *       ⚠ Ces deux modeles d'annonce sont DERIVES (origin {@code derive-jurika}),
 *       pas livres par le directeur : ils doivent etre valides par lui.</li>
 *   <li><b>Pieces jointes</b> — depot des versions legalisees (OPTIONNELLE).</li>
 *   <li><b>Synthese</b> — recap + archivage.</li>
 * </ol>
 *
 * <p><b>Refonte 13 -> 5 etapes.</b> Les etapes « statuts apostilles », « RC d'origine »,
 * « decision traduite/apostillee », « formulaire RC » et « RC + ICE de la succursale »
 * disparaissent : les trois premieres sont deja couvertes par les controles de
 * conformite (etape 1) et par le depot des pieces (etape 4) ; les deux dernieres
 * relevent du greffe APRES le depot (le RC et l'ICE de la succursale se saisissent
 * dans les identifiants du dossier, pas dans le workflow).
 *
 * <p><b>Data Room de la mere etrangere.</b> A la finalisation, la societe mere devient
 * un {@code entreprise_dossiers} ({@code origine = 'ETRANGERE'}, migration V16) : elle
 * porte alors sa propre Data Room, et la succursale creee y est rattachee
 * ({@code succursales.parent_dossier_id}, jusqu'ici impossible faute de dossier local).
 */
@Component
public class SuccursaleEtrWorkflow extends AbstractWorkflow {

    /** Delai minimum convocation -> assemblee (noyau partage). */
    public static final int CONVOCATION_DELAI_JOURS = WorkflowSteps.CONVOCATION_DELAI_JOURS;

    /** Nature de la decision de l'organe compétent. */
    private static final Set<String> TYPES_ASSEMBLEE = Set.of("ordinaire", "extraordinaire");

    /** Origine du representant : choisi en BD (mere deja enregistree) ou saisi. */
    private static final Set<String> RESPONSABLE_SOURCES = Set.of("BD", "EXTERNE");

    /**
     * Ecran de conformite BLOQUANT, conserve de l'ancien parcours 13 etapes et
     * rapatrie a l'etape 1 : ces six controles portent sur les pieces de la societe
     * mere etrangere, ils conditionnent la recevabilite du dossier au greffe.
     */
    static final List<String> CONTROLES_CONFORMITE = List.of(
            "controleOffice",            // OMPIC : denomination disponible
            "controleApostille",         // statuts apostilles
            "controleTraduction",        // traduction certifiee FR/AR
            "controleProcuration",       // procuration au representant resident
            "controleDomiciliation",     // adresse au Maroc justifiee
            "controleConvention");       // convention Maroc-pays d'origine

    @Override
    public WorkflowType type() {
        return WorkflowType.SUCCURSALE_ETR;
    }

    @Override
    protected StepResult doExecuteStep(StepContext ctx) {
        return switch (ctx.step()) {
            case 1 -> stepSocieteMere(ctx);
            case 2 -> stepSuccursale(ctx);
            case 3 -> stepGeneration(ctx);
            case 4 -> stepPiecesJointes(ctx);
            case 5 -> stepSynthese(ctx);
            default -> StepResult.blocked("Etape inconnue : " + ctx.step());
        };
    }

    // ------------------------------------------------------------------
    //  Step 1 : societe mere etrangere + decision de l'organe + conformite
    // ------------------------------------------------------------------
    private StepResult stepSocieteMere(StepContext ctx) {
        Map<String, Object> p = ctx.payload();

        // Reutilisation d'une mere DEJA enregistree : identite non re-saisie.
        String dossierMereId = strOrNull(first0(
                p.get("dossierMereEtrangereId"), p.get("dossierLocalId")));
        if (dossierMereId != null) {
            try {
                UUID.fromString(dossierMereId);
            } catch (IllegalArgumentException e) {
                return StepResult.blocked(
                        "Identifiant de societe mere invalide. Selectionnez-la dans la liste.");
            }
        }

        Map<String, Object> mere = buildSocieteMere(p);
        if (dossierMereId == null && strOrNull(mere.get("denomination")) == null) {
            return StepResult.blocked(
                    "Saisissez la societe mere etrangere (denomination), ou selectionnez-en "
                            + "une deja enregistree.");
        }
        if (dossierMereId == null) {
            // Creation d'une nouvelle mere : les mentions publiees dans l'avis sont
            // obligatoires — sans elles, l'annonce partirait avec des trous.
            for (String[] champ : new String[][]{
                    {"forme", "la forme juridique du pays d'origine"},
                    {"pays", "le pays d'origine"},
                    {"siege", "le siege social de la societe mere"}}) {
                if (strOrNull(mere.get(champ[0])) == null) {
                    return StepResult.blocked(
                            "Renseignez " + champ[1] + " : cette mention figure dans l'annonce "
                                    + "legale d'ouverture.");
                }
            }
        }

        // Date de la decision de l'organe competent.
        String dateRaw = strOrNull(first0(p.get("dateAG"), p.get("dateDecision"),
                p.get("organeDate"), p.get("dateAssemblee")));
        if (dateRaw == null) {
            return StepResult.blocked(
                    "La date de la decision de l'organe competent est obligatoire.");
        }
        LocalDate dateAg = parseDateOrNull(dateRaw);
        if (dateAg == null) {
            return StepResult.blocked("Date de decision invalide. Format attendu : AAAA-MM-JJ.");
        }
        if (dateAg.isAfter(LocalDate.now().plusDays(30))) {
            return StepResult.blocked(
                    "La date de decision ne peut pas etre dans le futur (> 30 jours).");
        }
        if (dateAg.isBefore(LocalDate.now().minusYears(2))) {
            return StepResult.blocked(
                    "La date de decision est trop ancienne (> 2 ans). Verifiez la saisie.");
        }

        String typeAg = strOrNull(first0(p.get("typeAssemblee"), p.get("assembleeNature")));
        if (typeAg == null) {
            return StepResult.blocked(
                    "Precisez le type de decision : ordinaire ou extraordinaire.");
        }
        String typeNorm = typeAg.toLowerCase(Locale.ROOT);
        if (!TYPES_ASSEMBLEE.contains(typeNorm)) {
            return StepResult.blocked(
                    "Type de decision invalide : attendu « ordinaire » ou « extraordinaire ».");
        }

        // Convocation OPTIONNELLE — regle DURE des 16 jours (jours calendaires).
        String convocationError =
                WorkflowSteps.convocationDelaiError(p.get("convocation"), dateAg);
        if (convocationError != null) return StepResult.blocked(convocationError);

        // Ecran de conformite : BLOQUANT (conserve du parcours 13 etapes).
        List<String> manquants = new ArrayList<>();
        for (String c : CONTROLES_CONFORMITE) {
            if (!isTrue(p, c)) manquants.add(c);
        }
        if (!manquants.isEmpty()) {
            return StepResult.blocked(
                    "Controles de conformite manquants (bloquant) : " + String.join(", ", manquants));
        }

        Map<String, Object> organe = readOrgane(p);
        organe.putIfAbsent("date", dateRaw);
        boolean isAu = etrIsAssocieUnique(p, organe, mere);

        Map<String, Object> out = new HashMap<>();
        if (dossierMereId != null) out.put("dossierMereEtrangereId", dossierMereId);
        if (!mere.isEmpty()) out.put("societeMere", mere);
        // Charge brute conservee sous `origine` (compat front / scripts historiques).
        out.put("origine", p);
        out.put("organe", organe);
        out.put("dateAG", dateRaw);
        out.put("assembleeNature", typeNorm);
        out.put("associeUnique", isAu);
        out.put("conformiteValidee", true);
        out.put("checksCompletes", CONTROLES_CONFORMITE);
        Object convocation = p.get("convocation");
        if (convocation != null) out.put("convocation", convocation);
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 2 : saisie de la succursale (identique au workflow marocain)
    // ------------------------------------------------------------------
    private StepResult stepSuccursale(StepContext ctx) {
        return SuccursaleSaisieStep.execute(ctx.payload(), RESPONSABLE_SOURCES);
    }

    // ------------------------------------------------------------------
    //  Step 3 : generation (PV etranger + annonce derivee)
    // ------------------------------------------------------------------
    private StepResult stepGeneration(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        if (!isTrue(p, "pvValide")) {
            return StepResult.blocked(
                    "Le PV de creation de la succursale doit etre valide avant de poursuivre.");
        }
        if (!isTrue(p, "annonceValide")) {
            return StepResult.blocked(
                    "L'annonce legale d'ouverture doit etre validee : sa publication au "
                            + "Journal d'Annonces Legales est obligatoire (les numero et date "
                            + "de depot legal, attribues par le greffe apres depot, restent "
                            + "facultatifs a ce stade).");
        }

        boolean isAu = readAssocieUniqueFromStep1(ctx, p);
        Map<String, Object> out = new HashMap<>();
        out.put("pvValide", true);
        out.put("annonceValide", true);
        out.put("associeUnique", isAu);
        out.put("pvTemplate", isAu
                ? "PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU"
                : "PV_CREATION_SUCCURSALE_ETRANGERE_SARL");
        out.put("annonceTemplate", isAu
                ? "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU"
                : "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL");
        // Tracabilite : ces modeles ne viennent pas du directeur.
        out.put("annonceOrigin", "derive-jurika");
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
    private static Map<String, Object> buildSocieteMere(Map<String, Object> p) {
        Object nested = p.get("societeMere");
        if (nested instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        Map<String, Object> mere = new LinkedHashMap<>();
        putIfPresent(mere, "denomination", firstNonNull(
                p.get("denominationSocieteMere"), p.get("denomination")));
        putIfPresent(mere, "forme", firstNonNull(
                p.get("formeJuridiqueOrigine"), p.get("forme")));
        putIfPresent(mere, "pays", firstNonNull(p.get("paysOrigine"), p.get("pays")));
        putIfPresent(mere, "capital", p.get("capital"));
        putIfPresent(mere, "siege", firstNonNull(p.get("siege"), p.get("siegeSocialMere")));
        putIfPresent(mere, "registre", p.get("registre"));
        putIfPresent(mere, "registreNumero", firstNonNull(
                p.get("registreNumero"), p.get("rcOrigine")));
        putIfPresent(mere, "loiApplicable", p.get("loiApplicable"));
        return mere;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readOrgane(Map<String, Object> p) {
        Object nested = p.get("organe");
        if (nested instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        Map<String, Object> organe = new LinkedHashMap<>();
        putIfPresent(organe, "competent", firstNonNull(
                p.get("organeCompetent"), p.get("competent")));
        putIfPresent(organe, "date", p.get("organeDate"));
        putIfPresent(organe, "heure", p.get("organeHeure"));
        putIfPresent(organe, "lieu", p.get("organeLieu"));
        return organe;
    }

    /**
     * Choix de la variante {@code _SARL_AU}. Faute d'identite en base, on se base sur :
     * un flag explicite {@code associeUnique}, un organe decisionnaire unipersonnel
     * (« … unique »), ou la forme mere en {@code AU} / unipersonnelle.
     */
    private static boolean etrIsAssocieUnique(Map<String, Object> p, Map<String, Object> organe,
                                              Map<String, Object> mere) {
        if (isTrue(p, "associeUnique")) return true;
        String competent = strOrNull(organe.get("competent"));
        if (competent != null && competent.toLowerCase(Locale.ROOT).contains("unique")) {
            return true;
        }
        String forme = strOrNull(first0(mere.get("forme"),
                p.get("formeJuridiqueOrigine"), p.get("forme")));
        return isAssocieUnique(forme);
    }

    /** Relit la nature uni/pluripersonnelle decidee a l'etape 1 (source unique). */
    @SuppressWarnings("unchecked")
    private static boolean readAssocieUniqueFromStep1(StepContext ctx, Map<String, Object> p) {
        if (isTrue(p, "associeUnique")) return true;
        Object s1 = ctx.existingData() == null ? null : ctx.existingData().get("step1");
        if (s1 instanceof Map<?, ?> m) {
            return Boolean.TRUE.equals(((Map<String, Object>) m).get("associeUnique"));
        }
        return false;
    }

    static boolean isAssocieUnique(String forme) {
        if (forme == null) return false;
        String f = forme.toUpperCase(Locale.ROOT);
        return f.contains("AU") || f.contains("UNIQUE");
    }

    private static void putIfPresent(Map<String, Object> out, String key, Object value) {
        if (value != null) out.put(key, value);
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
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
