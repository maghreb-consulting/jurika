package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static ma.jurika.ai.workflow.mapper.DissolutionMapperTest.bloc;
import static ma.jurika.ai.workflow.mapper.DissolutionMapperTest.fixture;
import static ma.jurika.ai.workflow.mapper.DissolutionMapperTest.txt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4d (E11d) : {@link LiquidationMapper} rend le PV de cloture par
 * {@code PV_CLOTURE_LIQUIDATION_SARL(_AU)} et le rapport par {@code RAPPORT_LIQUIDATEUR_SARL(_AU)}.
 * Compte definitif CALCULE (boni, mali ou nul, jamais suppose), repartition au prorata,
 * donnees pilotes exigees. Rendu sur le corpus reel : {@link LiquidationCorpusRenderTest}.
 */
class LiquidationMapperTest {

    private final LiquidationMapper mapper = new LiquidationMapper();

    @Test
    void codes_demandes_et_modeles_du_corpus() {
        assertThat(mapper.workflowCode()).isEqualTo("LIQUIDATION");
        assertThat(mapper.gabarit("PV_DISSOLUTION_LIQUIDATION_SARL", Map.of())).isEqualTo("PV_CLOTURE_LIQUIDATION_SARL");
        assertThat(mapper.gabarit("PV_DISSOLUTION_LIQUIDATION_SARL_AU", Map.of())).isEqualTo("PV_CLOTURE_LIQUIDATION_SARL_AU");
        // Lot L5 (E4) : le rapport du liquidateur est rendu par le modele du corpus selon la forme.
        assertThat(mapper.gabarit("RAPPORT_LIQUIDATION_DIRECTEUR", Map.of("formeJuridique", "SARL")))
                .isEqualTo("RAPPORT_LIQUIDATEUR_SARL");
        assertThat(mapper.gabarit("RAPPORT_LIQUIDATION_DIRECTEUR", Map.of("formeJuridique", "SARL_AU")))
                .isEqualTo("RAPPORT_LIQUIDATEUR_SARL_AU");
        assertThat(mapper.gabarit("RAPPORT_LIQUIDATION_DIRECTEUR",
                Map.of("formeJuridique", "SARL", "societe", Map.of("formeJuridique", "SARL AU"))))
                .isEqualTo("RAPPORT_LIQUIDATEUR_SARL_AU");
        assertThat(mapper.gabarit("ANNONCE_LEGALE_LIQUIDATION_SARL", Map.of())).isEqualTo("ANNONCE_LEGALE_LIQUIDATION_SARL");
    }

    @Test
    void compte_definitif_calcule_boni_et_repartition_au_prorata() {
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", fixture("liquidation.json"));
        assertThat(v).containsEntry("LIQUIDATION_RESULTAT_TYPE", "boni")
                .containsEntry("COMPTE_DEFINITIF_APPROBATION", "approuve")
                .containsEntry("LIQUIDATION_QUITUS_SENS", "accorde")
                .containsEntry("CLOTURE_CONVOCATION_AUTEUR", "liquidateur")
                .containsEntry("DISSOLUTION_DATE_DECISION", "15/05/2026")
                .containsEntry("PRESCRIPTION_QUINQUENNALE_DATE_TERME", "25/05/2031")
                .containsEntry("LIQUIDATION_DECLARATION_FINALE_DATE_LIMITE", "04/08/2026")
                .containsEntry("LIQUIDATION_SIEGE_IDENTIQUE", "non")
                .containsEntry("PASSIF_EVENTUEL_APPLICABLE", "non")
                .containsEntry("RETENUE_SOURCE_APPLICABLE", "oui");
        assertThat(txt(v.get("LIQUIDATION_DISPONIBLE_CHIFFRES"))).isEqualTo("140 000");
        assertThat(txt(v.get("LIQUIDATION_CAPITAL_REMBOURSE_CHIFFRES"))).isEqualTo("100 000");
        assertThat(txt(v.get("LIQUIDATION_BONI_CHIFFRES"))).isEqualTo("40 000");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lignes = (List<Map<String, Object>>) v.get("REPARTITION_SOLDE");
        assertThat(lignes).hasSize(2);
        assertThat(txt(lignes.get(0).get("ASSOCIE_NOMINAL_REMBOURSE_CHIFFRES"))).isEqualTo("60 000");
        assertThat(txt(lignes.get(0).get("ASSOCIE_SOLDE_BRUT_CHIFFRES"))).isEqualTo("24 000");
        assertThat(txt(lignes.get(0).get("ASSOCIE_RETENUE_SOURCE_CHIFFRES"))).isEqualTo("3 600");
        assertThat(txt(lignes.get(0).get("ASSOCIE_SOLDE_NET_CHIFFRES"))).isEqualTo("80 400");
        assertThat(txt(lignes.get(1).get("ASSOCIE_SOLDE_NET_CHIFFRES"))).isEqualTo("53 600");
    }

