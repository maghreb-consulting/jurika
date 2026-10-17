package ma.jurika.workflow.domain.strategy;

import ma.jurika.workflow.domain.model.WorkflowType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
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
 * Workflow LIQUIDATION (4 etapes) — spec directeur 2026-08-13.
 *
 * <p>Etapes cibles :
 * <ol>
 *   <li><b>Saisie</b> — societe <b>DISSOUTE</b>, date de l'AGE de cloture (assemblee
 *       EXTRAORDINAIRE uniquement, ou decision de l'associe unique en SARL AU), comptes
 *       finaux (total actif / total passif, controles par le rapport final), convocation
 *       OPTIONNELLE (regle des 16 jours). L'identite societe, la <b>date de dissolution</b>
 *       et le <b>liquidateur</b> sont repris de la BD.</li>
 *   <li><b>Generation</b> — PV de cloture ({@code PV_DISSOLUTION_LIQUIDATION_*}, etape
 *       « cloture ») + rapport du liquidateur ({@code RAPPORT_LIQUIDATION_DIRECTEUR}) +
 *       <b>annonce legale de cloture</b> ({@code ANNONCE_LEGALE_LIQUIDATION_*}). Feuille
 *       de presence et PV d'incident restent optionnels (non bloquants).</li>
 *   <li><b>Pieces jointes</b> — depot des versions legalisees (OPTIONNELLE, jamais
 *       bloquante : l'etape peut rester vide).</li>
 *   <li><b>Synthese</b> — recap ; la finalisation passe le dossier en {@code LIQUIDEE}.</li>
 * </ol>
 *
 * <p><b>Zero re-saisie</b> (RG transverse) :
 * <ul>
 *   <li>l'identite societe (denomination, capital, siege, RC, ville du greffe) est lue en BD
 *       ({@code ctx.existingData().get("dossier")} ici, {@code SocieteIdentityEnricher} cote
 *       ai-service) ;</li>
 *   <li>la <b>date de dissolution</b> est en base ({@code entreprise_dossiers.date_dissolution},
 *       ecrite par le workflow Dissolution) : elle n'est plus saisie. Un dossier ancien sans
 *       date en base autorise une saisie de secours ;</li>
 *   <li>le <b>liquidateur</b> a ete nomme A LA DISSOLUTION et est relu depuis la fiche
 *       structuree. Cas de secours identique pour les dossiers dissous avant cette evolution
 *       (selection/saisie une seule fois, puis persistance — cf. {@code WorkflowUseCases}) ;</li>
 *   <li>le boni / mali n'est ni saisi ni stocke ici : il se calcule au rapport final du
 *       liquidateur (L6b E19, regle unique cote ai-service).</li>
 * </ul>
 *
 * <p><b>Regle des 15 jours (RG-LI03, DURE)</b> : une societe ne peut etre liquidee que
 * 15 jours au minimum apres sa dissolution. Le controle porte sur l'ecart
 * {@code dateCloture - dateDissolution} (et non sur « aujourd'hui ») et se calcule en jours
 * calendaires via {@link WorkflowSteps#liquidationDelaiError} — insensible au fuseau horaire
 * et aux changements d'heure. Le front applique le meme calcul ; ce garde-fou serveur le rend
 * non contournable.
 */
@Component
public class LiquidationWorkflow extends AbstractWorkflow {

    /**
     * Delai minimum dissolution -> cloture de la liquidation (RG-LI03).
     *
     * @deprecated valeur historique erronee (16) et avertissement non bloquant. Utiliser
     *             {@link WorkflowSteps#LIQUIDATION_DELAI_JOURS} (15, regle DURE).
     */
    @Deprecated
    public static final int DELAI_LEGAL_JOURS = WorkflowSteps.LIQUIDATION_DELAI_JOURS;

    /** Delai minimum convocation -> assemblee (partage avec Modification / Dissolution). */
    public static final int CONVOCATION_DELAI_JOURS = WorkflowSteps.CONVOCATION_DELAI_JOURS;

    /** Seules les societes dissoutes sont liquidables (RG-LI02). */
    private static final Set<String> STATUTS_AUTORISES = Set.of("DISSOUTE", "EN_LIQUIDATION");

    /** Origine du liquidateur : lu en BD (nomme a la dissolution) ou saisi (cas de secours). */
    private static final Set<String> LIQUIDATEUR_SOURCES = Set.of("BD", "EXTERNE");

    @Override
    public WorkflowType type() {
        return WorkflowType.LIQUIDATION;
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
    //  Step 1 : saisie (societe DISSOUTE, date AGE de cloture, comptes
    //           finaux, liquidateur BD, convocation optionnelle)
    // ------------------------------------------------------------------
    private StepResult stepSaisie(StepContext ctx) {
        Map<String, Object> p = ctx.payload();

        // --- Societe : UUID strict + statut DISSOUTE ---
        String dossierIdRaw = strOrNull(p.get("dossierId"));
        if (dossierIdRaw == null) {
            return StepResult.blocked("S\u00e9lectionnez la soci\u00e9t\u00e9 \u00e0 liquider.");
        }
        try {
            UUID.fromString(dossierIdRaw);
        } catch (IllegalArgumentException e) {
            return StepResult.blocked(
                    "Identifiant de soci\u00e9t\u00e9 invalide. S\u00e9lectionnez la soci\u00e9t\u00e9 dans la liste.");
        }
        Map<String, Object> dossier = readDossier(ctx);
        String statut = strOrNull(dossier.get("statut"));
        if (statut != null && !STATUTS_AUTORISES.contains(statut)) {
            return StepResult.blocked(
                    "Cette soci\u00e9t\u00e9 (statut \u00ab " + statut + " \u00bb) ne peut pas \u00eatre liquid\u00e9e. "
                            + "Seules les soci\u00e9t\u00e9s dissoutes peuvent \u00eatre liquid\u00e9es.");
        }

        // --- Date de dissolution : LUE EN BD, saisie de secours si absente ---
        // Priorite a la base (le workflow Dissolution l'y a ecrite) ; le payload ne sert
        // que de repli pour les dossiers dissous avant cette evolution.
        String dateDissolutionRaw = strOrNull(first0(
                dossier.get("dateDissolution"), p.get("dateDissolution")));
        if (dateDissolutionRaw == null) {
            return StepResult.blocked(
                    "La date de dissolution est introuvable en base pour cette soci\u00e9t\u00e9. "
                            + "Renseignez-la (dossier ant\u00e9rieur \u00e0 l\u2019enregistrement automatique).");
        }
        LocalDate dateDissolution = parseDateOrNull(dateDissolutionRaw);
        if (dateDissolution == null) {
            return StepResult.blocked(
                    "Date de dissolution invalide. Format attendu : AAAA-MM-JJ.");
        }
        if (dateDissolution.isAfter(LocalDate.now())) {
            return StepResult.blocked("La date de dissolution ne peut pas \u00eatre dans le futur.");
        }

        // --- Date de l'AGE de CLOTURE (extraordinaire uniquement) ---
        String dateClotureRaw = strOrNull(first0(
                p.get("dateClotureLiquidation"), p.get("dateCloture"), p.get("dateAGE")));
        if (dateClotureRaw == null) {
            return StepResult.blocked(
                    "La date de l\u2019assembl\u00e9e g\u00e9n\u00e9rale extraordinaire de cl\u00f4ture de la "
                            + "liquidation est obligatoire.");
        }
        LocalDate dateCloture = parseDateOrNull(dateClotureRaw);
        if (dateCloture == null) {
            return StepResult.blocked(
                    "Date de cl\u00f4ture invalide. Format attendu : AAAA-MM-JJ.");
        }
        if (dateCloture.isAfter(LocalDate.now().plusDays(30))) {
            return StepResult.blocked(
                    "La date de cl\u00f4ture ne peut pas \u00eatre dans le futur (> 30 jours).");
        }

        // --- RG-LI03 : regle DURE des 15 jours (jours calendaires, dissolution -> cloture) ---
        String delaiError = WorkflowSteps.liquidationDelaiError(dateDissolution, dateCloture);
        if (delaiError != null) return StepResult.blocked(delaiError);

        // --- Liquidateur : BD d'abord (nomme a la dissolution), secours sinon ---
        Map<String, Object> liquidateurBd = asMap(dossier.get("liquidateur"));
        Map<String, Object> liquidateurIn = asMap(p.get("liquidateur"));
        // La BD gagne : un liquidateur enregistre n'est JAMAIS remplace par une saisie.
        Map<String, Object> liquidateur =
                liquidateurBd.isEmpty() ? liquidateurIn : liquidateurBd;
        // Lot L4d (E5) : personne physique (nom) ou personne morale (denomination).
        boolean liquidateurMorale = WorkflowSteps.liquidateurPersonneMorale(liquidateur);
        String liquidateurNom = WorkflowSteps.identiteLiquidateur(liquidateur);
        if (liquidateurNom == null) {
            return StepResult.blocked(
                    "Aucun liquidateur n\u2019est enregistr\u00e9 pour cette soci\u00e9t\u00e9 : d\u00e9signez-le "
                            + "(g\u00e9rant ou associ\u00e9 existant, ou liquidateur externe). Il sera "
                            + "ensuite repris automatiquement.");
        }
        String source = strOrNull(liquidateur.get("source"));
        if (source != null && !LIQUIDATEUR_SOURCES.contains(source.toUpperCase(Locale.ROOT))) {
            return StepResult.blocked(
                    "Origine du liquidateur invalide : attendu « BD » (partie prenante du "
                            + "dossier) ou « EXTERNE » (saisie manuelle).");
        }

        // --- Comptes finaux : total actif et total passif (controles par le rapport final) ---
        Map<String, Object> cf = asMap(first0(p.get("comptesFinaux"), p.get("cloture")));
        BigDecimal actif = asNonNegativeAmount(first0(cf.get("totalActif"), cf.get("actifRealise")));
        BigDecimal passif = asNonNegativeAmount(first0(cf.get("totalPassif"), cf.get("passifRegle")));
        if (actif == null || passif == null) {
            return StepResult.blocked(
                    "Les comptes finaux de liquidation sont obligatoires : total actif et "
                            + "total passif (MAD, valeurs positives ou nulles).");
        }
        // L6b (E19) : aucun resultat ici. actif - passif n'est pas le resultat de la liquidation
        // (frais, impot et remboursement du capital viennent ensuite) ; il se calcule au rapport
        // final du liquidateur (ai-service, ResultatLiquidation), seule source du PV et du rapport.

        // --- Convocation OPTIONNELLE — regle DURE des 16 jours (jours calendaires) ---
        String convocationError =
                WorkflowSteps.convocationDelaiError(p.get("convocation"), dateCloture);
        if (convocationError != null) return StepResult.blocked(convocationError);

        // --- Sortie ---
        Map<String, Object> out = new HashMap<>();
        out.put("dossierId", dossierIdRaw);
        out.put("dateDissolution", dateDissolutionRaw);
        out.put("dateClotureLiquidation", dateClotureRaw);
        out.put("joursDepuisDissolution",
                java.time.temporal.ChronoUnit.DAYS.between(dateDissolution, dateCloture));
        // Faits dossier propages pour ai-service / front.
        if (!dossier.isEmpty()) {
            out.put("formeJuridique", strOrNull(dossier.get("formeJuridique")));
            out.put("denomination", strOrNull(dossier.get("denomination")));
            out.put("ice", strOrNull(dossier.get("ice")));
        }
        // Liquidateur normalise (identite + origine) — consomme tel quel par les mappers.
        Map<String, Object> liq = new LinkedHashMap<>(liquidateur);
        if (!liquidateurMorale) liq.put("nom", liquidateurNom);
        liq.put("source", source == null
                ? (liquidateurBd.isEmpty() ? "EXTERNE" : "BD")
                : source.toUpperCase(Locale.ROOT));
        // Vrai si le liquidateur vient de la base : le front l'affiche en lecture seule.
        out.put("liquidateurDepuisBd", !liquidateurBd.isEmpty());
        out.put("liquidateur", liq);
        String siegeLiquidation = strOrNull(first0(
                dossier.get("siegeLiquidation"), p.get("siegeLiquidation"), liq.get("siege")));
        if (siegeLiquidation != null) out.put("siegeLiquidation", siegeLiquidation);

        // Comptes finaux (le resultat se calcule au rapport final).
        Map<String, Object> comptes = new HashMap<>();
        comptes.put("totalActif", actif);
        comptes.put("totalPassif", passif);
        comptes.put("devise", first0(cf.get("devise"), "MAD"));
        out.put("comptesFinaux", comptes);

        Object convocation = p.get("convocation");
        if (convocation != null) out.put("convocation", convocation);

        // --- Modeles selectionnes pour ai-service ---
        String forme = strOrNull(out.get("formeJuridique"));
        boolean isAu = "SARL_AU".equals(forme);
        // PV directeur unifie dissolution/liquidation, ici a l'etape « cloture ».
        out.put("pvTemplate", isAu
                ? "PV_DISSOLUTION_LIQUIDATION_SARL_AU"
                : "PV_DISSOLUTION_LIQUIDATION_SARL");
        out.put("pvEtape", "clôture");
        out.put("rapportTemplate", "RAPPORT_LIQUIDATION_DIRECTEUR");
        out.put("annonceTemplate", isAu
                ? "ANNONCE_LEGALE_LIQUIDATION_SARL_AU"
                : "ANNONCE_LEGALE_LIQUIDATION_SARL");
        // La cloture est toujours extraordinaire / decision de l'associe unique.
        out.put("assembleeNature", "extraordinaire");
        out.put("decisionType", isAu ? "AU" : "AGE");
        // Avertissement PRECOCE (non bloquant ici) : la finalisation exigera que toutes
        // les succursales soient fermees. Autant que l'employe le sache des l'etape 1
        // plutot qu'apres avoir genere tous les actes.
        List<String> succursalesOuvertes = succursalesOuvertes(ctx);
        out.put("succursalesOuvertes", succursalesOuvertes);
        out.put("succursalesOuvertesCount", succursalesOuvertes.size());
        return StepResult.ok(out);
    }

    // ------------------------------------------------------------------
    //  Step 2 : generation (PV de cloture + rapport + annonce legale)
    // ------------------------------------------------------------------
    private StepResult stepGeneration(StepContext ctx) {
        Map<String, Object> p = ctx.payload();
        if (!isTrue(p, "pvValide")) {
            return StepResult.blocked(
                    "Le PV de cl\u00f4ture de la liquidation doit \u00eatre valide avant de poursuivre.");
        }
        if (!isTrue(p, "rapportValide")) {
            return StepResult.blocked(
                    "Le rapport de liquidation doit \u00eatre g\u00e9n\u00e9r\u00e9 et valide.");
        }
        if (!isTrue(p, "annonceValide")) {
            return StepResult.blocked(
                    "L\u2019annonce l\u00e9gale de cl\u00f4ture de liquidation doit \u00eatre valid\u00e9e : sa "
                            + "publication au Journal d\u2019Annonces L\u00e9gales fonde la radiation au "
                            + "registre du commerce (les num\u00e9ro et date de d\u00e9p\u00f4t l\u00e9gal, attribu\u00e9s "
                            + "par le greffe apr\u00e8s d\u00e9p\u00f4t, restent facultatifs \u00e0 ce stade).");
        }
        Map<String, Object> out = new HashMap<>();
        out.put("pvValide", true);
        out.put("rapportValide", true);
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
    //  Step 4 : synthese + finalisation
    // ------------------------------------------------------------------
    private StepResult stepSynthese(StepContext ctx) {
        // GARDE BLOQUANTE (2026-08-13) — une societe ne peut pas etre liquidee tant
        // qu'elle exploite encore des succursales. Une succursale n'a pas de
        // personnalite juridique distincte : elle doit etre FERMEE (PV + annonce +
        // radiation au RC de son lieu d'exploitation) AVANT la cloture de la
        // liquidation de sa mere. Sans cette garde, les succursales restaient
        // « ACTIVE » sous une societe liquidee — et devenaient infermables, le
        // workflow de fermeture ne proposant que des meres au statut ACTIVE.
        List<String> ouvertes = succursalesOuvertes(ctx);
        if (!ouvertes.isEmpty()) {
            return StepResult.blocked(
                    "Cette soci\u00e9t\u00e9 exploite encore " + ouvertes.size() + " succursale(s) : "
                            + String.join(", ", ouvertes) + ". Fermez-les d\u2019abord (parcours "
                            + "\u00ab Fermeture de succursale \u00bb) : elles ne peuvent pas survivre \u00e0 "
                            + "la radiation de leur soci\u00e9t\u00e9 m\u00e8re.");
        }

        Map<String, Object> out = new HashMap<>();
        out.put("societeStatut", "LIQUIDEE");
        out.put("finaliseLe", java.time.Instant.now().toString());
        out.put("finalise", true);
        // Trace : la garde ci-dessus a ete franchie, aucune succursale ne subsistait.
        out.put("succursalesOuvertes", List.of());
        out.put("succursalesOuvertesCount", 0);
        out.put("nextSuggestion", "Engager la radiation au Registre du Commerce.");
        return StepResult.ok(out);
    }

    /**
     * Libelles des succursales encore ACTIVE de la societe, injectes en
     * {@code existingData} par {@code WorkflowUseCases} (le domaine ne lit pas la base).
     * Liste vide = aucune succursale ouverte, ou information indisponible.
     */
    private static List<String> succursalesOuvertes(StepContext ctx) {
        Object v = ctx.existingData() == null ? null : ctx.existingData().get("succursalesOuvertes");
        if (!(v instanceof List<?> l)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : l) {
            String s = o == null ? null : o.toString().trim();
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
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

    private static BigDecimal asNonNegativeAmount(Object v) {
        if (v == null) return null;
        try {
            BigDecimal b;
            if (v instanceof BigDecimal bd) b = bd;
            else if (v instanceof Number n) b = BigDecimal.valueOf(n.doubleValue());
            else b = new BigDecimal(v.toString().trim());
            return b.signum() >= 0 ? b : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
