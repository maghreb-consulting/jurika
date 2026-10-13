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
 * Workflow PV AGO — approbation des comptes (5 etapes), spec directeur, lot DIVERS §E
 * (2026-08-13).
 *
 * <p>Etapes cibles :
 * <ol>
 *   <li><b>Societe + assemblee</b> — dossier existant (identite lue en BD, VERROUILLEE :
 *       cf. correctif du 2026-08-13 sur la coherence colonnes / {@code fiche_structuree}),
 *       <b>date de l'AGO</b> — l'approbation des comptes releve TOUJOURS de l'assemblee
 *       ORDINAIRE, aucun selecteur de type n'est offert —, convocation OPTIONNELLE (regle
 *       DURE des 16 jours) et <b>exercice clos</b>.</li>
 *   <li><b>Donnees du PV</b> — resultat de l'exercice (benefice / perte + montant),
 *       affectation du resultat (reserve legale, reserves facultatives, report a nouveau,
 *       dividendes), distribution de dividendes le cas echeant (montant total, par part,
 *       date de mise en paiement), quitus a la gerance, conventions reglementees / CAC.</li>
 *   <li><b>Generation</b> — PV d'approbation ({@code PV_APPROBATION_COMPTES_SARL(/_AU)})
 *       + rapport de gestion ({@code RAPPORT_GESTION}, OPTIONNEL) + optionnels de seance
 *       (feuille de presence, defaut de quorum, irregularite de convocation).
 *       <b>Aucune annonce legale</b> : l'approbation des comptes n'est pas opposable aux
 *       tiers, aucun modele directeur d'avis n'existe pour ce type.</li>
 *   <li><b>Pieces jointes</b> — depot des versions legalisees (OPTIONNELLE).</li>
 *   <li><b>Synthese</b> — recap + archivage.</li>
 * </ol>
 *
 * <p><b>Refonte 4 -> 5 etapes.</b> L'ancien parcours melangeait la seance et l'approbation
 * a l'etape 2, generait a l'etape 3 et validait a l'etape 4 : la validation du PV occupait
 * une etape entiere. Elle rejoint la generation, ce qui libere l'etape de pieces jointes.
 * L'ancienne etape 1 exigeait {@code formeJuridique} dans le payload alors que cette
 * donnee est en BD : elle est desormais lue depuis le dossier.
 */
@Component
public class PvAgoWorkflow extends AbstractWorkflow {

    /** Delai minimum convocation -> assemblee (noyau partage). */
    public static final int CONVOCATION_DELAI_JOURS = WorkflowSteps.CONVOCATION_DELAI_JOURS;

    /** L'approbation des comptes ne se decide pas sur une societe qui n'est plus en activite. */
    private static final Set<String> STATUTS_INCOMPATIBLES =
            Set.of("DISSOUTE", "EN_LIQUIDATION", "LIQUIDEE", "RADIE");

    private static final Set<String> RESULTAT_TYPES = Set.of("benefice", "perte");

    @Override
    public WorkflowType type() {
        return WorkflowType.PV_AGO;
    }

    @Override
    protected StepResult doExecuteStep(StepContext ctx) {
        return switch (ctx.step()) {
            case 1 -> stepSociete(ctx);
            case 2 -> stepDonneesPv(ctx);
            case 3 -> stepGeneration(ctx);
            case 4 -> stepPiecesJointes(ctx);
            case 5 -> stepSynthese(ctx);
            default -> StepResult.blocked("\u00c9tape inconnue : " + ctx.step());
        };
    }

    // ------------------------------------------------------------------
    //  Step 1 : societe (BD) + date AGO + convocation + exercice clos
    // ------------------------------------------------------------------
    private StepResult stepSociete(StepContext ctx) {
        Map<String, Object> p = ctx.payload();

        String dossierIdRaw = strOrNull(p.get("dossierId"));
        if (dossierIdRaw == null) {
            return StepResult.blocked("S\u00e9lectionnez la soci\u00e9t\u00e9 dont les comptes sont approuv\u00e9s.");
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
                    "Cette soci\u00e9t\u00e9 est au statut \u00ab " + statut + " \u00bb : l\u2019approbation annuelle "
                            + "des comptes ne s\u2019y applique plus.");
        }