    @Test
    void associe_unique_net_attribue() {
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL_AU", fixture("liquidation.json"));
        assertThat(txt(v.get("ASSOCIE_SOLDE_NET_CHIFFRES"))).isEqualTo("134 000");
        assertThat(v).containsEntry("ASSOCIE_UNIQUE", "M. Ahmed Alaoui").doesNotContainKey("QUORUM_ATTEINT");
    }

    /**
     * L6b (E19) : le compte du PV vient du rapport final (une ligne de realisation et une ligne
     * d'apurement), les totaux de l'etape 1 concordants.
     */
    private static void comptes(Map<String, Object> p, long actif, long passif) {
        Map<String, Object> c = bloc(p, "cloture");
        c.put("actifRealise", actif);
        c.put("passifRegle", passif);
        Map<String, Object> r = bloc(p, "rapportLiquidateur");
        r.put("realisations", new java.util.ArrayList<>(List.of(new java.util.LinkedHashMap<>(Map.of(
                "designation", "Fonds de commerce", "prix", actif, "valeurInventaire", actif)))));
        r.put("apurements", new java.util.ArrayList<>(List.of(new java.util.LinkedHashMap<>(Map.of(
                "creancier", "BANQUE POPULAIRE", "montantAdmis", passif, "montantRegle", passif)))));
    }

