package ma.jurika.workflow.domain.strategy;

import ma.jurika.workflow.domain.model.WorkflowType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Couvre le repointage Phase D des 3 workflows succursale sur les nouveaux
 * modeles directeur (choix SARL vs SARL_AU par forme juridique), la conservation
 * de l'ecran de conformite ETR bloquant, et l'invariance de la structure d'etapes.
 */
class SuccursaleWorkflowsTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private static StepContext ctx(int step, Map<String, Object> payload,
                                   Map<String, Object> existing) {
        return new StepContext(WS, TICKET, USER, step, payload, existing);
    }

    private static Map<String, Object> dossier(String forme) {
        Map<String, Object> d = new HashMap<>();
        d.put("dossierId", UUID.randomUUID().toString());
        d.put("denomination", "PARACOSME");
        d.put("ice", "001234567000089");
        if (forme != null) d.put("formeJuridique", forme);
        return d;
    }

    // ==================================================================
    //  SUCCURSALE_MA
    // ==================================================================

    /**
     * Date d'assemblee VALIDE en permanence : la strategie refuse > J+30 et < J-2 ans.
     * Une date en dur aurait fait tourner le test au rouge avec le temps.
     */
    private static final LocalDate DATE_AG = LocalDate.now().plusDays(10);

    /** Etape 1 nominale : societe mere + date/type d'assemblee. */
    private static Map<String, Object> maStep1(LocalDate dateAg) {
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", UUID.randomUUID().toString());
        p.put("dateAG", dateAg.toString());
        p.put("typeAssemblee", "extraordinaire");
        return p;
    }

    /** Etape 2 nominale : bloc succursale complet. */
    private static Map<String, Object> maStep2() {
        Map<String, Object> succ = new HashMap<>();
        succ.put("enseigne", "PARACOSME — Agence Marrakech");
        succ.put("adresse", "5 Avenue Mohammed VI");
        succ.put("ville", "Marrakech");
        succ.put("villeGreffe", "MARRAKECH");
        succ.put("activite", "Conseil");
        succ.put("dateOuverture", "2026-10-01");
        Map<String, Object> p = new HashMap<>();
        p.put("succursale", succ);
        return p;
    }

    @Test
    void ma_passe_a_5_etapes() {
        // Lot DIVERS §B : 11 -> 5 etapes (les etapes « statuts modifies », « formulaire RC »
        // et « RC secondaire » n'avaient pas de modele directeur / relevent du greffe).
        assertThat(WorkflowType.SUCCURSALE_MA.totalSteps()).isEqualTo(5);
    }

    @Test
    void ma_step1_accepte_societeMereId_comme_alias_de_dossierId() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        String mereId = UUID.randomUUID().toString();
        Map<String, Object> p = new HashMap<>();
        p.put("societeMereId", mereId);
        p.put("dateAG", DATE_AG.toString());
        p.put("typeAssemblee", "extraordinaire");
        StepResult r = wf.executeStep(ctx(1, p, Map.of("dossier", dossier("SARL"))));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsEntry("dossierId", mereId);
        assertThat(r.stepData()).containsEntry("societeMereId", mereId);
        assertThat(r.stepData()).containsEntry("assembleeNature", "extraordinaire");
    }

    @Test
    void ma_step1_exige_la_societe_mere() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        StepResult r = wf.executeStep(ctx(1, Map.of("dateAG", DATE_AG.toString()), Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("societe mere");
    }

    @Test
    void ma_step1_exige_le_type_d_assemblee() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        Map<String, Object> p = maStep1(DATE_AG);
        p.remove("typeAssemblee");
        StepResult r = wf.executeStep(ctx(1, p, Map.of("dossier", dossier("SARL"))));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("ordinaire");
    }

    @Test
    void ma_step1_refuse_une_societe_dissoute() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        Map<String, Object> d = dossier("SARL");
        d.put("statut", "DISSOUTE");
        StepResult r = wf.executeStep(ctx(1, maStep1(DATE_AG), Map.of("dossier", d)));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("DISSOUTE");
    }

    // ---- Convocation optionnelle : regle DURE des 16 jours calendaires ----

    @Test
    void ma_step1_convocation_absente_ne_bloque_pas() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        StepResult r = wf.executeStep(ctx(1, maStep1(DATE_AG), Map.of("dossier", dossier("SARL"))));
        assertThat(r.canAdvance()).isTrue();
    }

    @Test
    void ma_step1_convocation_16j_respectee_passe() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        Map<String, Object> p = maStep1(DATE_AG);
        p.put("convocation", Map.of("date", DATE_AG.minusDays(16).toString())); // 16 jours pile
        StepResult r = wf.executeStep(ctx(1, p, Map.of("dossier", dossier("SARL"))));
        assertThat(r.canAdvance()).isTrue();
    }

    @Test
    void ma_step1_convocation_trop_courte_bloque() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        Map<String, Object> p = maStep1(DATE_AG);
        p.put("convocation", Map.of("date", DATE_AG.minusDays(11).toString())); // 11 jours
        StepResult r = wf.executeStep(ctx(1, p, Map.of("dossier", dossier("SARL"))));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("16");
    }

    // ---- Etape 2 : saisie de la succursale ----

    @Test
    void ma_step2_transporte_le_bloc_succursale_et_le_mandataire() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        Map<String, Object> p = maStep2();
        p.put("formalitesMandataireNom", "M. Karim BENALI");
        StepResult r = wf.executeStep(ctx(2, p, Map.of("dossier", dossier("SARL"))));
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) r.stepData().get("succursale");
        assertThat(succ).containsEntry("enseigne", "PARACOSME — Agence Marrakech");
        assertThat(succ).containsEntry("villeGreffe", "MARRAKECH");
        assertThat(r.stepData()).containsEntry("formalitesMandataireNom", "M. Karim BENALI");
    }

    @Test
    void ma_step2_greffe_succursale_repli_sur_la_ville() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        Map<String, Object> p = maStep2();
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) p.get("succursale");
        succ.remove("villeGreffe");
        StepResult r = wf.executeStep(ctx(2, p, Map.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) r.stepData().get("succursale");
        assertThat(out).containsEntry("villeGreffe", "Marrakech");
    }

    @Test
    void ma_step2_dotation_annoncee_sans_montant_bloque() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        Map<String, Object> p = maStep2();
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) p.get("succursale");
        succ.put("dotationPresente", true);
        StepResult r = wf.executeStep(ctx(2, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("montant");
    }

    @Test
    void ma_step2_dotation_decochee_ne_fuit_pas_dans_les_documents() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        Map<String, Object> p = maStep2();
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) p.get("succursale");
        succ.put("dotationPresente", false);
        succ.put("dotationMontant", 250_000); // residu d'une saisie precedente
        StepResult r = wf.executeStep(ctx(2, p, Map.of()));
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) r.stepData().get("succursale");
        assertThat(out).doesNotContainKey("dotationMontant");
        assertThat(out).containsEntry("dotationPresente", false);
    }

    @Test
    void ma_step2_responsable_annonce_exige_identite_adresse_piece_et_pouvoirs() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        Map<String, Object> p = maStep2();
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) p.get("succursale");
        succ.put("responsablePresent", true);
        succ.put("responsable", Map.of("nom", "TAZI"));
        StepResult r = wf.executeStep(ctx(2, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("adresse");
    }

    @Test
    void ma_step2_responsable_complet_passe() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        Map<String, Object> p = maStep2();
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) p.get("succursale");
        succ.put("responsablePresent", true);
        succ.put("responsable", Map.of(
                "source", "bd", "nom", "TAZI", "prenom", "Yassine",
                "adresse", "8 RUE IBN SINA, MARRAKECH",
                "pieceNumero", "EE123456", "pouvoirs", "gestion courante"));
        StepResult r = wf.executeStep(ctx(2, p, Map.of()));
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) r.stepData().get("succursale");
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = (Map<String, Object>) out.get("responsable");
        assertThat(resp).containsEntry("source", "BD"); // normalise en majuscules
    }

    // ---- Etape 3 : generation (PV + annonce) ----

    @Test
    void ma_step3_exige_pv_et_annonce_valides() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        StepResult sansRien = wf.executeStep(ctx(3, Map.of(), Map.of()));
        assertThat(sansRien.canAdvance()).isFalse();
        StepResult sansAnnonce = wf.executeStep(ctx(3, Map.of("pvValide", true), Map.of()));
        assertThat(sansAnnonce.canAdvance()).isFalse();
        assertThat(sansAnnonce.message()).contains("annonce");
    }

    @Test
    void ma_step3_choisit_les_modeles_SARL_par_defaut() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        StepResult r = wf.executeStep(ctx(3,
                Map.of("pvValide", true, "annonceValide", true),
                Map.of("dossier", dossier("SARL"))));
        assertThat(r.stepData()).containsEntry("pvTemplate", "PV_CREATION_SUCCURSALE_MAROC_SARL");
        assertThat(r.stepData())
                .containsEntry("annonceTemplate", "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL");
        assertThat(r.stepData()).containsEntry("associeUnique", false);
    }

    @Test
    void ma_step3_choisit_les_modeles_SARL_AU() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        StepResult r = wf.executeStep(ctx(3,
                Map.of("pvValide", true, "annonceValide", true),
                Map.of("dossier", dossier("SARL_AU"))));
        assertThat(r.stepData()).containsEntry("pvTemplate", "PV_CREATION_SUCCURSALE_MAROC_SARL_AU");
        assertThat(r.stepData())
                .containsEntry("annonceTemplate", "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL_AU");
    }

    @Test
    void ma_step3_defaut_SARL_si_forme_inconnue() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        StepResult r = wf.executeStep(ctx(3,
                Map.of("pvValide", true, "annonceValide", true), Map.of()));
        assertThat(r.stepData()).containsEntry("pvTemplate", "PV_CREATION_SUCCURSALE_MAROC_SARL");
    }

    // ---- Etape 4 : pieces jointes OPTIONNELLE ----

    @Test
    void ma_step4_pieces_jointes_vides_ne_bloquent_pas() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        StepResult r = wf.executeStep(ctx(4, Map.of(), Map.of()));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsEntry("piecesJointesCount", 0);
    }

    @Test
    void ma_step5_finalise() {
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        StepResult r = wf.executeStep(ctx(5, Map.of(), Map.of()));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsEntry("finalise", true);
    }

    @Test
    void ma_etape_hors_plage_est_rejetee() {
        // Garde-fou de la refonte 11 -> 5 : une ancienne etape (6..11) ne doit plus
        // pouvoir etre executee. AbstractWorkflow borne la plage avant la strategie.
        SuccursaleMaWorkflow wf = new SuccursaleMaWorkflow();
        assertThatThrownBy(() -> wf.executeStep(ctx(6, Map.of(), Map.of())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("1..5");
    }

    // ==================================================================
    //  FERMETURE_SUCCURSALE
    // ==================================================================

    /** Etape 1 nominale de la fermeture : selection BD + fermeture + assemblee. */
    private static Map<String, Object> fermStep1() {
        Map<String, Object> succ = new HashMap<>();
        succ.put("enseigne", "PARACOSME — Agence Marrakech");
        succ.put("adresse", "5 Avenue Mohammed VI");
        succ.put("ville", "Marrakech");
        succ.put("villeGreffe", "MARRAKECH");
        succ.put("activite", "Conseil");
        succ.put("rcNumero", "78901");
        succ.put("dateFermeture", "2026-10-31");
        succ.put("motif", "la reorganisation du reseau commercial");

        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", UUID.randomUUID().toString());
        p.put("succursaleDbId", UUID.randomUUID().toString());
        p.put("succursale", succ);
        p.put("dateAG", DATE_AG.toString());
        p.put("typeAssemblee", "extraordinaire");
        return p;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> succOf(Map<String, Object> p) {
        return (Map<String, Object>) p.get("succursale");
    }

    @Test
    void fermeture_conserve_4_etapes() {
        assertThat(WorkflowType.FERMETURE_SUCCURSALE.totalSteps()).isEqualTo(4);
    }

    @Test
    void fermeture_step1_reprend_le_bloc_succursale_de_la_BD() {
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        StepResult r = wf.executeStep(ctx(1, fermStep1(), Map.of("dossier", dossier("SARL"))));
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) r.stepData().get("succursale");
        assertThat(succ).containsEntry("rcNumero", "78901");
        assertThat(succ).containsEntry("dateFermeture", "2026-10-31");
        assertThat(succ).containsEntry("villeGreffe", "MARRAKECH");
        assertThat(r.stepData()).containsEntry("assembleeNature", "extraordinaire");
    }

    @Test
    void fermeture_step1_exige_la_selection_ou_une_saisie_exploitable() {
        // Ni identifiant BD, ni enseigne/ville : rien ne permet de designer la succursale.
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        Map<String, Object> p = fermStep1();
        p.remove("succursaleDbId");
        p.remove("succursaleId");
        succOf(p).remove("enseigne");
        succOf(p).remove("ville");
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("succursale a fermer");
    }

    @Test
    void fermeture_step1_accepte_la_saisie_manuelle_dune_succursale_absente_du_referentiel() {
        // Fin de la « fermeture fantome » : une succursale exploitee avant la creation de
        // la table `succursales` n'a pas d'identifiant BD. L'etape 1 l'accepte des lors que
        // l'enseigne et la ville sont fournies — WorkflowUseCases cree alors la ligne, si
        // bien que la finalisation la marque REELLEMENT fermee (avant, PV et annonce
        // etaient produits sans qu'aucune ligne ne passe jamais FERMEE).
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        Map<String, Object> p = fermStep1();
        p.remove("succursaleDbId");
        p.remove("succursaleId");
        StepResult r = wf.executeStep(ctx(1, p, Map.of("dossier", dossier("SARL"))));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).doesNotContainKey("succursaleDbId");
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) r.stepData().get("succursale");
        assertThat(succ).containsEntry("rcNumero", "78901");
    }

    @Test
    void fermeture_step1_refuse_une_saisie_manuelle_sans_ville() {
        // La ville designe le greffe ou la succursale sera radiee : sans elle, la ligne
        // creee serait inexploitable pour la suite des formalites.
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        Map<String, Object> p = fermStep1();
        p.remove("succursaleDbId");
        p.remove("succursaleId");
        succOf(p).remove("ville");
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("enseigne");
    }

    @Test
    void fermeture_step1_exige_le_rc_de_la_succursale() {
        // Le RC est PUBLIE dans l'avis (« immatriculee sous le n° … ») : sans lui,
        // l'annonce partirait avec un trou.
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        Map<String, Object> p = fermStep1();
        succOf(p).remove("rcNumero");
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("RC");
    }

    @Test
    void fermeture_step1_exige_la_date_d_effet() {
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        Map<String, Object> p = fermStep1();
        succOf(p).remove("dateFermeture");
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("date d'effet");
    }

    @Test
    void fermeture_step1_exige_un_motif_suffisant() {
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        Map<String, Object> sansMotif = fermStep1();
        succOf(sansMotif).remove("motif");
        assertThat(wf.executeStep(ctx(1, sansMotif, Map.of())).canAdvance()).isFalse();

        Map<String, Object> tropCourt = fermStep1();
        succOf(tropCourt).put("motif", "bof");
        StepResult r = wf.executeStep(ctx(1, tropCourt, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("trop court");
    }

    @Test
    void fermeture_step1_convocation_trop_courte_bloque() {
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        Map<String, Object> p = fermStep1();
        p.put("convocation", Map.of("date", DATE_AG.minusDays(11).toString()));
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("16");
    }

    @Test
    void fermeture_step1_accepte_les_alias_du_front_legacy() {
        // Ancien payload : champs plats rcSecondaire / adresseSuccursale / motifFermeture.
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        Map<String, Object> p = new HashMap<>();
        p.put("dossierId", UUID.randomUUID().toString());
        p.put("succursaleDbId", UUID.randomUUID().toString());
        p.put("succursaleDenomination", "PARACOSME — Agence Marrakech");
        p.put("rcSecondaire", "78901");
        p.put("adresseSuccursale", "5 Avenue Mohammed VI");
        p.put("villeSuccursale", "Marrakech");
        p.put("dateEffet", "2026-10-31");
        p.put("motifFermeture", "la reorganisation du reseau");
        p.put("dateAG", DATE_AG.toString());
        p.put("typeAssemblee", "extraordinaire");
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) r.stepData().get("succursale");
        assertThat(succ).containsEntry("rcNumero", "78901");
        assertThat(succ).containsEntry("adresse", "5 Avenue Mohammed VI");
        assertThat(succ).containsEntry("enseigne", "PARACOSME — Agence Marrakech");
    }

    @Test
    void fermeture_step2_exige_pv_et_annonce_et_choisit_les_modeles() {
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        assertThat(wf.executeStep(ctx(2, Map.of("pvValide", true), Map.of())).canAdvance())
                .isFalse();

        StepResult r = wf.executeStep(ctx(2,
                Map.of("pvValide", true, "annonceValide", true),
                Map.of("dossier", dossier("SARL"))));
        assertThat(r.stepData()).containsEntry("pvTemplate", "PV_FERMETURE_SUCCURSALE_SARL");
        assertThat(r.stepData())
                .containsEntry("annonceTemplate", "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL");
    }

    @Test
    void fermeture_step2_choisit_les_modeles_SARL_AU() {
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        StepResult r = wf.executeStep(ctx(2,
                Map.of("pvValide", true, "annonceValide", true),
                Map.of("dossier", dossier("SARL_AU"))));
        assertThat(r.stepData()).containsEntry("pvTemplate", "PV_FERMETURE_SUCCURSALE_SARL_AU");
        assertThat(r.stepData())
                .containsEntry("annonceTemplate", "ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL_AU");
    }

    @Test
    void fermeture_step3_pieces_jointes_vides_ne_bloquent_pas() {
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        StepResult r = wf.executeStep(ctx(3, Map.of(), Map.of()));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsEntry("piecesJointesCount", 0);
    }

    @Test
    void fermeture_step4_marque_la_succursale_fermee() {
        FermetureSuccursaleWorkflow wf = new FermetureSuccursaleWorkflow();
        StepResult r = wf.executeStep(ctx(4, Map.of(), Map.of()));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsEntry("succursaleStatut", "FERMEE");
        assertThat(r.stepData()).containsEntry("finalise", true);
    }

    // ==================================================================
    //  SUCCURSALE_ETR
    // ==================================================================

    /** Etape 1 nominale : mere etrangere complete + decision + 6 controles coches. */
    private static Map<String, Object> etrStep1() {
        Map<String, Object> p = new HashMap<>();
        p.put("denominationSocieteMere", "GLOBAL TRADING LTD");
        p.put("formeJuridiqueOrigine", "Limited");
        p.put("paysOrigine", "Royaume-Uni");
        p.put("siege", "10 Downing Street, Londres");
        p.put("dateAG", DATE_AG.toString());
        p.put("typeAssemblee", "extraordinaire");
        for (String c : SuccursaleEtrWorkflow.CONTROLES_CONFORMITE) {
            p.put(c, true);
        }
        return p;
    }

    @Test
    void etr_passe_a_5_etapes() {
        assertThat(WorkflowType.SUCCURSALE_ETR.totalSteps()).isEqualTo(5);
    }

    @Test
    void etr_step1_transporte_bloc_societeMere_et_organe() {
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        StepResult r = wf.executeStep(ctx(1, etrStep1(), Map.of()));
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> mere = (Map<String, Object>) r.stepData().get("societeMere");
        assertThat(mere).containsEntry("denomination", "GLOBAL TRADING LTD");
        assertThat(mere).containsEntry("pays", "Royaume-Uni");
        assertThat(mere).containsEntry("forme", "Limited");
        assertThat(r.stepData()).containsEntry("assembleeNature", "extraordinaire");
        assertThat(r.stepData()).containsEntry("conformiteValidee", true);
    }

    @Test
    void etr_step1_exige_la_mere_ou_sa_selection() {
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        Map<String, Object> p = etrStep1();
        p.remove("denominationSocieteMere");
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("societe mere");
    }

    @Test
    void etr_step1_exige_les_mentions_publiees_de_la_mere() {
        // Forme / pays / siege figurent dans l'avis : sans eux, l'annonce aurait des trous.
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        Map<String, Object> p = etrStep1();
        p.remove("siege");
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("annonce");
    }

    @Test
    void etr_step1_mere_selectionnee_dispense_de_la_saisie() {
        // Reutilisation d'une mere DEJA enregistree : aucune re-saisie exigee.
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        String mereId = UUID.randomUUID().toString();
        Map<String, Object> p = new HashMap<>();
        p.put("dossierMereEtrangereId", mereId);
        p.put("dateAG", DATE_AG.toString());
        p.put("typeAssemblee", "extraordinaire");
        for (String c : SuccursaleEtrWorkflow.CONTROLES_CONFORMITE) p.put(c, true);
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsEntry("dossierMereEtrangereId", mereId);
    }

    @Test
    void etr_step1_conformite_bloque_si_controles_manquants() {
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        Map<String, Object> p = etrStep1();
        p.put("controleApostille", false);
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("conformite");
    }

    @Test
    void etr_step1_convocation_trop_courte_bloque() {
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        Map<String, Object> p = etrStep1();
        p.put("convocation", Map.of("date", DATE_AG.minusDays(11).toString()));
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.canAdvance()).isFalse();
        assertThat(r.message()).contains("16");
    }

    @Test
    void etr_step1_detecte_organe_unipersonnel() {
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        Map<String, Object> p = etrStep1();
        p.put("organe", Map.of("competent", "le gerant unique"));
        StepResult r = wf.executeStep(ctx(1, p, Map.of()));
        assertThat(r.stepData()).containsEntry("associeUnique", true);
    }

    @Test
    void etr_step2_partage_la_saisie_succursale_du_workflow_marocain() {
        // Meme noyau (SuccursaleSaisieStep) : les memes regles s'appliquent.
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        Map<String, Object> p = maStep2();
        StepResult r = wf.executeStep(ctx(2, p, Map.of()));
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) r.stepData().get("succursale");
        assertThat(succ).containsEntry("villeGreffe", "MARRAKECH");

        Map<String, Object> ko = maStep2();
        @SuppressWarnings("unchecked")
        Map<String, Object> koSucc = (Map<String, Object>) ko.get("succursale");
        koSucc.put("dotationPresente", true);
        assertThat(wf.executeStep(ctx(2, ko, Map.of())).canAdvance()).isFalse();
    }

    @Test
    void etr_step2_expose_le_representant_sans_double_saisie() {
        // Le PV etranger consomme `representant`, l'avis `succursale.responsable` :
        // une seule saisie doit alimenter les deux.
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        Map<String, Object> p = maStep2();
        @SuppressWarnings("unchecked")
        Map<String, Object> succ = (Map<String, Object>) p.get("succursale");
        succ.put("responsablePresent", true);
        succ.put("responsable", Map.of(
                "nom", "EL FASSI", "prenom", "Youssef",
                "adresse", "42 RUE ANFA, CASABLANCA",
                "pieceNumero", "BK998877", "pouvoirs", "diriger la succursale"));
        StepResult r = wf.executeStep(ctx(2, p, Map.of()));
        assertThat(r.canAdvance()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> rep = (Map<String, Object>) r.stepData().get("representant");
        assertThat(rep).containsEntry("nom", "EL FASSI");
    }

    @Test
    void etr_step3_exige_pv_et_annonce_et_choisit_la_variante_ETRANGERE() {
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        assertThat(wf.executeStep(ctx(3, Map.of("pvValide", true), Map.of())).canAdvance())
                .isFalse();

        StepResult r = wf.executeStep(ctx(3,
                Map.of("pvValide", true, "annonceValide", true), Map.of()));
        assertThat(r.stepData())
                .containsEntry("pvTemplate", "PV_CREATION_SUCCURSALE_ETRANGERE_SARL");
        assertThat(r.stepData()).containsEntry(
                "annonceTemplate", "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL");
        // Tracabilite : ce modele n'est PAS du directeur.
        assertThat(r.stepData()).containsEntry("annonceOrigin", "derive-jurika");
    }

    @Test
    void etr_step3_relit_la_nature_unipersonnelle_de_l_etape_1() {
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        StepResult r = wf.executeStep(ctx(3,
                Map.of("pvValide", true, "annonceValide", true),
                Map.of("step1", Map.of("associeUnique", true))));
        assertThat(r.stepData())
                .containsEntry("pvTemplate", "PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU");
        assertThat(r.stepData()).containsEntry(
                "annonceTemplate", "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL_AU");
    }

    @Test
    void etr_step4_pieces_jointes_vides_ne_bloquent_pas() {
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        StepResult r = wf.executeStep(ctx(4, Map.of(), Map.of()));
        assertThat(r.canAdvance()).isTrue();
        assertThat(r.stepData()).containsEntry("piecesJointesCount", 0);
    }

    @Test
    void etr_etape_hors_plage_est_rejetee() {
        SuccursaleEtrWorkflow wf = new SuccursaleEtrWorkflow();
        assertThatThrownBy(() -> wf.executeStep(ctx(8, Map.of(), Map.of())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("1..5");
    }
}
