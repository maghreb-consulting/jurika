package ma.jurika.workflow.domain.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreationSarlWorkflowTest {

    private final CreationSarlWorkflow workflow = new CreationSarlWorkflow();
    private final UUID ws = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @Test
    @DisplayName("Step 3 capital : 25% non libere => blocage")
    void capitalNonLibere25() {
        Map<String, Object> payload = Map.of(
                "apportNumeraire", 100000,
                "apportNature", 0,
                "apportIndustrie", 0,
                "capitalLibere", 20000,
                "nombreParts", 1000
        );
        var ctx = new StepContext(ws, ticket, user, 3, payload, Map.of());
        StepResult result = workflow.executeStep(ctx);
        assertThat(result.canAdvance()).isFalse();
        assertThat(result.message()).contains("25%");
    }

    @Test
    @DisplayName("Step 3 capital : 25% libere exactement => OK")
    void capital25LibereOk() {
        Map<String, Object> payload = Map.of(
                "apportNumeraire", 100000,
                "apportNature", 0,
                "apportIndustrie", 0,
                "capitalLibere", 25000,
                "nombreParts", 1000,
                // C1 2026-06-21 — depot bancaire desormais OBLIGATOIRE (politique stricte).
                "depotBanqueNom", "Attijariwafa Bank",
                "depotNumero", "007 780 1234567890123 45"
        );
        var ctx = new StepContext(ws, ticket, user, 3, payload, Map.of());
        StepResult result = workflow.executeStep(ctx);
        assertThat(result.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> capital = (Map<String, Object>) result.stepData().get("capital");
        assertThat((BigDecimal) capital.get("capitalSocialMad")).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("Step 2 siege : type bail OK")
    void siegeBailOk() {
        Map<String, Object> payload = Map.of(
                "adresse", "1 rue X", "province", "Casa", "commune", "Casa",
                "codePostal", "20000", "justificatifType", "BAIL"
        );
        var ctx = new StepContext(ws, ticket, user, 2, payload, Map.of());
        assertThat(workflow.executeStep(ctx).canAdvance()).isTrue();
    }

    @Test
    @DisplayName("Step 2 siege : type invalide => blocage")
    void siegeTypeInvalide() {
        Map<String, Object> payload = Map.of(
                "adresse", "1 rue X", "province", "Casa", "commune", "Casa",
                "codePostal", "20000", "justificatifType", "AUTRE"
        );
        var ctx = new StepContext(ws, ticket, user, 2, payload, Map.of());
        StepResult result = workflow.executeStep(ctx);
        assertThat(result.canAdvance()).isFalse();
    }

    @Test
    @DisplayName("Step 7 : statuts non valides => blocage")
    void statutsNonValides() {
        var ctx = new StepContext(ws, ticket, user, 7,
                Map.of("statutsDocumentId", "doc-1", "statutsValides", false), Map.of());
        StepResult result = workflow.executeStep(ctx);
        assertThat(result.canAdvance()).isFalse();
        assertThat(result.message()).contains("statuts");
    }

    @Test
    @DisplayName("Step 8 : AUCUNE piece obligatoire => l'etape avance meme sans piece")
    @SuppressWarnings("unchecked")
    void piecesNonBloquant() {
        // Politique produit 2026-08 : Step8 non bloquant (aligne sur le front,
        // requiredOk=true). Un payload vide (aucune piece) doit avancer.
        var ctx = new StepContext(ws, ticket, user, 8,
                Map.of("piecesUploaded", List.of()), Map.of());
        StepResult result = workflow.executeStep(ctx);
        assertThat(result.canAdvance()).isTrue();
        Map<String, Object> pieces = (Map<String, Object>) result.stepData().get("pieces");
        assertThat((List<Object>) pieces.get("uploaded")).isEmpty();
    }

    @Test
    @DisplayName("Step 8 : CIN manquante n'est PLUS bloquante + payload preserve")
    @SuppressWarnings("unchecked")
    void piecesCinManquanteNonBloquante() {
        // Meme avec des personnes attendues sans leur CIN, l'etape avance ; le
        // payload (piecesUploaded, cinPersonnes) est preserve pour la re-hydratation.
        Map<String, Object> payload = Map.of(
                "piecesUploaded", List.of("CIN_DIRIGEANT_abc12345"),
                "cinPersonnes", List.of(
                        Map.of("code", "CIN_DIRIGEANT_abc12345", "label", "CIN — Ali Alaoui (Dirigeant)"),
                        Map.of("code", "CIN_ASSOCIE_def67890", "label", "CIN — Sara Bennani (Associe)")));
        var ctx = new StepContext(ws, ticket, user, 8, payload, Map.of());
        StepResult result = workflow.executeStep(ctx);
        assertThat(result.canAdvance()).isTrue();
        assertThat((List<Object>) result.stepData().get("cinPersonnes")).hasSize(2);
    }

    @Test
    @DisplayName("Step 5 : la validation preserve signataires + gouvernance + CAC (0 champ perdu)")
    @SuppressWarnings("unchecked")
    void dirigeantsPreserveToutLePayload() {
        // Bug persistance « bulletproof » : handleDirigeants reconstruisait une
        // sortie partielle qui droppait `signataires` (et les autres cles Step5
        // hors dirigeants/gerance). Ce test prouve qu'executeStep re-stocke
        // l'integralite des cles necessaires a la re-hydratation du composant.
        Map<String, Object> gerance = Map.of(
                "dureeMandat", "illimitée",
                "remunerationMode", "non rémunéré",
                "gerantModeDesignation", "statutaire",
                "dureeGerance", "99 années",
                "modeSignature", "séparée",
                "modeSignatureAdmin", "identique");
        Map<String, Object> payload = Map.of(
                "dirigeants", List.of(Map.of(
                        "typePersonne", "PHYSIQUE",
                        "nom", "Alaoui", "prenom", "Ali", "cinNumero", "BK12345",
                        "isStatutaire", true)),
                "gerance", gerance,
                "cacNomme", true,
                "cacNom", "Cabinet Fiduciaire XYZ",
                "signataires", List.of(
                        Map.of("nom", "Ali Alaoui", "qualite", "Gérant")));
        var ctx = new StepContext(ws, ticket, user, 5, payload, Map.of());
        StepResult result = workflow.executeStep(ctx);
        assertThat(result.canAdvance()).isTrue();

        Map<String, Object> out = result.stepData();
        // La cle qui disparaissait : signataires (art. 15) survit a la validation.
        List<Map<String, Object>> signataires =
                (List<Map<String, Object>>) out.get("signataires");
        assertThat(signataires).hasSize(1);
        assertThat(signataires.get(0).get("qualite")).isEqualTo("Gérant");
        // Les autres cles Step5 sont egalement preservees.
        assertThat(out.get("cacNomme")).isEqualTo(true);
        assertThat(out.get("cacNom")).isEqualTo("Cabinet Fiduciaire XYZ");
        Map<String, Object> geranceOut = (Map<String, Object>) out.get("gerance");
        assertThat(geranceOut.get("gerantModeDesignation")).isEqualTo("statutaire");
        assertThat(geranceOut.get("modeSignatureAdmin")).isEqualTo("identique");
        assertThat((List<Object>) out.get("dirigeants")).hasSize(1);
    }
}
