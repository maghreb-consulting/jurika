package ma.jurika.workflow.domain.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 (B.1) — Validation back des Options de constitution (voie directeur) au
 * Step 3 (capital). Non bloquant si absent ; libellés EXACTS des conditions des
 * modèles sinon (« intégrale »/« partielle », « séparée »/« séparée avec plafond »/
 * « conjointe »). Plafond requis si « séparée avec plafond ».
 */
class CreationSarlWorkflowDirecteurValidationTest {

    private final CreationSarlWorkflow workflow = new CreationSarlWorkflow();
    private final UUID ws = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    private Map<String, Object> baseCapital() {
        Map<String, Object> p = new HashMap<>();
        p.put("apportNumeraire", 100000);
        p.put("apportNature", 0);
        p.put("apportIndustrie", 0);
        p.put("capitalLibere", 100000);
        p.put("nombreParts", 1000);
        p.put("depotBanqueNom", "Attijariwafa Bank");
        p.put("depotNumero", "007 780 1234567890123 45");
        return p;
    }

    private StepResult run(Map<String, Object> p) {
        return workflow.executeStep(new StepContext(ws, ticket, user, 3, p, Map.of()));
    }

    @Test
    @DisplayName("Options absentes => OK (défauts mapper, non bloquant)")
    void optionsAbsentes_ok() {
        assertThat(run(baseCapital()).canAdvance()).isTrue();
    }

    @Test
    @DisplayName("modeSignature invalide => blocage")
    void modeSignatureInvalide_bloque() {
        Map<String, Object> p = baseCapital();
        p.put("modeSignature", "au petit bonheur");
        StepResult r = run(p);
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("signature");
    }

    @Test
    @DisplayName("« séparée avec plafond » sans plafond => blocage")
    void plafondManquant_bloque() {
        Map<String, Object> p = baseCapital();
        p.put("modeSignature", "séparée avec plafond");
        StepResult r = run(p);
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("Plafond");
    }

    @Test
    @DisplayName("Options valides (libellés exacts) => OK")
    void optionsValides_ok() {
        Map<String, Object> p = baseCapital();
        p.put("modeLiberation", "intégrale");
        p.put("modeSignature", "séparée avec plafond");
        p.put("signaturePlafond", 50000);
        assertThat(run(p).canAdvance()).isTrue();
    }

    @Test
    @DisplayName("modeLiberation invalide => blocage")
    void modeLiberationInvalide_bloque() {
        Map<String, Object> p = baseCapital();
        p.put("modeLiberation", "moitié");
        StepResult r = run(p);
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("liberation");
    }

    // ---- Phase 4 — contrat §2 : dépôt conditionnel, ICE optionnel, signature admin ----

    @Test
    @DisplayName("Dépôt NON bloqué sans banque => OK (§2.2 conditionnel)")
    void depotNonBloque_sansBanque_ok() {
        Map<String, Object> p = baseCapital();
        p.remove("depotBanqueNom");
        p.remove("depotNumero");
        p.put("depotFondsBloque", "non");
        assertThat(run(p).canAdvance()).isTrue();
    }

    @Test
    @DisplayName("Dépôt bloqué sans banque => blocage (§2.2)")
    void depotBloque_sansBanque_bloque() {
        Map<String, Object> p = baseCapital();
        p.remove("depotBanqueNom");
        p.put("depotFondsBloque", "oui");
        StepResult r = run(p);
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("Banque");
    }

    @Test
    @DisplayName("modeSignatureAdmin invalide => blocage")
    void modeSignatureAdminInvalide_bloque() {
        Map<String, Object> p = baseCapital();
        p.put("modeSignatureAdmin", "au feeling");
        StepResult r = run(p);
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("administrative");
    }

    @Test
    @DisplayName("modeSignatureAdmin « signature seule » => OK")
    void modeSignatureAdminValide_ok() {
        Map<String, Object> p = baseCapital();
        p.put("modeSignatureAdmin", "signature seule");
        assertThat(run(p).canAdvance()).isTrue();
    }

    @Test
    @DisplayName("ICE absent au Step 1 => OK (§2.10 attribué post-immat)")
    void iceAbsent_step1_ok() {
        Map<String, Object> p = new HashMap<>();
        p.put("denomination", "PARACOSME");
        p.put("cnNumero", "CN-12345");
        p.put("cnDate", "2026-01-10");
        p.put("activiteCn", "conseil");
        p.put("beneficiaire", "M. BENANI");
        p.put("formeJuridique", "SARL");
        StepResult r = workflow.executeStep(new StepContext(ws, ticket, user, 1, p, Map.of()));
        assertThat(r.canAdvance()).isTrue();
    }

    @Test
    @DisplayName("ICE fourni mais mal formé => blocage (RG-C13)")
    void iceMalForme_bloque() {
        Map<String, Object> p = new HashMap<>();
        p.put("denomination", "PARACOSME");
        p.put("cnNumero", "CN-12345");
        p.put("cnDate", "2026-01-10");
        p.put("activiteCn", "conseil");
        p.put("beneficiaire", "M. BENANI");
        p.put("formeJuridique", "SARL");
        p.put("ice", "12345");
        StepResult r = workflow.executeStep(new StepContext(ws, ticket, user, 1, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("ICE");
    }
}
