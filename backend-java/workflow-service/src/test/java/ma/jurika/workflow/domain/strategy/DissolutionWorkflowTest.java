package ma.jurika.workflow.domain.strategy;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.workflow.domain.model.WorkflowType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Couvre le workflow DISSOLUTION en 4 etapes (spec directeur, 2026-08-12) :
 * <ul>
 *   <li>Etape 1 — saisie complete : societe, motif, date AGE (extraordinaire uniquement),
 *       liquidateur (BD ou externe), siege de la liquidation, convocation 16 jours.</li>
 *   <li>Etape 2 — generation : PV + annonce legale (depot legal facultatif).</li>
 *   <li>Etape 3 — pieces jointes optionnelles (jamais bloquantes, peuvent etre vides).</li>
 *   <li>Etape 4 — synthese / finalisation.</li>
 * </ul>
 */
class DissolutionWorkflowTest {

    private final DissolutionWorkflow workflow = new DissolutionWorkflow();
    private final UUID ws = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID dossier = UUID.randomUUID();

    private static final String MOTIF =
            "Cessation definitive de l'activite commerciale de la societe.";

    /** existingData avec faits dossier injectes par WorkflowUseCases. */
    private Map<String, Object> existingWithDossier(String forme) {
        Map<String, Object> facts = new HashMap<>();
        facts.put("dossierId", dossier.toString());
        facts.put("denomination", "PARACOSME");
        facts.put("statut", "ACTIVE");
        if (forme != null) facts.put("formeJuridique", forme);
        return Map.of("dossier", facts);
    }

    private Map<String, Object> liquidateur(String source) {
        Map<String, Object> l = new HashMap<>();
        l.put("source", source);
        l.put("civilite", "M.");
        l.put("prenom", "Ahmed");
        l.put("nom", "ALAOUI");
        l.put("adresse", "45 BD ZERKTOUNI, CASABLANCA");
        return l;
    }

    /** Payload etape 1 complet et valide (liquidateur choisi en BD). */
    private Map<String, Object> step1Payload() {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("motifDissolution", MOTIF);
        p.put("dateAGE", LocalDate.now().minusDays(1).toString());
        p.put("liquidateur", liquidateur("BD"));
        p.put("siegeLiquidation", "12 RUE DES FOULES, CASABLANCA");
        return p;
    }

    private StepResult run(int step, Map<String, Object> payload, String forme) {
        return workflow.executeStep(
                new StepContext(ws, ticket, user, step, payload, existingWithDossier(forme)));
    }

    // ── Structure ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Le workflow DISSOLUTION compte 4 etapes")
    void totalSteps_estQuatre() {
        assertThat(WorkflowType.DISSOLUTION.totalSteps()).isEqualTo(4);
        assertThat(workflow.totalSteps()).isEqualTo(4);
    }

