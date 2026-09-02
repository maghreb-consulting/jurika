package ma.jurika.workflow.domain.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ma.jurika.common.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Couvre la refonte 2026-06-25 du workflow IMPORT (11 etapes : meme tronc de
 * SAISIE que la CREATION + 3 uploads typés + suivi + synthèse).
 */
class ImportWorkflowTest {

    private final ImportWorkflow workflow = new ImportWorkflow();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID dossierId = UUID.randomUUID();

    private StepResult run(int step, Map<String, Object> payload, Map<String, Object> existing) {
        return workflow.executeStep(new StepContext(
                workspaceId, ticketId, userId, step, payload, existing));
    }

    private Map<String, Object> validDenomination() {
        Map<String, Object> m = new HashMap<>();
        m.put("denomination", "ATLAS SARL");
        m.put("ice", "001234567890123");
        m.put("rcNumero", "123456");
        m.put("ifNumero", "98765432");
        m.put("formeJuridique", "SARL");
        return m;
    }

    @Test
    @DisplayName("IMPORT compte 11 etapes")
    void totalSteps() {
        assertThat(workflow.totalSteps()).isEqualTo(11);
    }

    @Test
    @DisplayName("Step 1 valide expose denomination en nested + ICE normalise")
    void step1Ok() {
        StepResult r = run(1, validDenomination(), Map.of());
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsKey("denomination");
        @SuppressWarnings("unchecked")
        Map<String, Object> den = (Map<String, Object>) r.stepData().get("denomination");
        assertThat(den.get("ice")).isEqualTo("001234567890123");
        assertThat(den.get("rcNumero")).isEqualTo("123456");
        assertThat(r.stepData().get("formeJuridique")).isEqualTo("SARL");
    }

