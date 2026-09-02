package ma.jurika.workflow.domain.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Couvre le workflow MODIFICATION en 5 etapes (voie directeur unifiee, 2026-08-11) :
 * <ul>
 *   <li>Etape 1 — selection dossier + decisions (resolutionType) + convocation 16 jours.</li>
 *   <li>Etape 4 — pieces jointes optionnelles (jamais bloquantes).</li>
 *   <li>Etape 5 — finalisation (double validation PV + statuts).</li>
 * </ul>
 */
class ModificationWorkflowTest {

    private final ModificationWorkflow workflow = new ModificationWorkflow();
    private final UUID ws = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID dossier = UUID.randomUUID();

    private Map<String, Object> step3Data(boolean jalRequis) {
        Map<String, Object> step3 = new HashMap<>();
        step3.put("jalRequis", jalRequis);
        return Map.of("step3", step3);
    }

    /** existingData avec faits dossier injectes par WorkflowUseCases. */
    private Map<String, Object> existingWithDossier(String forme) {
        Map<String, Object> facts = new HashMap<>();
        facts.put("dossierId", dossier.toString());
        if (forme != null) facts.put("formeJuridique", forme);
        return Map.of("dossier", facts);
    }

    private Map<String, Object> decision(String id, String rt) {
        Map<String, Object> d = new HashMap<>();
        d.put("id", id);
        d.put("resolutionType", rt);
        return d;
    }

    // ── Etape 1 : sanity sur la selection / coherence forme juridique ───

    @Test
    @DisplayName("Step 1 : aucune modification selectionnee → blocage")
    void step1_selectionVide_blocage() {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("selectedTypes", List.of());
        p.put("decisionType", "AGE");
        p.put("datePV", "2026-07-01");
        var ctx = new StepContext(ws, ticket, user, 1, p, existingWithDossier("SARL"));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("au moins une");
    }

    @Test
    @DisplayName("Step 1 : SARL_AU + AGE → blocage RG-M02")
    void step1_sarlAu_avecAge_blocage() {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("selectedTypes", List.of("modification_denomination"));
        p.put("decisionType", "AGE");
        p.put("datePV", "2026-07-01");
        var ctx = new StepContext(ws, ticket, user, 1, p, existingWithDossier("SARL_AU"));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("associe unique");
    }

