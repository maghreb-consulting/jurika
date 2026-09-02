package ma.jurika.workflow.domain.strategy;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.workflow.domain.model.WorkflowType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Couvre le workflow LIQUIDATION en 4 etapes (spec directeur, 2026-08-13) :
 * <ul>
 *   <li>Etape 1 — saisie : societe DISSOUTE, date de l'AGE de cloture (AGE only), comptes
 *       finaux -> boni/mali CALCULE, liquidateur + date de dissolution repris de la BD
 *       (anti-duplication) + cas de secours, convocation 16 jours, <b>regle DURE des
 *       15 jours</b> dissolution -> cloture.</li>
 *   <li>Etape 2 — generation : PV + rapport + annonce legale (depot legal facultatif).</li>
 *   <li>Etape 3 — pieces jointes optionnelles (jamais bloquantes, peuvent etre vides).</li>
 *   <li>Etape 4 — synthese : statut LIQUIDEE.</li>
 * </ul>
 */
class LiquidationWorkflowTest {

    private final LiquidationWorkflow workflow = new LiquidationWorkflow();
    private final UUID ws = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID dossier = UUID.randomUUID();

    /** Dissolution il y a 60 jours : le delai des 15 jours est largement respecte. */
    private static final LocalDate DATE_DISSOLUTION = LocalDate.now().minusDays(60);
    private static final LocalDate DATE_CLOTURE = LocalDate.now().minusDays(1);

    /** Liquidateur tel qu'il est enregistre en BD (nomme a la dissolution). */
    private static Map<String, Object> liquidateurBd() {
        Map<String, Object> l = new HashMap<>();
        l.put("source", "BD");
        l.put("civilite", "M.");
        l.put("prenom", "Ahmed");
        l.put("nom", "ALAOUI");
        l.put("adresse", "45 BD ZERKTOUNI, CASABLANCA");
        return l;
    }

    /**
     * existingData avec les faits dossier injectes par WorkflowUseCases : statut DISSOUTE,
     * date de dissolution ET liquidateur — tous LUS EN BASE.
     */
    private Map<String, Object> dossierFacts(String forme, boolean avecLiquidateur,
                                             LocalDate dateDissolution) {
        Map<String, Object> facts = new HashMap<>();
        facts.put("dossierId", dossier.toString());
        facts.put("denomination", "PARACOSME");
        facts.put("statut", "DISSOUTE");
        if (forme != null) facts.put("formeJuridique", forme);
        if (dateDissolution != null) facts.put("dateDissolution", dateDissolution.toString());
        if (avecLiquidateur) {
            facts.put("liquidateur", liquidateurBd());
            facts.put("siegeLiquidation", "12 RUE DES FOULES, CASABLANCA");
        }
        return facts;
    }

    /** Payload etape 1 : societe + date de cloture + comptes finaux. RIEN d'autre. */
    private Map<String, Object> step1Payload(long actif, long passif) {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", dossier.toString());
        p.put("dateClotureLiquidation", DATE_CLOTURE.toString());
        p.put("comptesFinaux", Map.of("totalActif", actif, "totalPassif", passif, "devise", "MAD"));
        return p;
    }

    private Map<String, Object> step1Payload() {
        return step1Payload(500_000L, 300_000L);
    }

    private StepResult run(int step, Map<String, Object> payload, Map<String, Object> facts) {
        return workflow.executeStep(new StepContext(
                ws, ticket, user, step, payload, Map.of("dossier", facts)));
    }

    private StepResult run(int step, Map<String, Object> payload) {
        return run(step, payload, dossierFacts("SARL", true, DATE_DISSOLUTION));
    }

    // ── Structure ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Le workflow LIQUIDATION compte 4 etapes")
    void totalSteps_estQuatre() {
        assertThat(WorkflowType.LIQUIDATION.totalSteps()).isEqualTo(4);
        assertThat(workflow.totalSteps()).isEqualTo(4);
    }