    @Test
    void mali_quand_l_actif_net_ne_couvre_pas_le_capital() {
        Map<String, Object> p = fixture("liquidation.json");
        comptes(p, 120_000, 50_000);
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p);
        assertThat(v).containsEntry("LIQUIDATION_RESULTAT_TYPE", "mali").doesNotContainKey("REPARTITION_SOLDE");
        assertThat(txt(v.get("LIQUIDATION_MALI_CHIFFRES"))).isEqualTo("40 000");
        assertThat(txt(v.get("LIQUIDATION_CAPITAL_REMBOURSE_CHIFFRES"))).isEqualTo("60 000");
    }

    @Test
    void solde_nul() {
        Map<String, Object> p = fixture("liquidation.json");
        comptes(p, 260_000, 150_000);
        assertThat(mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p)).containsEntry("LIQUIDATION_RESULTAT_TYPE", "nul");
    }

    @Test
    void remuneration_du_liquidateur_deduite() {
        Map<String, Object> p = fixture("liquidation.json");
        Map<String, Object> l = bloc(p, "liquidateur");
        l.put("remunerationApplicable", "oui");
        l.put("remunerationMontant", 20_000);
        // Le tableau des frais du rapport final comprend la remuneration (10 000 + 20 000).
        liste(p, "frais").add(new java.util.LinkedHashMap<>(Map.of("nature", "R\u00e9mun\u00e9ration du liquidateur",
                "montant", 20_000)));
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p);
        assertThat(txt(v.get("LIQUIDATION_DISPONIBLE_CHIFFRES"))).isEqualTo("120 000");
    }

    private void refuse(String variable, Consumer<Map<String, Object>> modification) {
        Map<String, Object> p = fixture("liquidation.json");
        modification.accept(p);
        assertThatThrownBy(() -> mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p))
                .isInstanceOf(GenerationRefuseeException.class)
                .satisfies(e -> assertThat(((GenerationRefuseeException) e).donnees())
                        .extracting(GenerationRefuseeException.DonneeManquante::variable).contains(variable));
    }

    @Test
    void le_sens_du_solde_ne_se_suppose_pas() {
        // L6b (E19) : le compte vient du rapport final ; sans lui, rien ne se calcule.
        refuse("RAPPORT_LIQUIDATEUR", p -> p.remove("rapportLiquidateur"));
        refuse("LIQUIDATION_FRAIS_TOTAL_CHIFFRES", p -> liste(p, "frais").get(0).put("montant", "abc"));
        refuse("LIQUIDATION_ACTIF_REALISE_TOTAL_CHIFFRES", p -> bloc(p, "cloture").put("actifRealise", 310_000));
        refuse("LIQUIDATION_FRAIS_TOTAL_CHIFFRES", p -> bloc(p, "cloture").put("frais", 12_000));
        refuse("LIQUIDATION_DISPONIBLE_CHIFFRES", p -> comptes(p, 300_000, 400_000));
    }

    @Test
    void donnees_pilotes_jamais_supposees() {
        refuse("QUORUM_ATTEINT", p -> p.remove("quorumAtteint"));
        refuse("CLOTURE_CONVOCATION_AUTEUR", p -> bloc(p, "cloture").remove("convocationAuteur"));
        refuse("COMPTE_DEFINITIF_APPROBATION", p -> bloc(p, "cloture").remove("compteDefinitifApprobation"));
        refuse("LIQUIDATION_QUITUS_SENS", p -> bloc(p, "cloture").remove("quitus"));
        refuse("LIQUIDATION_COMPTES_BANCAIRES_CLOTURES", p -> bloc(p, "cloture").remove("comptesBancairesClotures"));
        refuse("RETENUE_SOURCE_APPLICABLE", p -> bloc(p, "cloture").remove("retenueApplicable"));
        refuse("PASSIF_EVENTUEL_APPLICABLE", p -> bloc(p, "cloture").remove("passifEventuelApplicable"));
        refuse("CAC_EXISTE", p -> bloc(p, "mentionsPv").remove("cacExiste"));
        refuse("LIQUIDATION_SIEGE_IDENTIQUE", p -> {
            bloc(p, "liquidateur").remove("siegeIdentique");
            bloc(p, "liquidateur").remove("siege");
        });
        // Exoneration invoquee : la retenue de chacun ne se repartit pas au prorata.
        refuse("ASSOCIE_RETENUE_SOURCE_CHIFFRES", p -> {
            bloc(p, "cloture").put("retenueExonerationInvoquee", "oui");
            bloc(p, "cloture").put("retenueExonerationBeneficiaire", "Mme Fatima Bennani");
        });
    }

    @Test
    void siege_au_siege_social_enregistre_a_la_dissolution() {
        Map<String, Object> p = fixture("liquidation.json");
        bloc(p, "liquidateur").put("siegeIdentique", "oui");
        bloc(p, "liquidateur").remove("siege");
        assertThat(mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p)).containsEntry("LIQUIDATION_SIEGE_IDENTIQUE", "oui")
                .doesNotContainKey("LIQUIDATION_SIEGE_ADRESSE");
    }

    @Test
    void prorata_qui_ne_tombe_pas_juste_refuse() {
        refuse("ASSOCIE_SOLDE_BRUT_CHIFFRES", p -> {
            // 1 000 parts : 333 / 667 ; boni 40 001 : quotes-parts non exactes au centime.
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> a = (List<Map<String, Object>>) p.get("associes");
            a.get(0).put("nombreParts", 333);
            a.get(1).put("nombreParts", 667);
            comptes(p, 300_001, 150_000);
        });
    }

    @Test
    void compte_refuse_ni_quitus_ni_repartition() {
        Map<String, Object> p = fixture("liquidation.json");
        Map<String, Object> c = bloc(p, "cloture");
        c.put("compteDefinitifApprobation", "refuse");
        c.put("refusMotifs", "les pi\u00e8ces justificatives n'ont pas \u00e9t\u00e9 produites");
        c.remove("quitus");
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p);
        assertThat(v).containsEntry("COMPTE_DEFINITIF_APPROBATION", "refuse").doesNotContainKey("LIQUIDATION_QUITUS_SENS")
                .doesNotContainKey("REPARTITION_SOLDE");
    }

    @Test
    void convocation_par_mandataire_de_justice() {
        Map<String, Object> p = fixture("liquidation.json");
        Map<String, Object> c = bloc(p, "cloture");
        c.put("convocationAuteur", "mandataire");
        c.put("ordonnance", Map.of("associeDemandeur", "Mme Fatima Bennani", "tribunal", "Casablanca",
                "numero", "1234/2026", "date", "2026-05-30", "mandataireNom", "Me Karim Tazi"));
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p);
        assertThat(v).containsEntry("ORDONNANCE_REFERE_NUMERO", "1234/2026")
                .containsEntry("ORDONNANCE_REFERE_DATE", "30/05/2026")
                .containsEntry("MANDATAIRE_REFERE_NOM", "Me Karim Tazi");
    }

    // ------------------------------------------------------------------ rapport du liquidateur (L5 E4)

    private static final String RAPPORT = "RAPPORT_LIQUIDATION_DIRECTEUR";

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> liste(Map<String, Object> p, String cle) {
        return (List<Map<String, Object>>) bloc(p, "rapportLiquidateur").get(cle);
    }

    private void rapportRefuse(String variable, Consumer<Map<String, Object>> modification) {
        Map<String, Object> p = fixture("liquidation.json");
        modification.accept(p);
        assertThatThrownBy(() -> mapper.map(RAPPORT, p))
                .isInstanceOf(GenerationRefuseeException.class)
                .satisfies(e -> assertThat(((GenerationRefuseeException) e).donnees())
                        .extracting(GenerationRefuseeException.DonneeManquante::variable).contains(variable));
    }

    @Test
    void rapport_totaux_et_boni_calcules_depuis_les_operations_saisies() {
        Map<String, Object> v = mapper.map(RAPPORT, fixture("liquidation.json"));
        assertThat(v).containsEntry("RAPPORT_LIQUIDATEUR_NATURE", "final")
                .containsEntry("LIQUIDATION_RESULTAT_TYPE", "boni")
                .containsEntry("LIQUIDATION_PROLONGEE", "non")
                .containsEntry("RAPPORT_CREANCES_IRRECOUVRABLES_EXISTENT", "non")
                .containsEntry("LIQUIDATEUR_EST_ANCIEN_GERANT", "oui")
                .containsEntry("RAPPORT_ANNEXES_EXISTENT", "oui")
                .containsEntry("RAPPORT_LIQUIDATEUR_DATE_ARRETE_COMPTES", "10/06/2026")
                .containsEntry("DATE_SIGNATURE", "12/06/2026");
        assertThat(txt(v.get("LIQUIDATION_ACTIF_REALISE_TOTAL_CHIFFRES"))).isEqualTo("300 000");
        assertThat(txt(v.get("LIQUIDATION_PLUS_VALUES_TOTAL_CHIFFRES"))).isEqualTo("30 000");
        assertThat(txt(v.get("LIQUIDATION_MOINS_VALUES_TOTAL_CHIFFRES"))).isEqualTo("5 000");
        assertThat(txt(v.get("LIQUIDATION_PASSIF_ADMIS_TOTAL_CHIFFRES"))).isEqualTo("150 000");
        assertThat(txt(v.get("LIQUIDATION_PASSIF_REGLE_TOTAL_CHIFFRES"))).isEqualTo("150 000");
        assertThat(txt(v.get("LIQUIDATION_PASSIF_RESTANT_CHIFFRES"))).isEqualTo("0");
        assertThat(txt(v.get("LIQUIDATION_FRAIS_TOTAL_CHIFFRES"))).isEqualTo("10 000");
        assertThat(txt(v.get("LIQUIDATION_TRESORERIE_DISPONIBLE_CHIFFRES"))).isEqualTo("140 000");
        assertThat(txt(v.get("LIQUIDATION_CAPITAL_REMBOURSE_CHIFFRES"))).isEqualTo("100 000");
        assertThat(txt(v.get("LIQUIDATION_BONI_CHIFFRES"))).isEqualTo("40 000");
        assertThat(v.get("LIQUIDATION_BONI_LETTRES")).isEqualTo("QUARANTE MILLE");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> associes = (List<Map<String, Object>>) v.get("ASSOCIES");
        assertThat(associes).extracting(a -> txt(a.get("ASSOCIE_BONI_CHIFFRES"))).containsExactly("24 000", "16 000");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> real = (List<Map<String, Object>>) v.get("RAPPORT_REALISATION_ACTIF");
        assertThat(real).extracting(l -> l.get("REALISATION_RESULTAT_SENS")).containsExactly("plus-value", "moins-value");
    }

    @Test
    void rapport_montants_avec_centimes() {
        Map<String, Object> p = fixture("liquidation.json");
        liste(p, "realisations").get(0).put("prix", "249 999,50");
        liste(p, "realisations").get(1).put("prix", "50000.50");
        Map<String, Object> v = mapper.map(RAPPORT, p);
        assertThat(txt(v.get("LIQUIDATION_ACTIF_REALISE_TOTAL_CHIFFRES"))).isEqualTo("300 000");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> real = (List<Map<String, Object>>) v.get("RAPPORT_REALISATION_ACTIF");
        assertThat(txt(real.get(0).get("REALISATION_PRIX"))).isEqualTo("249 999,50");
        assertThat(txt(real.get(1).get("REALISATION_RESULTAT_MONTANT"))).isEqualTo("4 999,50");
        assertThat(RapportLiquidateurCorpusVarsBuilder.lettresMontant(new java.math.BigDecimal("300000.50")))
                .isEqualTo("TROIS CENT MILLE DIRHAMS ET CINQUANTE CENTIMES");
    }

    @Test
    void rapport_associe_unique_et_mali() {
        Map<String, Object> p = fixture("liquidation.json");
        bloc(p, "societe").put("formeJuridique", "SARL_AU");
        bloc(p, "cloture").put("actifRealise", 200_000);
        liste(p, "realisations").get(0).put("prix", 150_000);
        assertThat(mapper.gabarit(RAPPORT, p)).isEqualTo("RAPPORT_LIQUIDATEUR_SARL_AU");
        Map<String, Object> v = mapper.map(RAPPORT, p);
        assertThat(v).containsEntry("LIQUIDATION_RESULTAT_TYPE", "mali").containsEntry("ASSOCIE_NOM", "M. Ahmed Alaoui")
                .doesNotContainKey("ASSOCIES").doesNotContainKey("LIQUIDATION_BONI_CHIFFRES")
                .doesNotContainKey("DISSOLUTION_ORGANE");
        assertThat(txt(v.get("LIQUIDATION_MALI_CHIFFRES"))).isEqualTo("60 000");
        assertThat(txt(v.get("LIQUIDATION_CAPITAL_REMBOURSE_CHIFFRES"))).isEqualTo("40 000");
    }

    @Test
    void rapport_ne_contredit_pas_le_proces_verbal_de_cloture() {
        rapportRefuse("LIQUIDATION_ACTIF_REALISE_TOTAL_CHIFFRES", p -> bloc(p, "cloture").put("actifRealise", 310_000));
        rapportRefuse("LIQUIDATION_PASSIF_REGLE_TOTAL_CHIFFRES", p -> bloc(p, "cloture").put("passifRegle", 140_000));
        rapportRefuse("LIQUIDATION_FRAIS_TOTAL_CHIFFRES", p -> bloc(p, "cloture").put("frais", 12_000));
        // Remuneration du liquidateur : comprise dans le total des frais du rapport.
        rapportRefuse("LIQUIDATION_FRAIS_TOTAL_CHIFFRES", p -> {
            bloc(p, "liquidateur").put("remunerationApplicable", "oui");
            bloc(p, "liquidateur").put("remunerationMontant", 20_000);
            bloc(p, "cloture").remove("frais");
        });
        // Passif connu restant : le rapport final le declare acquitte.
        rapportRefuse("LIQUIDATION_PASSIF_RESTANT_CHIFFRES", p -> liste(p, "apurements").get(0).put("montantAdmis", 120_000));
        rapportRefuse("APUREMENT_SOLDE", p -> liste(p, "apurements").get(0).put("montantAdmis", 90_000));
        rapportRefuse("LIQUIDATION_TRESORERIE_DISPONIBLE_CHIFFRES", p -> {
            bloc(p, "cloture").remove("actifRealise");
            liste(p, "realisations").get(0).put("prix", 100_000);
        });
    }

    @Test
    void rapport_donnees_pilotes_et_tableaux_jamais_supposes() {
        rapportRefuse("FORME_JURIDIQUE", p -> bloc(p, "societe").remove("formeJuridique"));
        rapportRefuse("LIQUIDATION_PROLONGEE", p -> bloc(p, "rapportLiquidateur").remove("prolongee"));
        rapportRefuse("TVA_ASSUJETTIE", p -> bloc(p, "rapportLiquidateur").remove("tvaAssujettie"));
        rapportRefuse("LIQUIDATEUR_EST_ANCIEN_GERANT", p -> bloc(p, "liquidateur").remove("ancienGerant"));
        rapportRefuse("RAPPORT_CREANCES_IRRECOUVRABLES_EXISTENT",
                p -> bloc(p, "rapportLiquidateur").remove("creancesIrrecouvrablesExistent"));
        // Tableau annonce mais vide : nomme, jamais imprime vide.
        rapportRefuse("RAPPORT_PASSIFS_CONTESTES_EXISTENT", p -> bloc(p, "rapportLiquidateur").put("passifsContestesExistent", "oui"));
        rapportRefuse("SALARIES_DIX_OU_PLUS", p -> {
            bloc(p, "rapportLiquidateur").put("personnelExiste", "oui");
            bloc(p, "rapportLiquidateur").put("personnel", List.of(Map.of("nom", "M. Karim Saidi", "fonction", "comptable",
                    "sortContrat", "licenciement", "dateEffet", "2026-05-31", "indemnites", 12_000,
                    "soldeToutCompteDate", "2026-05-31")));
            bloc(p, "rapportLiquidateur").put("chargesPersonnelTotal", 15_000);
        });
        rapportRefuse("RAPPORT_DILIGENCES_FISCALES_SOCIALES", p -> bloc(p, "rapportLiquidateur").put("diligences", List.of()));
        rapportRefuse("REALISATION_PRIX", p -> liste(p, "realisations").get(0).put("prix", "deux cent mille"));
        rapportRefuse("LIQUIDATEUR_FRAIS_REMBOURSES_MONTANT", p -> bloc(p, "rapportLiquidateur").remove("fraisRembourses"));
        rapportRefuse("BONI_RETENUE_SOURCE_APPLICABLE", p -> bloc(p, "cloture").remove("retenueApplicable"));
    }

    @Test
    void rapport_solde_nul_et_prorata_inexact_refuses() {
        // Disponible 100 000 = capital : le modele n'a pas de branche "nul" (ecart du cabinet).
        rapportRefuse("LIQUIDATION_RESULTAT_TYPE", p -> {
            bloc(p, "cloture").put("actifRealise", 260_000);
            liste(p, "realisations").get(0).put("prix", 210_000);
        });
        // 333 / 667 parts, boni de 40 001 : quotes-parts non exactes au centime.
        rapportRefuse("ASSOCIE_BONI_CHIFFRES", p -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> a = (List<Map<String, Object>>) p.get("associes");
            a.get(0).put("nombreParts", 333);
            a.get(1).put("nombreParts", 667);
            bloc(p, "cloture").put("actifRealise", 300_001);
            liste(p, "realisations").get(0).put("prix", 250_001);
        });
    }
}