    @Test
    @DisplayName("Etape 5 (hors plage) → rejetee")
    void etapeHorsPlage_rejetee() {
        assertThatThrownBy(() -> run(5, Map.of(), "SARL"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("1..4");
    }

    // ── Etape 1 : saisie ───────────────────────────────────────────────

    @Test
    @DisplayName("Step 1 : saisie complete SARL → OK, PV + annonce SARL, AGE extraordinaire")
    void step1_complet_sarl() {
        StepResult res = run(1, step1Payload(), "SARL");
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("pvTemplate", "PV_DISSOLUTION_LIQUIDATION_SARL");
        assertThat(res.stepData()).containsEntry("annonceTemplate", "ANNONCE_LEGALE_DISSOLUTION_SARL");
        assertThat(res.stepData()).containsEntry("pvEtape", "dissolution");
        // La dissolution est TOUJOURS extraordinaire (aucun choix ordinaire offert).
        assertThat(res.stepData()).containsEntry("assembleeNature", "extraordinaire");
        assertThat(res.stepData()).containsEntry("decisionType", "AGE");
        assertThat(res.stepData()).containsEntry("siegeLiquidation", "12 RUE DES FOULES, CASABLANCA");
    }

    @Test
    @DisplayName("Step 1 : SARL AU → templates AU + decision de l'associe unique")
    void step1_complet_sarlAu() {
        StepResult res = run(1, step1Payload(), "SARL_AU");
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("pvTemplate", "PV_DISSOLUTION_LIQUIDATION_SARL_AU");
        assertThat(res.stepData())
                .containsEntry("annonceTemplate", "ANNONCE_LEGALE_DISSOLUTION_SARL_AU");
        assertThat(res.stepData()).containsEntry("decisionType", "AU");
    }

    @Test
    @DisplayName("Step 1 : liquidateur absent → blocage")
    void step1_liquidateurAbsent_blocage() {
        Map<String, Object> p = step1Payload();
        p.remove("liquidateur");
        StepResult res = run(1, p, "SARL");
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("liquidateur");
    }

    @Test
    @DisplayName("Step 1 : liquidateur externe sans adresse → blocage (adresse publiee au JAL)")
    void step1_liquidateurExterneSansAdresse_blocage() {
        Map<String, Object> p = step1Payload();
        Map<String, Object> l = liquidateur("EXTERNE");
        l.remove("adresse");
        p.put("liquidateur", l);
        StepResult res = run(1, p, "SARL");
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("adresse du liquidateur");
    }

    @Test
    @DisplayName("Step 1 : liquidateur externe complet → OK, source normalisee")
    void step1_liquidateurExterne_ok() {
        Map<String, Object> p = step1Payload();
        p.put("liquidateur", liquidateur("externe"));
        StepResult res = run(1, p, "SARL");
        assertThat(res.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> liq = (Map<String, Object>) res.stepData().get("liquidateur");
        assertThat(liq).containsEntry("source", "EXTERNE");
        assertThat(liq).containsEntry("siege", "12 RUE DES FOULES, CASABLANCA");
    }

    @Test
    @DisplayName("Step 1 : origine du liquidateur inconnue → blocage")
    void step1_sourceLiquidateurInvalide_blocage() {
        Map<String, Object> p = step1Payload();
        p.put("liquidateur", liquidateur("AUTRE"));
        StepResult res = run(1, p, "SARL");
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("Origine du liquidateur");
    }

    @Test
    @DisplayName("Step 1 : siege de la liquidation absent → blocage")
    void step1_siegeLiquidationAbsent_blocage() {
        Map<String, Object> p = step1Payload();
        p.remove("siegeLiquidation");
        StepResult res = run(1, p, "SARL");
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("siege de la liquidation");
    }

    @Test
    @DisplayName("Step 1 : siege de la liquidation repris du liquidateur → OK")
    void step1_siegeLiquidationDuLiquidateur_ok() {
        Map<String, Object> p = step1Payload();
        p.remove("siegeLiquidation");
        Map<String, Object> l = liquidateur("BD");
        l.put("siege", "9 RUE DE PARIS, RABAT");
        p.put("liquidateur", l);
        StepResult res = run(1, p, "SARL");
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("siegeLiquidation", "9 RUE DE PARIS, RABAT");
    }

    @Test
    @DisplayName("Step 1 : motif trop court → blocage")
    void step1_motifTropCourt_blocage() {
        Map<String, Object> p = step1Payload();
        p.put("motifDissolution", "trop court");
        StepResult res = run(1, p, "SARL");
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("trop court");
    }

    @Test
    @DisplayName("Step 1 : societe deja DISSOUTE → blocage")
    void step1_societeDejaDissoute_blocage() {
        Map<String, Object> facts = new HashMap<>();
        facts.put("dossierId", dossier.toString());
        facts.put("formeJuridique", "SARL");
        facts.put("statut", "DISSOUTE");
        StepResult res = workflow.executeStep(new StepContext(
                ws, ticket, user, 1, step1Payload(), Map.of("dossier", facts)));
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("DISSOUTE");
    }

    // ── Etape 1 : convocation OPTIONNELLE, regle des 16 jours ──────────

    @Test
    @DisplayName("Step 1 : convocation absente → OK (optionnelle)")
    void step1_sansConvocation_ok() {
        assertThat(run(1, step1Payload(), "SARL").canAdvance()).isTrue();
    }

    @Test
    @DisplayName("Step 1 : convocation a 16 jours pile → OK")
    void step1_convocation16Jours_ok() {
        LocalDate age = LocalDate.now().minusDays(1);
        Map<String, Object> p = step1Payload();
        p.put("dateAGE", age.toString());
        p.put("convocation", Map.of("date", age.minusDays(16).toString(), "heure", "10H00"));
        StepResult res = run(1, p, "SARL");
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsKey("convocation");
    }

    @Test
    @DisplayName("Step 1 : convocation a 15 jours → blocage (16 jours requis)")
    void step1_convocation15Jours_blocage() {
        LocalDate age = LocalDate.now().minusDays(1);
        Map<String, Object> p = step1Payload();
        p.put("dateAGE", age.toString());
        p.put("convocation", Map.of("date", age.minusDays(15).toString()));
        StepResult res = run(1, p, "SARL");
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("16");
    }

    @Test
    @DisplayName("Regle des 16 jours : calcul en jours CALENDAIRES (insensible au fuseau)")
    void convocationDelai_joursCalendaires() {
        // Passage a l'heure d'ete en Europe/Casablanca : la duree reelle n'est pas
        // un multiple de 24 h, le calcul LocalDate doit rester exact.
        LocalDate assemblee = LocalDate.of(2026, 4, 10);
        assertThat(WorkflowSteps.convocationDelaiError(
                Map.of("date", "2026-03-25"), assemblee)).isNull();          // 16 j
        assertThat(WorkflowSteps.convocationDelaiError(
                Map.of("date", "2026-03-26"), assemblee)).isNotNull();       // 15 j
        assertThat(WorkflowSteps.convocationDelaiError(null, assemblee)).isNull();
        assertThat(WorkflowSteps.convocationDelaiError(
                Map.of("date", ""), assemblee)).isNull();
    }

    // ── Etape 2 : generation PV + annonce ─────────────────────────────

    @Test
    @DisplayName("Step 2 : PV non valide → blocage")
    void step2_pvNonValide_blocage() {
        StepResult res = run(2, Map.of("annonceValide", true), "SARL");
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("PV de dissolution");
    }

    @Test
    @DisplayName("Step 2 : annonce legale non validee → blocage")
    void step2_annonceNonValide_blocage() {
        StepResult res = run(2, Map.of("pvValide", true), "SARL");
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("annonce legale");
    }

    @Test
    @DisplayName("Step 2 : PV + annonce valides, sans depot legal → OK (depot legal facultatif)")
    void step2_sansDepotLegal_ok() {
        StepResult res = run(2, Map.of("pvValide", true, "annonceValide", true), "SARL");
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).doesNotContainKey("depotLegal");
    }

    @Test
    @DisplayName("Step 2 : depot legal renseigne → persiste pour la synthese")
    void step2_avecDepotLegal_ok() {
        Map<String, Object> p = new HashMap<>();
        p.put("pvValide", true);
        p.put("annonceValide", true);
        p.put("depotLegal", Map.of("numero", "78945", "date", "2026-06-01"));
        StepResult res = run(2, p, "SARL");
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsKey("depotLegal");
    }

    // ── Etape 3 : pieces jointes OPTIONNELLES ─────────────────────────

    @Test
    @DisplayName("Step 3 : aucune piece jointe → OK (etape facultative)")
    void step3_vide_ok() {
        StepResult res = run(3, Map.of(), "SARL");
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("piecesJointesCount", 0);
    }

    @Test
    @DisplayName("Step 3 : pieces jointes deposees → comptees pour la synthese")
    void step3_avecPieces_ok() {
        Map<String, Object> p = new HashMap<>();
        p.put("piecesJointes", List.of(
                Map.of("id", "pv-legalise", "label", "PV legalise", "filename", "PV.pdf"),
                Map.of("id", "annonce", "label", "Annonce publiee", "filename", "JAL.pdf")));
        StepResult res = run(3, p, "SARL");
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("piecesJointesCount", 2);
    }

    // ── Etape 4 : synthese ────────────────────────────────────────────

    @Test
    @DisplayName("Step 4 : synthese → statut DISSOUTE + finalise")
    void step4_synthese_ok() {
        StepResult res = run(4, Map.of(), "SARL");
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("societeStatut", "DISSOUTE");
        assertThat(res.stepData()).containsEntry("finalise", true);
    }
}