    @Test
    @DisplayName("Step 1 rejette un RC manquant (champ requis)")
    void step1RcManquant() {
        Map<String, Object> p = validDenomination();
        p.remove("rcNumero");
        assertThatThrownBy(() -> run(1, p, Map.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("RC");
    }

    @Test
    @DisplayName("Step 1 rejette un IF manquant (champ requis)")
    void step1IfManquant() {
        Map<String, Object> p = validDenomination();
        p.remove("ifNumero");
        assertThatThrownBy(() -> run(1, p, Map.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("IF");
    }

    @Test
    @DisplayName("Step 1 ICE doit avoir 15 chiffres (RG-C13)")
    void step1IceInvalide() {
        Map<String, Object> p = validDenomination();
        p.put("ice", "12345");
        StepResult r = run(1, p, Map.of());
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("ICE invalide");
    }

    @Test
    @DisplayName("Step 1 ne force PAS le certificat negatif (purement CREATION)")
    void step1PasDeCnRequis() {
        // validDenomination ne porte aucun champ CN -> doit passer.
        assertThat(run(1, validDenomination(), Map.of()).canAdvance()).isTrue();
    }

    @Test
    @DisplayName("Step 3 calcule la valeur nominale ; pas de controle depot/25%")
    void step3CapitalParts() {
        Map<String, Object> p = new HashMap<>();
        p.put("apportNumeraire", 100000);
        p.put("nombreParts", 1000);
        // Aucun depotBanqueNom / capitalLibere -> NE DOIT PAS bloquer.
        StepResult r = run(3, p, Map.of());
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> cap = (Map<String, Object>) r.stepData().get("capital");
        assertThat(cap.get("nombreParts")).isEqualTo(1000);
        assertThat(cap.get("capitalSocialMad")).isNotNull();
        assertThat(cap.get("valeurNominaleMad")).isNotNull();
    }

    @Test
    @DisplayName("Step 3 accepte capitalSocialMad direct (societe existante)")
    void step3CapitalDirect() {
        Map<String, Object> p = new HashMap<>();
        p.put("capitalSocialMad", 300000);
        p.put("nombreParts", 3000);
        StepResult r = run(3, p, Map.of());
        assertThat(r.canAdvance()).isTrue();
    }

    @Test
    @DisplayName("Step 6 SARL : somme des parts doit egaler le total step3")
    void step6SommeParts() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("step3", Map.of("capital", Map.of("nombreParts", 1000)));

        Map<String, Object> ok = Map.of("associes", List.of(
                Map.of("nom", "BENATIK", "prenom", "Oussama", "cin", "BK1", "nombreParts", 600),
                Map.of("nom", "ALAOUI", "prenom", "Salma", "cin", "BK2", "nombreParts", 400)),
                "formeJuridique", "SARL");
        assertThat(run(6, ok, existing).canAdvance()).isTrue();

        Map<String, Object> ko = Map.of("associes", List.of(
                Map.of("nom", "BENATIK", "prenom", "Oussama", "cin", "BK1", "nombreParts", 700)),
                "formeJuridique", "SARL");
        StepResult r = run(6, ko, existing);
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("Somme parts");
    }

    @Test
    @DisplayName("Step 7 accepte une liste de documents juridiques whitelistes")
    void step7DocsOk() {
        Map<String, Object> payload = Map.of("documents", List.of(
                Map.of("type", "STATUTS", "filename", "statuts.pdf", "uploaded", true),
                Map.of("type", "RC", "filename", "rc.pdf", "uploaded", true)));
        StepResult r = run(7, payload, Map.of());
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsKey("juridique");
    }

    @Test
    @DisplayName("Step 8/9 (comptable/fiscal) sont optionnels et persistent le payload")
    void step8et9Optionnels() {
        assertThat(run(8, Map.of("documentsFinanciers", List.of()), Map.of()).canAdvance()).isTrue();
        assertThat(run(9, Map.of("documentsFiscaux", List.of()), Map.of()).canAdvance()).isTrue();
    }

    @Test
    @DisplayName("Step 10 (Suivi) persiste regime TVA + annee + anterieures filtrees")
    void step10Suivi() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("regimeTvaMensuel", false);
        payload.put("anneeExercice", 2025);
        payload.put("anneesAnterieuresSelectionnees", List.of(2023, 2024, 2025, 2030));
        StepResult r = run(10, payload, Map.of());
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> suivi = (Map<String, Object>) r.stepData().get("suivi");
        assertThat(suivi.get("regimeTvaMensuel")).isEqualTo(false);
        assertThat(suivi.get("anneeExercice")).isEqualTo(2025);
        @SuppressWarnings("unchecked")
        List<Object> ant = (List<Object>) suivi.get("anneesAnterieuresSelectionnees");
        assertThat(ant).containsExactly(2023, 2024);
    }

    @Test
    @DisplayName("Step 11 (Synthese) consolide une fiche COMPLETE depuis les step bags")
    void step11SyntheseComplete() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("step1", Map.of("denomination", validDenomination()));
        existing.put("step2", Map.of("siege", Map.of(
                "adresse", "12 rue des Cedres, Maarif", "commune", "Casablanca")));
        existing.put("step3", Map.of("capital", Map.of(
                "capitalSocialMad", 300000, "valeurNominaleMad", 100, "nombreParts", 3000)));
        existing.put("step4", Map.of("activite", Map.of(
                "description", "Conseil juridique", "dateDebutExercice", "2025-01-01")));
        existing.put("step5", Map.of("dirigeants", Map.of(
                "dirigeants", List.of(Map.of("nom", "BENATIK", "prenom", "Oussama", "cinNumero", "BK1")),
                "gerance", Map.of("dureeMandat", "ILLIMITEE", "remunerationMode", "GRATUIT"))));
        existing.put("step6", Map.of("associes", Map.of(
                "associes", List.of(
                        Map.of("nom", "BENATIK", "prenom", "Oussama", "cin", "BK1", "nombreParts", 3000)))));
        existing.put("step7", Map.of("juridique", Map.of(
                "documents", List.of(Map.of("type", "STATUTS", "filename", "statuts.pdf")))));
        existing.put("step10", Map.of("suivi", Map.of(
                "regimeTvaMensuel", true, "anneeExercice", 2025,
                "anneesAnterieuresSelectionnees", List.of(2024))));

        StepResult r = run(11, Map.of("validated", true), existing);
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> synthese = (Map<String, Object>) r.stepData().get("synthese");
        assertThat(synthese.get("importComplete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> fiche = (Map<String, Object>) synthese.get("ficheJuridique");
        assertThat(fiche.get("raisonSociale")).isEqualTo("ATLAS SARL");
        assertThat(fiche.get("ice")).isEqualTo("001234567890123");
        assertThat(fiche.get("rcNumero")).isEqualTo("123456");
        assertThat(fiche.get("ifNumero")).isEqualTo("98765432");
        assertThat(fiche.get("siegeAdresse")).isEqualTo("12 rue des Cedres, Maarif");
        assertThat(fiche.get("siegeVille")).isEqualTo("Casablanca");
        assertThat(fiche.get("objetSocial")).isEqualTo("Conseil juridique");
        assertThat(fiche.get("capitalSocial")).isEqualTo(300000);
        assertThat(fiche).containsKey("associes");
        assertThat(fiche).containsKey("dirigeants");
        // Le suivi est reporte dans la synthese.
        @SuppressWarnings("unchecked")
        Map<String, Object> suivi = (Map<String, Object>) synthese.get("suivi");
        assertThat(suivi.get("anneeExercice")).isEqualTo(2025);
    }

    // =====================================================================
    //  Non-regression C1 (simulation 2026-08-15, GROUPE 0) — traversee 11/11
    // =====================================================================

    /**
     * Le defaut C1 rendait l'IMPORT <b>infranchissable</b> : l'etape 4 exige
     * {@code dateDebutExercice}, mais la de-dup d'aout 2026 avait retire ce champ
     * de l'UI de l'etape 4 pendant que l'etape 3 le masque en {@code importMode}.
     * Les tests existants validaient chaque etape ISOLEMENT — aucun ne parcourait
     * la chaine complete, donc aucun ne voyait le blocage.
     *
     * <p>Ces deux tests deroulent les 11 etapes d'affilee en enchainant les
     * {@code stepData} comme le fait {@code WorkflowUseCases}, et exigent que la
     * synthese finale sorte {@code importComplete = true}. Toute exigence de champ
     * qui ne serait plus alimentable ferait echouer la traversee ici.
     */
    @Test
    @DisplayName("C1 — IMPORT SARL : les 11 etapes s'enchainent jusqu'a importComplete")
    void traverseeComplete_sarl() {
        Map<String, Object> fiche = traverse("SARL", List.of(
                Map.of("nom", "EL AMRANI", "prenom", "Youssef", "cin", "BK1", "nombreParts", 600),
                Map.of("nom", "BENNANI", "prenom", "Salma", "cin", "BK2", "nombreParts", 400)));

        assertThat(fiche.get("raisonSociale")).isEqualTo("ATLAS SARL");
        assertThat(fiche.get("formeJuridique")).isEqualTo("SARL");
        // La date qui bloquait tout doit avoir traverse jusqu'a la fiche consolidee.
        assertThat(fiche.get("dateDebutExercice")).isEqualTo("2024-01-01");
        @SuppressWarnings("unchecked")
        List<Object> associes = (List<Object>) fiche.get("associes");
        assertThat(associes).hasSize(2);
    }

    @Test
    @DisplayName("C1 — IMPORT SARL AU : les 11 etapes s'enchainent jusqu'a importComplete")
    void traverseeComplete_sarlAu() {
        Map<String, Object> fiche = traverse("SARL_AU", List.of(
                Map.of("nom", "BENJELLOUN", "prenom", "Salma", "cin", "BK9", "nombreParts", 1000)));

        assertThat(fiche.get("formeJuridique")).isEqualTo("SARL_AU");
        assertThat(fiche.get("dateDebutExercice")).isEqualTo("2024-01-01");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> associes = (List<Map<String, Object>>) fiche.get("associes");
        assertThat(associes).hasSize(1);
        assertThat(associes.get(0).get("pourcentageDetention")).isEqualTo(100);
    }

    /**
     * Deroule les 11 etapes en accumulant les {@code stepData} produits, comme le
     * fait l'orchestrateur, et renvoie la fiche juridique consolidee par la synthese.
     */
    private Map<String, Object> traverse(String forme, List<Map<String, Object>> associes) {
        Map<String, Object> acc = new HashMap<>();

        Map<String, Object> p1 = validDenomination();
        p1.put("formeJuridique", forme);
        acc.put("step1", stepOk(1, p1, acc));

        acc.put("step2", stepOk(2, Map.of(
                "adresse", "12 rue des Cedres, Maarif",
                "province", "Casablanca-Settat",
                "commune", "Casablanca",
                "codePostal", "20000"), acc));

        acc.put("step3", stepOk(3, Map.of(
                "capitalSocialMad", 100000, "nombreParts", 1000), acc));

        // L'etape du defaut C1 : `description` ET `dateDebutExercice` sont exiges.
        acc.put("step4", stepOk(4, Map.of(
                "description", "Negoce et distribution de produits alimentaires",
                "dateDebutExercice", "2024-01-01"), acc));

        acc.put("step5", stepOk(5, Map.of(
                "dirigeants", List.of(Map.of(
                        "nom", "EL AMRANI", "prenom", "Youssef", "cinNumero", "BK1")),
                "gerance", Map.of(
                        "dureeMandat", "ILLIMITEE", "remunerationMode", "GRATUIT")), acc));

        acc.put("step6", stepOk(6, Map.of(
                "associes", associes, "formeJuridique", forme), acc));

        acc.put("step7", stepOk(7, Map.of("documents", List.of(
                Map.of("type", "STATUTS", "filename", "statuts.pdf", "uploaded", true),
                Map.of("type", "RC", "filename", "rc.pdf", "uploaded", true))), acc));

        acc.put("step8", stepOk(8, Map.of("documentsFinanciers", List.of()), acc));
        acc.put("step9", stepOk(9, Map.of("documentsFiscaux", List.of()), acc));

        acc.put("step10", stepOk(10, Map.of(
                "regimeTvaMensuel", true,
                "anneeExercice", 2026,
                "anneesAnterieuresSelectionnees", List.of(2024)), acc));

        Map<String, Object> synthese11 = stepOk(11, Map.of("validated", true), acc);
        @SuppressWarnings("unchecked")
        Map<String, Object> synthese = (Map<String, Object>) synthese11.get("synthese");
        assertThat(synthese.get("importComplete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> fiche = (Map<String, Object>) synthese.get("ficheJuridique");
        return fiche;
    }

    /** Execute une etape et exige qu'elle passe : un blocage arrete la traversee. */
    private Map<String, Object> stepOk(int step, Map<String, Object> payload,
                                       Map<String, Object> acc) {
        StepResult r = run(step, payload, acc);
        assertThat(r.canAdvance())
                .describedAs("Etape %d refusee : %s", step, r.message())
                .isTrue();
        return r.stepData();
    }

    @Test
    @DisplayName("Step 11 bloque si l'etape 1 (denomination) est absente")
    void step11BloqueSansDenomination() {
        Map<String, Object> existing = Map.of(
                "step7", Map.of("juridique", Map.of(
                        "documents", List.of(Map.of("type", "RC", "filename", "rc.pdf")))));
        StepResult r = run(11, Map.of("validated", true), existing);
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("Etape 1");
    }
}