    @Test
    @DisplayName("Etape 5 (hors plage) → rejetee")
    void etapeHorsPlage_rejetee() {
        assertThatThrownBy(() -> run(5, Map.of()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("1..4");
    }

    // ── Etape 1 : selection de la societe ──────────────────────────────

    @Test
    @DisplayName("Step 1 : societe DISSOUTE → OK, PV de cloture + rapport + annonce SARL")
    void step1_complet_sarl() {
        StepResult res = run(1, step1Payload());
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("pvTemplate", "PV_DISSOLUTION_LIQUIDATION_SARL");
        assertThat(res.stepData()).containsEntry("pvEtape", "clôture");
        assertThat(res.stepData()).containsEntry("rapportTemplate", "RAPPORT_LIQUIDATION_DIRECTEUR");
        assertThat(res.stepData()).containsEntry("annonceTemplate", "ANNONCE_LEGALE_LIQUIDATION_SARL");
        // La cloture est TOUJOURS extraordinaire (aucun choix ordinaire offert).
        assertThat(res.stepData()).containsEntry("assembleeNature", "extraordinaire");
        assertThat(res.stepData()).containsEntry("decisionType", "AGE");
    }

    @Test
    @DisplayName("Step 1 : SARL AU → modeles AU + decision de l'associe unique")
    void step1_complet_sarlAu() {
        StepResult res = run(1, step1Payload(), dossierFacts("SARL_AU", true, DATE_DISSOLUTION));
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("pvTemplate", "PV_DISSOLUTION_LIQUIDATION_SARL_AU");
        assertThat(res.stepData())
                .containsEntry("annonceTemplate", "ANNONCE_LEGALE_LIQUIDATION_SARL_AU");
        assertThat(res.stepData()).containsEntry("decisionType", "AU");
    }

    @Test
    @DisplayName("Step 1 : societe ACTIVE (non dissoute) → blocage")
    void step1_societeNonDissoute_blocage() {
        Map<String, Object> facts = dossierFacts("SARL", true, DATE_DISSOLUTION);
        facts.put("statut", "ACTIVE");
        StepResult res = run(1, step1Payload(), facts);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("DISSOUTE");
    }

    @Test
    @DisplayName("Step 1 : dossierId absent → blocage")
    void step1_dossierAbsent_blocage() {
        Map<String, Object> p = step1Payload();
        p.remove("dossierId");
        StepResult res = run(1, p);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("Selectionnez la societe");
    }

    // ── Etape 1 : ANTI-DUPLICATION (liquidateur + date de dissolution BD) ──

    @Test
    @DisplayName("Anti-duplication : le liquidateur est repris de la BD, aucune saisie requise")
    void step1_liquidateurRepriseDeLaBd() {
        // Le payload ne porte AUCUN liquidateur : il vient integralement de la base.
        StepResult res = run(1, step1Payload());
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("liquidateurDepuisBd", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> liq = (Map<String, Object>) res.stepData().get("liquidateur");
        assertThat(liq).containsEntry("nom", "ALAOUI");
        assertThat(liq).containsEntry("prenom", "Ahmed");
        assertThat(liq).containsEntry("source", "BD");
        assertThat(res.stepData()).containsEntry("siegeLiquidation", "12 RUE DES FOULES, CASABLANCA");
    }

    @Test
    @DisplayName("Anti-duplication : une saisie ne peut PAS ecraser le liquidateur enregistre")
    void step1_liquidateurBd_gagneSurLaSaisie() {
        Map<String, Object> p = step1Payload();
        p.put("liquidateur", Map.of("source", "EXTERNE", "nom", "IMPOSTEUR", "prenom", "Jean"));
        StepResult res = run(1, p);
        assertThat(res.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> liq = (Map<String, Object>) res.stepData().get("liquidateur");
        assertThat(liq).containsEntry("nom", "ALAOUI");
        assertThat(liq).containsEntry("source", "BD");
    }

    @Test
    @DisplayName("Anti-duplication : la date de dissolution est lue en BD, pas dans le payload")
    void step1_dateDissolutionRepriseDeLaBd() {
        Map<String, Object> p = step1Payload();
        p.put("dateDissolution", LocalDate.now().minusDays(2).toString()); // tentative de surcharge
        StepResult res = run(1, p);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("dateDissolution", DATE_DISSOLUTION.toString());
    }

    // ── Etape 1 : CAS DE SECOURS (dossier dissous avant cette evolution) ──

    @Test
    @DisplayName("Secours : sans liquidateur en BD, la saisie est acceptee une fois")
    void step1_secours_liquidateurSaisi_ok() {
        Map<String, Object> facts = dossierFacts("SARL", false, DATE_DISSOLUTION);
        Map<String, Object> p = step1Payload();
        p.put("liquidateur", Map.of("source", "EXTERNE", "nom", "BENNANI", "prenom", "Karim",
                "adresse", "9 RUE DE PARIS, RABAT"));
        p.put("siegeLiquidation", "9 RUE DE PARIS, RABAT");
        StepResult res = run(1, p, facts);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("liquidateurDepuisBd", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> liq = (Map<String, Object>) res.stepData().get("liquidateur");
        assertThat(liq).containsEntry("nom", "BENNANI");
        assertThat(liq).containsEntry("source", "EXTERNE");
    }

    @Test
    @DisplayName("Secours : ni liquidateur BD ni saisie → blocage explicite")
    void step1_secours_sansLiquidateur_blocage() {
        StepResult res = run(1, step1Payload(), dossierFacts("SARL", false, DATE_DISSOLUTION));
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("liquidateur");
    }

    @Test
    @DisplayName("Secours : origine du liquidateur inconnue → blocage")
    void step1_sourceLiquidateurInvalide_blocage() {
        Map<String, Object> p = step1Payload();
        p.put("liquidateur", Map.of("source", "AUTRE", "nom", "BENNANI"));
        StepResult res = run(1, p, dossierFacts("SARL", false, DATE_DISSOLUTION));
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("Origine du liquidateur");
    }

    @Test
    @DisplayName("Secours : sans date de dissolution en BD, la saisie prend le relais")
    void step1_secours_dateDissolutionSaisie_ok() {
        Map<String, Object> facts = dossierFacts("SARL", true, null);
        Map<String, Object> p = step1Payload();
        p.put("dateDissolution", DATE_DISSOLUTION.toString());
        StepResult res = run(1, p, facts);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("dateDissolution", DATE_DISSOLUTION.toString());
    }

    @Test
    @DisplayName("Secours : ni date de dissolution BD ni saisie → blocage explicite")
    void step1_sansDateDissolution_blocage() {
        StepResult res = run(1, step1Payload(), dossierFacts("SARL", true, null));
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("date de dissolution");
    }

    // ── Etape 1 : REGLE DES 15 JOURS (dissolution → cloture) ────────────

    @Nested
    @DisplayName("RG-LI03 — regle DURE des 15 jours")
    class Delai15Jours {

        private StepResult clotureApres(long jours) {
            LocalDate dissolution = LocalDate.now().minusDays(90);
            Map<String, Object> facts = dossierFacts("SARL", true, dissolution);
            Map<String, Object> p = step1Payload();
            p.put("dateClotureLiquidation", dissolution.plusDays(jours).toString());
            return run(1, p, facts);
        }

        @Test
        @DisplayName("Cloture a J+14 → REJETEE")
        void quatorzeJours_rejete() {
            StepResult res = clotureApres(14);
            assertThat(res.canAdvance()).isFalse();
            assertThat(res.message()).contains("15");
            assertThat(res.message()).contains("14 jour(s)");
        }

        @Test
        @DisplayName("Cloture a J+15 pile → ACCEPTEE")
        void quinzeJours_accepte() {
            assertThat(clotureApres(15).canAdvance()).isTrue();
        }

        @Test
        @DisplayName("Cloture a J+30 → ACCEPTEE")
        void auDela_accepte() {
            assertThat(clotureApres(30).canAdvance()).isTrue();
        }

        @Test
        @DisplayName("Cloture anterieure a la dissolution → REJETEE")
        void clotureAvantDissolution_rejete() {
            StepResult res = clotureApres(-5);
            assertThat(res.canAdvance()).isFalse();
            assertThat(res.message()).contains("preceder");
        }

        @Test
        @DisplayName("Calcul en jours CALENDAIRES : insensible au fuseau / changement d'heure")
        void joursCalendaires_insensiblesAuFuseau() {
            // Passage a l'heure d'ete (Europe/Casablanca, nuit du 2026-03-29) : la duree
            // reelle en heures n'est PAS un multiple de 24 h — le calcul LocalDate, lui,
            // reste exact. Un calcul en millisecondes (Date.now() / 86400000) derivait ici.
            LocalDate dissolution = LocalDate.of(2026, 3, 20);
            assertThat(WorkflowSteps.liquidationDelaiError(
                    dissolution, LocalDate.of(2026, 4, 4))).isNull();          // 15 j
            assertThat(WorkflowSteps.liquidationDelaiError(
                    dissolution, LocalDate.of(2026, 4, 3))).isNotNull();       // 14 j
            // Passage a l'heure d'hiver (2026-10-25) — meme exigence.
            LocalDate automne = LocalDate.of(2026, 10, 15);
            assertThat(WorkflowSteps.liquidationDelaiError(
                    automne, LocalDate.of(2026, 10, 30))).isNull();            // 15 j
            assertThat(WorkflowSteps.liquidationDelaiError(
                    automne, LocalDate.of(2026, 10, 29))).isNotNull();         // 14 j
            // Date de dissolution inconnue = pas de controle opposable.
            assertThat(WorkflowSteps.liquidationDelaiError(null, automne)).isNull();
        }

        @Test
        @DisplayName("Le seuil partage vaut bien 15 jours")
        void seuil_est15() {
            assertThat(WorkflowSteps.LIQUIDATION_DELAI_JOURS).isEqualTo(15);
        }
    }

    // ── Etape 1 : comptes finaux → boni / mali CALCULE ─────────────────

    @Test
    @DisplayName("Comptes finaux : actif > passif → boni calcule (aucune saisie separee)")
    void step1_boniCalcule() {
        StepResult res = run(1, step1Payload(500_000L, 300_000L));
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("resultatType", "boni");
        @SuppressWarnings("unchecked")
        Map<String, Object> comptes = (Map<String, Object>) res.stepData().get("comptesFinaux");
        assertThat(comptes).containsEntry("issue", "BONI");
        assertThat((BigDecimal) comptes.get("resultatMontant"))
                .isEqualByComparingTo(BigDecimal.valueOf(200_000));
    }

    @Test
    @DisplayName("Comptes finaux : passif > actif → mali calcule, montant en valeur absolue")
    void step1_maliCalcule() {
        StepResult res = run(1, step1Payload(300_000L, 500_000L));
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("resultatType", "mali");
        @SuppressWarnings("unchecked")
        Map<String, Object> comptes = (Map<String, Object>) res.stepData().get("comptesFinaux");
        assertThat(comptes).containsEntry("issue", "MALI");
        assertThat((BigDecimal) comptes.get("resultatMontant"))
                .isEqualByComparingTo(BigDecimal.valueOf(200_000));
    }

    @Test
    @DisplayName("Comptes finaux : solde nul → boni de 0")
    void step1_soldeNul_estBoni() {
        StepResult res = run(1, step1Payload(300_000L, 300_000L));
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("resultatType", "boni");
    }

    @Test
    @DisplayName("Comptes finaux absents → blocage")
    void step1_comptesAbsents_blocage() {
        Map<String, Object> p = step1Payload();
        p.remove("comptesFinaux");
        StepResult res = run(1, p);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("comptes finaux");
    }

    @Test
    @DisplayName("Comptes finaux negatifs → blocage")
    void step1_comptesNegatifs_blocage() {
        Map<String, Object> p = step1Payload();
        p.put("comptesFinaux", Map.of("totalActif", -1, "totalPassif", 10));
        StepResult res = run(1, p);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("positives ou nulles");
    }

    // ── Etape 1 : date de cloture ─────────────────────────────────────

    @Test
    @DisplayName("Step 1 : date de cloture absente → blocage")
    void step1_dateClotureAbsente_blocage() {
        Map<String, Object> p = step1Payload();
        p.remove("dateClotureLiquidation");
        StepResult res = run(1, p);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("cloture de la");
    }

    @Test
    @DisplayName("Step 1 : date de cloture trop lointaine → blocage")
    void step1_dateClotureFuture_blocage() {
        Map<String, Object> p = step1Payload();
        p.put("dateClotureLiquidation", LocalDate.now().plusDays(60).toString());
        StepResult res = run(1, p);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("futur");
    }

    // ── Etape 1 : convocation OPTIONNELLE, regle des 16 jours ──────────

    @Test
    @DisplayName("Step 1 : convocation absente → OK (optionnelle)")
    void step1_sansConvocation_ok() {
        assertThat(run(1, step1Payload()).canAdvance()).isTrue();
    }

    @Test
    @DisplayName("Step 1 : convocation a 16 jours pile → OK")
    void step1_convocation16Jours_ok() {
        Map<String, Object> p = step1Payload();
        p.put("convocation", Map.of("date", DATE_CLOTURE.minusDays(16).toString(),
                "heure", "10H00"));
        StepResult res = run(1, p);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsKey("convocation");
    }

    @Test
    @DisplayName("Step 1 : convocation a 15 jours → blocage (16 jours requis)")
    void step1_convocation15Jours_blocage() {
        Map<String, Object> p = step1Payload();
        p.put("convocation", Map.of("date", DATE_CLOTURE.minusDays(15).toString()));
        StepResult res = run(1, p);
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("16");
    }

    // ── Etape 2 : generation PV + rapport + annonce ────────────────────

    @Test
    @DisplayName("Step 2 : PV non valide → blocage")
    void step2_pvNonValide_blocage() {
        StepResult res = run(2, Map.of("rapportValide", true, "annonceValide", true));
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("PV de cloture");
    }

    @Test
    @DisplayName("Step 2 : rapport non valide → blocage")
    void step2_rapportNonValide_blocage() {
        StepResult res = run(2, Map.of("pvValide", true, "annonceValide", true));
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("rapport de liquidation");
    }

    @Test
    @DisplayName("Step 2 : annonce legale non validee → blocage")
    void step2_annonceNonValide_blocage() {
        StepResult res = run(2, Map.of("pvValide", true, "rapportValide", true));
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("annonce legale");
    }

    @Test
    @DisplayName("Step 2 : les 3 actes valides, sans depot legal → OK (depot legal facultatif)")
    void step2_sansDepotLegal_ok() {
        StepResult res = run(2, Map.of("pvValide", true, "rapportValide", true,
                "annonceValide", true));
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).doesNotContainKey("depotLegal");
    }

    @Test
    @DisplayName("Step 2 : depot legal renseigne → persiste pour la synthese")
    void step2_avecDepotLegal_ok() {
        Map<String, Object> p = new HashMap<>();
        p.put("pvValide", true);
        p.put("rapportValide", true);
        p.put("annonceValide", true);
        p.put("depotLegal", Map.of("numero", "78945", "date", "2026-07-01"));
        StepResult res = run(2, p);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsKey("depotLegal");
    }

    // ── Etape 3 : pieces jointes OPTIONNELLES ─────────────────────────

    @Test
    @DisplayName("Step 3 : aucune piece jointe → OK (etape facultative, skippable)")
    void step3_vide_ok() {
        StepResult res = run(3, Map.of());
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
        StepResult res = run(3, p);
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("piecesJointesCount", 2);
    }

    // ── Etape 4 : synthese ────────────────────────────────────────────

    @Test
    @DisplayName("Step 4 : synthese → statut LIQUIDEE + finalise")
    void step4_synthese_ok() {
        StepResult res = run(4, Map.of());
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("societeStatut", "LIQUIDEE");
        assertThat(res.stepData()).containsEntry("finalise", true);
        assertThat(res.stepData()).containsEntry("succursalesOuvertesCount", 0);
    }

    // ── Succursales : la cloture ne peut pas les laisser derriere elle ─────

    /** Contexte d'etape avec la liste des succursales ACTIVE injectee par l'application. */
    private StepResult runAvecSuccursales(int step, Map<String, Object> payload,
                                          List<String> succursales) {
        Map<String, Object> existing = new HashMap<>();
        existing.put("dossier", dossierFacts("SARL", true, DATE_DISSOLUTION));
        existing.put("succursalesOuvertes", succursales);
        return workflow.executeStep(new StepContext(ws, ticket, user, step, payload, existing));
    }

    @Test
    @DisplayName("Step 4 : societe avec succursales ouvertes → cloture REFUSEE")
    void step4_succursalesOuvertes_bloque() {
        // Une succursale n'a pas de personnalite juridique distincte de sa societe : elle
        // ne peut pas survivre a la radiation de celle-ci. Sans cette garde, les
        // succursales restaient ACTIVE sous une societe liquidee — et devenaient
        // infermables, le workflow de fermeture ne proposant que des meres ACTIVE.
        StepResult res = runAvecSuccursales(4, Map.of(),
                List.of("Agence Marrakech (Marrakech) RC 78901", "Agence Fes (Fes)"));
        assertThat(res.canAdvance()).isFalse();
        assertThat(res.message()).contains("2 succursale");
        assertThat(res.message()).contains("Agence Marrakech");
        assertThat(res.message()).contains("Agence Fes");
        assertThat(res.message()).contains("Fermeture de succursale");
    }

    @Test
    @DisplayName("Step 4 : toutes les succursales fermees → cloture autorisee")
    void step4_aucuneSuccursaleOuverte_ok() {
        StepResult res = runAvecSuccursales(4, Map.of(), List.of());
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("societeStatut", "LIQUIDEE");
    }

    @Test
    @DisplayName("Step 1 : succursales ouvertes signalees des la saisie (non bloquant)")
    void step1_succursalesOuvertes_avertit_sans_bloquer() {
        // L'employe doit l'apprendre AVANT d'avoir genere PV, rapport et annonce — pas a
        // la derniere etape. L'etape 1 se contente donc de transmettre l'information.
        StepResult res = runAvecSuccursales(1, step1Payload(),
                List.of("Agence Marrakech (Marrakech)"));
        assertThat(res.canAdvance()).isTrue();
        assertThat(res.stepData()).containsEntry("succursalesOuvertesCount", 1);
    }
}
