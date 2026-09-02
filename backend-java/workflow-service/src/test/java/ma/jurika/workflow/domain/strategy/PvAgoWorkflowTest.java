package ma.jurika.workflow.domain.strategy;

import ma.jurika.workflow.domain.model.WorkflowType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot DIVERS §E (2026-08-13) — workflow PV AGO refondu en 5 etapes.
 *
 * <p>Couvre : l'etape 1 (societe BD, date d'AGO ORDINAIRE, exercice clos, convocation
 * 16 j), l'etape 2 (resultat, affectation soldee, dividendes, quitus, conventions, CAC),
 * l'etape 3 (PV obligatoire / rapport de gestion OPTIONNEL / aucune annonce legale), et
 * l'etape 4 optionnelle.
 */
class PvAgoWorkflowTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    /** Date d'AGO toujours dans la fenetre acceptee (J-2 ans .. J+30). */
    private static final LocalDate DATE_AGO = LocalDate.now().plusDays(5);
    /** Exercice clos anterieur a l'assemblee (l'AGO approuve un exercice deja clos). */
    private static final int EXERCICE = LocalDate.now().minusYears(1).getYear();

    private final PvAgoWorkflow wf = new PvAgoWorkflow();

    private static StepContext ctx(int step, Map<String, Object> payload,
                                   Map<String, Object> existing) {
        return new StepContext(WS, TICKET, USER, step, payload, existing);
    }

    private static Map<String, Object> dossier(String forme) {
        Map<String, Object> d = new HashMap<>();
        d.put("denomination", "PARACOSME");
        d.put("formeJuridique", forme);
        return d;
    }

    private static Map<String, Object> step1() {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", UUID.randomUUID().toString());
        p.put("exerciceClos", String.valueOf(EXERCICE));
        p.put("dateAGO", DATE_AGO.toString());
        return p;
    }

    private static Map<String, Object> affectation(String libelle, long montant) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("libelle", libelle);
        a.put("montant", montant);
        return a;
    }

    /** Etape 2 nominale : resultat 100 000 solde par deux postes d'affectation. */
    private static Map<String, Object> step2() {
        List<Map<String, Object>> aff = new ArrayList<>();
        aff.add(affectation("Réserve légale", 5_000L));
        aff.add(affectation("Report à nouveau", 95_000L));

        Map<String, Object> a = new LinkedHashMap<>();
        a.put("exerciceClosDate", EXERCICE + "-12-31");
        a.put("resultatType", "bénéfice");
        a.put("resultatNet", 100_000L);
        a.put("affectations", aff);

        Map<String, Object> p = new HashMap<>();
        p.put("approbation", a);
        return p;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> approbationOf(Map<String, Object> p) {
        return (Map<String, Object>) p.get("approbation");
    }

    @Test
    void passe_a_5_etapes() {
        assertThat(WorkflowType.PV_AGO.totalSteps()).isEqualTo(5);
    }

    // ==================================================================
    //  Etape 1 — societe, exercice, date d'AGO, convocation
    // ==================================================================

    @Nested
    class Etape1 {

        @Test
        void nominal_impose_l_assemblee_ordinaire() {
            StepResult r = wf.executeStep(ctx(1, step1(), Map.of("dossier", dossier("SARL"))));
            assertThat(r.canAdvance()).isTrue();
            // L'approbation des comptes releve TOUJOURS de l'AGO : la nature est imposee,
            // jamais choisie (aucun selecteur de type cote front).
            assertThat(r.stepData()).containsEntry("assembleeNature", "ordinaire");
            assertThat(r.stepData()).containsEntry("decisionType", "AGO");
            assertThat(r.stepData()).containsEntry("exerciceClosDate", EXERCICE + "-12-31");
        }

        @Test
        void exige_la_societe() {
            Map<String, Object> p = step1();
            p.remove("dossierId");
            StepResult r = wf.executeStep(ctx(1, p, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("societe");
        }

        @Test
        void exige_l_exercice_clos() {
            Map<String, Object> p = step1();
            p.remove("exerciceClos");
            StepResult r = wf.executeStep(ctx(1, p, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("exercice clos");
        }

        @Test
        void exige_la_date_d_ago() {
            Map<String, Object> p = step1();
            p.remove("dateAGO");
            StepResult r = wf.executeStep(ctx(1, p, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("ordinaire");
        }

        @Test
        void refuse_une_societe_dissoute() {
            Map<String, Object> d = dossier("SARL");
            d.put("statut", "DISSOUTE");
            StepResult r = wf.executeStep(ctx(1, step1(), Map.of("dossier", d)));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("DISSOUTE");
        }

        @Test
        void refuse_une_assemblee_anterieure_a_la_cloture() {
            // L'AGO approuve un exercice DEJA clos : elle ne peut pas le preceder.
            Map<String, Object> p = step1();
            p.put("exerciceClos", String.valueOf(LocalDate.now().getYear() + 1));
            StepResult r = wf.executeStep(ctx(1, p, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("AVANT la cloture");
        }

        @Test
        void accepte_un_exercice_clos_en_date_complete() {
            Map<String, Object> p = step1();
            p.put("exerciceClos", EXERCICE + "-06-30");
            StepResult r = wf.executeStep(ctx(1, p, Map.of()));
            assertThat(r.canAdvance()).isTrue();
            assertThat(r.stepData()).containsEntry("exerciceClosDate", EXERCICE + "-06-30");
        }

        @Test
        void convocation_16j_respectee_passe_et_trop_courte_bloque() {
            Map<String, Object> ok = step1();
            ok.put("convocation", Map.of("date", DATE_AGO.minusDays(16).toString()));
            assertThat(wf.executeStep(ctx(1, ok, Map.of())).canAdvance()).isTrue();

            Map<String, Object> ko = step1();
            ko.put("convocation", Map.of("date", DATE_AGO.minusDays(11).toString()));
            StepResult r = wf.executeStep(ctx(1, ko, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("16");
        }
    }

    // ==================================================================
    //  Etape 2 — donnees du PV
    // ==================================================================

    @Nested
    class Etape2 {

        @Test
        void nominal_normalise_le_bloc_approbation() {
            StepResult r = wf.executeStep(ctx(2, step2(), Map.of()));
            assertThat(r.canAdvance()).isTrue();
            Map<String, Object> a = approbationOf(r.stepData());
            assertThat(a).containsEntry("resultatType", "benefice");
            assertThat(a).containsEntry("resultatNet", 100_000L);
            // Defauts metier : quitus accorde, aucune convention, pas de CAC.
            assertThat(a).containsEntry("quitusGerance", "oui");
            assertThat(a).containsEntry("conventionsReglementees", "non");
            assertThat(a).containsEntry("commissairePresent", "non");
        }

        @Test
        void exige_le_type_et_le_montant_du_resultat() {
            Map<String, Object> sansType = step2();
            approbationOf(sansType).remove("resultatType");
            assertThat(wf.executeStep(ctx(2, sansType, Map.of())).canAdvance()).isFalse();

            Map<String, Object> sansMontant = step2();
            approbationOf(sansMontant).remove("resultatNet");
            StepResult r = wf.executeStep(ctx(2, sansMontant, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("montant du resultat");
        }

        @Test
        void exige_une_affectation_qui_solde_le_resultat() {
            Map<String, Object> vide = step2();
            approbationOf(vide).put("affectations", List.of());
            assertThat(wf.executeStep(ctx(2, vide, Map.of())).canAdvance()).isFalse();

            Map<String, Object> desequilibre = step2();
            approbationOf(desequilibre).put("affectations",
                    List.of(affectation("Réserve légale", 5_000L)));
            StepResult r = wf.executeStep(ctx(2, desequilibre, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("ne solde pas");
        }

        @Test
        void affectation_d_une_perte_se_solde_sur_la_valeur_absolue() {
            Map<String, Object> p = step2();
            Map<String, Object> a = approbationOf(p);
            a.put("resultatType", "perte");
            a.put("resultatNet", -100_000L);
            assertThat(wf.executeStep(ctx(2, p, Map.of())).canAdvance()).isTrue();
        }

        @Test
        void exige_libelle_et_montant_sur_chaque_ligne() {
            Map<String, Object> p = step2();
            approbationOf(p).put("affectations",
                    List.of(affectation("", 100_000L)));
            StepResult r = wf.executeStep(ctx(2, p, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("libelle");
        }

        @Test
        void dividendes_annonces_exigent_total_part_et_date() {
            Map<String, Object> p = step2();
            Map<String, Object> a = approbationOf(p);
            a.put("dividendeDistribue", "oui");
            assertThat(wf.executeStep(ctx(2, p, Map.of())).message()).contains("TOTAL");

            a.put("dividendeMontantTotal", 40_000L);
            assertThat(wf.executeStep(ctx(2, p, Map.of())).message()).contains("par part");

            a.put("dividendeParPart", 40L);
            assertThat(wf.executeStep(ctx(2, p, Map.of())).message()).contains("mise en paiement");

            a.put("dividendeMiseEnPaiementDate", DATE_AGO.plusDays(30).toString());
            assertThat(wf.executeStep(ctx(2, p, Map.of())).canAdvance()).isTrue();
        }

        @Test
        void dividendes_ne_peuvent_pas_depasser_le_resultat() {
            Map<String, Object> p = step2();
            Map<String, Object> a = approbationOf(p);
            a.put("dividendeDistribue", "oui");
            a.put("dividendeMontantTotal", 150_000L);
            a.put("dividendeParPart", 150L);
            a.put("dividendeMiseEnPaiementDate", DATE_AGO.plusDays(30).toString());
            StepResult r = wf.executeStep(ctx(2, p, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("depasse le resultat");
        }

        @Test
        void distribution_decochee_ne_fuit_pas_dans_le_pv() {
            Map<String, Object> p = step2();
            Map<String, Object> a = approbationOf(p);
            a.put("dividendeDistribue", "non");
            a.put("dividendeMontantTotal", 40_000L);  // residu d'une saisie precedente
            a.put("dividendeParPart", 40L);
            StepResult r = wf.executeStep(ctx(2, p, Map.of()));
            assertThat(r.canAdvance()).isTrue();
            Map<String, Object> out = approbationOf(r.stepData());
            assertThat(out).doesNotContainKey("dividendeMontantTotal");
            assertThat(out).doesNotContainKey("dividendeParPart");
        }

        @Test
        void conventions_annoncees_exigent_leur_description() {
            Map<String, Object> p = step2();
            approbationOf(p).put("conventionsReglementees", "oui");
            StepResult r = wf.executeStep(ctx(2, p, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("Conventions");
        }

        @Test
        void commissaire_present_exige_son_nom() {
            Map<String, Object> p = step2();
            approbationOf(p).put("commissairePresent", "oui");
            StepResult r = wf.executeStep(ctx(2, p, Map.of()));
            assertThat(r.canAdvance()).isFalse();
            assertThat(r.message()).contains("nom");
        }

        @Test
        void quitus_refuse_est_conserve() {
            Map<String, Object> p = step2();
            approbationOf(p).put("quitusGerance", "non");
            StepResult r = wf.executeStep(ctx(2, p, Map.of()));
            assertThat(approbationOf(r.stepData())).containsEntry("quitusGerance", "non");
        }
    }

    // ==================================================================
    //  Etapes 3 a 5
    // ==================================================================

    @Test
    void step3_exige_le_pv_mais_pas_le_rapport_de_gestion() {
        assertThat(wf.executeStep(ctx(3, Map.of(), Map.of())).canAdvance()).isFalse();

        StepResult r = wf.executeStep(ctx(3, Map.of("pvValide", true),
                Map.of("dossier", dossier("SARL"))));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsEntry("pvTemplate", "PV_APPROBATION_COMPTES_SARL");
        // Le rapport de gestion est OPTIONNEL : son absence ne bloque pas.
        assertThat(r.stepData()).containsEntry("rapportGestionValide", false);
        // Aucune annonce legale : l'approbation n'est pas opposable aux tiers.
        assertThat(r.stepData()).containsEntry("annonceRequise", false);
        assertThat(r.stepData()).doesNotContainKey("annonceTemplate");
    }

    @Test
    void step3_choisit_le_modele_SARL_AU() {
        StepResult r = wf.executeStep(ctx(3, Map.of("pvValide", true),
                Map.of("dossier", dossier("SARL_AU"))));
        assertThat(r.stepData()).containsEntry("pvTemplate", "PV_APPROBATION_COMPTES_SARL_AU");
    }

    @Test
    void step4_pieces_jointes_vides_ne_bloquent_pas() {
        StepResult r = wf.executeStep(ctx(4, Map.of(), Map.of()));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsEntry("piecesJointesCount", 0);
    }

    @Test
    void step5_finalise() {
        StepResult r = wf.executeStep(ctx(5, Map.of(), Map.of()));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsEntry("finalise", true);
    }

    @Test
    void etape_hors_plage_est_rejetee() {
        assertThatThrownBy(() -> wf.executeStep(ctx(6, Map.of(), Map.of())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("1..5");
    }
}