        // Exercice clos : annee ou date complete.
        String exerciceClos = strOrNull(first0(p.get("exerciceClos"), p.get("exerciceClosDate")));
        if (exerciceClos == null) {
            return StepResult.blocked("L\u2019exercice clos \u00e0 approuver est obligatoire.");
        }
        LocalDate exerciceClosDate = resolveExerciceClos(exerciceClos);
        if (exerciceClosDate == null) {
            return StepResult.blocked(
                    "Exercice clos invalide. Attendu : une annee (AAAA) ou une date (AAAA-MM-JJ).");
        }

        // Date de l'AGO — assemblee ORDINAIRE par nature : aucun choix de type.
        String dateRaw = strOrNull(first0(p.get("dateAGO"), p.get("dateAG"), p.get("dateAssemblee")));
        if (dateRaw == null) {
            return StepResult.blocked(
                    "La date de l\u2019assembl\u00e9e g\u00e9n\u00e9rale ordinaire est obligatoire.");
        }
        LocalDate dateAgo = parseDateOrNull(dateRaw);
        if (dateAgo == null) {
            return StepResult.blocked("Date d\u2019assembl\u00e9e invalide. Format attendu : AAAA-MM-JJ.");
        }
        if (dateAgo.isAfter(LocalDate.now().plusDays(30))) {
            return StepResult.blocked(
                    "La date d\u2019assembl\u00e9e ne peut pas \u00eatre dans le futur (> 30 jours).");
        }
        if (dateAgo.isBefore(LocalDate.now().minusYears(2))) {
            return StepResult.blocked(
                    "La date d\u2019assembl\u00e9e est trop ancienne (> 2 ans). V\u00e9rifiez la saisie.");
        }
        // L'AGO d'approbation se tient APRES la cloture de l'exercice qu'elle approuve.
        if (dateAgo.isBefore(exerciceClosDate)) {
            return StepResult.blocked(
                    "L\u2019assembl\u00e9e (" + dateRaw + ") ne peut pas se tenir AVANT la cl\u00f4ture de "
                            + "l\u2019exercice qu\u2019elle approuve (" + exerciceClosDate + ").");
        }

        // Convocation OPTIONNELLE — regle DURE des 16 jours (jours calendaires).
        String convocationError =
                WorkflowSteps.convocationDelaiError(p.get("convocation"), dateAgo);
        if (convocationError != null) return StepResult.blocked(convocationError);

        String forme = strOrNull(dossier.get("formeJuridique"));
        boolean isAu = isAssocieUnique(forme);