    @Test
    @DisplayName("Step 1 : dossierId manquant → blocage \"Selectionnez la societe\"")
    void step1_dossierIdManquant_blocage() {
        Map<String, Object> p = new HashMap<>();
        p.put("selectedTypes", List.of("modification_denomination"));
        p.put("decisionType", "AGE");
        p.put("datePV", "2026-07-01");
        var ctx = new StepContext(ws, ticket, user, 1, p, Map.of());
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).containsIgnoringCase("societe");
    }

    @Test
    @DisplayName("Step 1 : dossierId invalide (pas un UUID) → blocage")
    void step1_dossierIdInvalide_blocage() {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", "not-a-uuid");
        p.put("selectedTypes", List.of("modification_denomination"));
        p.put("decisionType", "AGE");
        p.put("datePV", "2026-07-01");
        var ctx = new StepContext(ws, ticket, user, 1, p, existingWithDossier("SARL"));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).containsIgnoringCase("invalide");
    }

    @Test
    @DisplayName("Step 1 : dossier inconnu (existingData.dossier vide) → blocage")
    void step1_dossierInconnu_blocage() {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("selectedTypes", List.of("modification_denomination"));
        p.put("decisionType", "AGE");
        p.put("datePV", "2026-07-01");
        var ctx = new StepContext(ws, ticket, user, 1, p, Map.of());
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).containsIgnoringCase("introuvable");
    }

    @Test
    @DisplayName("Step 1 : selectedTypes snake_case (resolutionType) valides → ok")
    void step1_selectedTypesSnakeCase_ok() {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("selectedTypes", List.of("modification_denomination"));
        p.put("decisionType", "AGE");
        p.put("datePV", "2026-07-01");
        var ctx = new StepContext(ws, ticket, user, 1, p, existingWithDossier("SARL"));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("dossierId", dossier.toString());
        assertThat(res.stepData()).containsEntry("selectedTypes", List.of("modification_denomination"));
    }

    // Non-regression du 400 : un resolutionType snake_case ne doit JAMAIS tomber dans
    // un chemin legacy UPPERCASE (retire). Le libelle « Type de modification non
    // reconnu » ne doit plus exister.
    @Test
    @DisplayName("Step 1 : selectedDecisions valides → ok + selectedTypes = resolutionTypes")
    void step1_directeur_selectionValide_ok() {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("selectedDecisions", List.of(
                decision("sarl-age-transfert-siege-autre", "transfert_siege"),
                decision("sarl-ago-approbation-comptes", "approbation_comptes")));
        p.put("decisionType", "AGE");
        p.put("datePV", "2026-07-01");
        var ctx = new StepContext(ws, ticket, user, 1, p, existingWithDossier("SARL"));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("selectedTypes",
                List.of("transfert_siege", "approbation_comptes"));
        // transfert_siege declenche le JAL (RG-M14).
        assertThat(res.stepData()).containsEntry("jalRequis", true);
        assertThat(res.stepData()).containsKey("selectedDecisions");
    }

    @Test
    @DisplayName("Step 1 : resolutionType inconnu → blocage")
    void step1_directeur_resolutionInconnu_blocage() {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("selectedDecisions", List.of(decision("x", "type_bidon")));
        p.put("decisionType", "AGE");
        p.put("datePV", "2026-07-01");
        var ctx = new StepContext(ws, ticket, user, 1, p, existingWithDossier("SARL"));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).containsIgnoringCase("resolution");
    }

    @Test
    @DisplayName("Step 1 : cession pluripersonnelle en SARL_AU → formeChangeRequis")
    void step1_directeur_cessionAu_formeChange() {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("selectedDecisions",
                List.of(decision("au-ext-cession-pluripersonnelle", "cession_parts_pluripersonnelle")));
        p.put("decisionType", "AU");
        p.put("datePV", "2026-07-01");
        var ctx = new StepContext(ws, ticket, user, 1, p, existingWithDossier("SARL_AU"));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("formeChangeRequis", true);
    }

    // ── Etape 1 : convocation optionnelle — regle DURE des 16 jours ─────

    @Test
    @DisplayName("Step 1 : convocation < 16 jours avant l'assemblee → blocage")
    void step1_convocationTropTardive_blocage() {
        Map<String, Object> conv = new HashMap<>();
        conv.put("date", "2026-06-20"); // 11 jours avant le 2026-07-01
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("selectedTypes", List.of("modification_denomination"));
        p.put("decisionType", "AGE");
        p.put("datePV", "2026-07-01");
        p.put("convocation", conv);
        var ctx = new StepContext(ws, ticket, user, 1, p, existingWithDossier("SARL"));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).containsIgnoringCase("16 jours");
    }

    @Test
    @DisplayName("Step 1 : convocation >= 16 jours avant l'assemblee → ok")
    void step1_convocation16JoursOuPlus_ok() {
        Map<String, Object> conv = new HashMap<>();
        conv.put("date", "2026-06-10"); // 21 jours avant le 2026-07-01
        conv.put("heure", "10:00");
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("selectedTypes", List.of("modification_denomination"));
        p.put("decisionType", "AGE");
        p.put("datePV", "2026-07-01");
        p.put("convocation", conv);
        var ctx = new StepContext(ws, ticket, user, 1, p, existingWithDossier("SARL"));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsKey("convocation");
    }

    // ── Etape 2 : saisie (directeur — jamais bloquante ici) ─────────────

    @Test
    @DisplayName("Step 2 : resolutionTypes sans valeurs detaillees → ok (saisie via editeur)")
    void step2_directeur_sansValeurs_ok() {
        Map<String, Object> p = new HashMap<>();
        p.put("valeurs", new HashMap<>());
        var ctx = new StepContext(ws, ticket, user, 2, p, Map.of());
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isTrue();
    }

    // ── Etape 4 : pieces jointes OPTIONNELLES (jamais bloquantes) ───────

    @Test
    @DisplayName("Step 4 : aucune piece jointe → ok (etape optionnelle)")
    void step4_piecesVides_ok() {
        Map<String, Object> p = new HashMap<>();
        var ctx = new StepContext(ws, ticket, user, 4, p, Map.of());
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("piecesJointesCount", 0);
    }

    @Test
    @DisplayName("Step 4 : pieces jointes fournies → ok + compteur")
    void step4_avecPieces_ok() {
        Map<String, Object> p = new HashMap<>();
        p.put("piecesJointes", List.of(Map.of("id", "a"), Map.of("id", "b")));
        var ctx = new StepContext(ws, ticket, user, 4, p, Map.of());
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("piecesJointesCount", 2);
    }

    // ── Etape 5 : finalisation (double validation PV + statuts) ─────────

    @Test
    @DisplayName("Step 5 : pvValide=false → blocage")
    void step5_pvNonValide_blocage() {
        Map<String, Object> p = new HashMap<>();
        p.put("pvValide", false);
        p.put("statutsValides", true);
        var ctx = new StepContext(ws, ticket, user, 5, p, step3Data(false));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("proces-verbal");
    }

    @Test
    @DisplayName("Step 5 : statutsValides=false → blocage")
    void step5_statutsNonValides_blocage() {
        Map<String, Object> p = new HashMap<>();
        p.put("pvValide", true);
        p.put("statutsValides", false);
        var ctx = new StepContext(ws, ticket, user, 5, p, step3Data(false));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("statuts");
    }

    @Test
    @DisplayName("Step 5 : PV + statuts valides, JAL non requis → OK (finalise=true)")
    void step5_pvEtStatutsValides_sansJal_ok() {
        Map<String, Object> p = new HashMap<>();
        p.put("pvValide", true);
        p.put("statutsValides", true);
        var ctx = new StepContext(ws, ticket, user, 5, p, step3Data(false));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("finalise", true);
    }

    @Test
    @DisplayName("Step 5 : JAL requis + jalValide=false → blocage RG-M13")
    void step5_jalRequisManquant_blocage() {
        Map<String, Object> p = new HashMap<>();
        p.put("pvValide", true);
        p.put("statutsValides", true);
        p.put("jalValide", false);
        var ctx = new StepContext(ws, ticket, user, 5, p, step3Data(true));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("Journal d'Annonces Legales");
    }

    @Test
    @DisplayName("Step 5 : PV + statuts + JAL valides → OK (finalise=true)")
    void step5_pvStatutsJalValides_ok() {
        Map<String, Object> p = new HashMap<>();
        p.put("pvValide", true);
        p.put("statutsValides", true);
        p.put("jalValide", true);
        var ctx = new StepContext(ws, ticket, user, 5, p, step3Data(true));
        StepResult res = workflow.executeStep(ctx);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("finalise", true);
    }
}