        Map<String, Object> out = new HashMap<>();
        out.put("dossierId", dossierIdRaw);
        out.put("exerciceClos", exerciceClos);
        out.put("exerciceClosDate", exerciceClosDate.toString());
        out.put("dateAGO", dateRaw);
        // L'approbation des comptes est TOUJOURS ordinaire : la valeur est imposee,
        // jamais choisie (aucun selecteur de type cote front).
        out.put("assembleeNature", "ordinaire");
        Object convocation = p.get("convocation");
        if (convocation != null) out.put("convocation", convocation);
        if (forme != null) {
            out.put("formeJuridique", forme);
            out.put("associeUnique", isAu);
        }
        putIfPresent(out, "denomination", dossier.get("denomination"));
        putIfPresent(out, "ice", dossier.get("ice"));
        out.put("decisionType", isAu ? "AU" : "AGO");
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 2 : donnees du PV (resultat, affectation, dividendes, quitus…)
    // ------------------------------------------------------------------
    private StepResult stepDonneesPv(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        Map<String, Object> a = new LinkedHashMap<>(asMap(first0(
                p.get("approbation"), p.get("etatsFinanciers"))));
        // Champs plats acceptes (retro-compatibilite scripts / anciens payloads).
        for (String k : new String[]{
                "exerciceClosDate", "commissairePresent", "commissaireNom",
                "resultatType", "resultatNet", "affectations",
                "dividendeDistribue", "dividendeMontantTotal", "dividendeParPart",
                "dividendeMiseEnPaiementDate", "quitusGerance",
                "conventionsReglementees", "conventionsDetail"}) {
            if (!a.containsKey(k) && p.get(k) != null) a.put(k, p.get(k));
        }

        // Resultat de l'exercice : type + montant.
        String resultatType = normResultatType(a.get("resultatType"));
        if (resultatType == null) {
            return StepResult.blocked(
                    "Pr\u00e9cisez le r\u00e9sultat de l\u2019exercice : b\u00e9n\u00e9fice ou perte.");
        }
        Long resultatNet = toLong(a.get("resultatNet"));
        if (resultatNet == null) {
            return StepResult.blocked(
                    "Le montant du r\u00e9sultat de l\u2019exercice est obligatoire (en dirhams).");
        }
        a.put("resultatType", resultatType);
        a.put("resultatNet", resultatNet);

        // Affectation du resultat : au moins une ligne, et la somme doit equilibrer.
        List<?> affectations = asList(a, "affectations");
        if (affectations.isEmpty()) {
            return StepResult.blocked(
                    "L\u2019affectation du r\u00e9sultat est obligatoire : r\u00e9serve l\u00e9gale, r\u00e9serves "
                            + "facultatives, report \u00e0 nouveau, dividendes\u2026");
        }
        long somme = 0L;
        for (Object row : affectations) {
            Map<String, Object> r = asMap(row);
            Long montant = toLong(first0(r.get("montant"), r.get("montantChiffres")));
            if (strOrNull(r.get("libelle")) == null) {
                return StepResult.blocked(
                        "Chaque ligne d\u2019affectation doit porter un libell\u00e9 (r\u00e9serve l\u00e9gale, "
                                + "report \u00e0 nouveau, dividendes\u2026).");
            }
            if (montant == null) {
                return StepResult.blocked(
                        "Chaque ligne d\u2019affectation doit porter un montant.");
            }
            somme += montant;
        }
        long aAffecter = Math.abs(resultatNet);
        if (somme != aAffecter) {
            return StepResult.blocked(
                    "L\u2019affectation ne solde pas le r\u00e9sultat : " + somme + " DH affect\u00e9s pour "
                            + aAffecter + " DH \u00e0 affecter. Ajustez les montants.");
        }

        // Dividendes : si distribution annoncee, le detail publie devient obligatoire.
        String dividendeDistribue = normOuiNon(a.get("dividendeDistribue"), "non");
        a.put("dividendeDistribue", dividendeDistribue);
        if ("oui".equals(dividendeDistribue)) {
            Long total = toLong(first0(a.get("dividendeMontantTotal"), a.get("dividendeTotal")));
            if (total == null || total <= 0) {
                return StepResult.blocked(
                        "Distribution annonc\u00e9e : pr\u00e9cisez le montant TOTAL des dividendes.");
            }
            if (total > aAffecter) {
                return StepResult.blocked(
                        "Le montant total des dividendes (" + total + " DH) d\u00e9passe le r\u00e9sultat "
                                + "a affecter (" + aAffecter + " DH).");
            }
            if (toLong(a.get("dividendeParPart")) == null) {
                return StepResult.blocked(
                        "Distribution annonc\u00e9e : pr\u00e9cisez le dividende par part sociale.");
            }
            String miseEnPaiement = strOrNull(a.get("dividendeMiseEnPaiementDate"));
            if (miseEnPaiement == null) {
                return StepResult.blocked(
                        "Distribution annonc\u00e9e : pr\u00e9cisez la date de mise en paiement.");
            }
            if (parseDateOrNull(miseEnPaiement) == null) {
                return StepResult.blocked(
                        "Date de mise en paiement invalide. Format attendu : AAAA-MM-JJ.");
            }
            a.put("dividendeMontantTotal", total);
        } else {
            // Une distribution saisie puis decochee ne doit pas fuir dans le PV.
            a.remove("dividendeMontantTotal");
            a.remove("dividendeTotal");
            a.remove("dividendeParPart");
            a.remove("dividendeMiseEnPaiementDate");
        }

        // Quitus a la gerance (defaut : accorde) et conventions reglementees.
        a.put("quitusGerance", normOuiNon(a.get("quitusGerance"), "oui"));
        String conventions = normOuiNon(a.get("conventionsReglementees"), "non");
        a.put("conventionsReglementees", conventions);
        if ("oui".equals(conventions) && strOrNull(a.get("conventionsDetail")) == null) {
            return StepResult.blocked(
                    "Conventions reglementees annoncees : decrivez-les (elles figurent dans "
                            + "une r\u00e9solution du PV).");
        }
        if (!"oui".equals(conventions)) {
            a.remove("conventionsDetail");
        }

        // Commissaire aux comptes : si present, son nom est requis (il est nomme au PV).
        String cacPresent = normOuiNon(a.get("commissairePresent"), "non");
        a.put("commissairePresent", cacPresent);
        if ("oui".equals(cacPresent) && strOrNull(a.get("commissaireNom")) == null) {
            return StepResult.blocked(
                    "Commissaire aux comptes present : son nom est obligatoire (il est nomme "
                            + "dans le proc\u00e8s-verbal).");
        }
        if (!"oui".equals(cacPresent)) {
            a.remove("commissaireNom");
        }

        Map<String, Object> out = new HashMap<>();
        out.put("approbation", a);
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 3 : generation (PV + rapport de gestion OPTIONNEL)
    // ------------------------------------------------------------------
    private StepResult stepGeneration(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        if (!isTrue(p, "pvValide")) {
            return StepResult.blocked(
                    "Le PV d\u2019approbation des comptes doit \u00eatre valide avant de poursuivre.");
        }

        Map<String, Object> dossier = readDossier(ctx);
        String forme = strOrNull(dossier.get("formeJuridique"));
        boolean isAu = isAssocieUnique(forme);

        Map<String, Object> out = new HashMap<>();
        out.put("pvValide", true);
        out.put("pvTemplate", isAu
                ? "PV_APPROBATION_COMPTES_SARL_AU"
                : "PV_APPROBATION_COMPTES_SARL");
        // Rapport de gestion : OPTIONNEL (recommande, jamais bloquant). Le directeur
        // n'en fait pas une condition de tenue de l'assemblee.
        out.put("rapportGestionTemplate", "RAPPORT_GESTION");
        out.put("rapportGestionValide", isTrue(p, "rapportGestionValide"));
        // Aucune annonce legale : l'approbation des comptes n'est pas opposable aux tiers.
        out.put("annonceRequise", false);
        if (forme != null) {
            out.put("formeJuridique", forme);
            out.put("associeUnique", isAu);
        }
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
        out.put("archiveLe", java.time.Instant.now().toString());
        out.put("finalise", true);
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /** Accepte une annee (AAAA -> 31/12) ou une date complete (AAAA-MM-JJ). */
    static LocalDate resolveExerciceClos(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.matches("\\d{4}")) {
            return LocalDate.of(Integer.parseInt(s), 12, 31);
        }
        return parseDateOrNull(s);
    }

    /** Type de resultat normalise sur les libelles du modele (« benefice » / « perte »). */
    private static String normResultatType(Object raw) {
        String s = norm(raw);
        if (s.isEmpty()) return null;
        if (s.startsWith("benef") || s.startsWith("profit")) return "benefice";
        if (s.startsWith("pert") || s.startsWith("defic")) return "perte";
        return RESULTAT_TYPES.contains(s) ? s : null;
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

    private static String norm(Object raw) {
        if (raw == null) return "";
        String n = java.text.Normalizer.normalize(String.valueOf(raw), java.text.Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readDossier(StepContext ctx) {
        Object v = ctx.existingData() == null ? null : ctx.existingData().get("dossier");
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
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

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(o).trim().replaceAll("[\\s\\u00A0\\u202F_]", "");
            return s.isEmpty() ? null : Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate parseDateOrNull(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException ignore) {
            return null;
        }
    }
}
