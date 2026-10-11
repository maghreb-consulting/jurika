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
        assertThat(mapper.gabarit("RAPPORT_LIQUIDATION_DIRECTEUR", Map.of("formeJuridique", "SARL")))
                .isEqualTo("RAPPORT_LIQUIDATEUR_SARL");
        assertThat(mapper.gabarit("RAPPORT_LIQUIDATION_DIRECTEUR", Map.of("formeJuridique", "SARL_AU")))
                .isEqualTo("RAPPORT_LIQUIDATEUR_SARL_AU");
        assertThat(mapper.gabarit("RAPPORT_LIQUIDATION_DIRECTEUR", Map.of("associeUnique", true)))
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

    @Test
    void mali_quand_l_actif_net_ne_couvre_pas_le_capital() {
        Map<String, Object> p = fixture("liquidation.json");
        Map<String, Object> c = bloc(p, "cloture");
        c.put("actifRealise", 120_000);
        c.put("passifRegle", 50_000);
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p);
        assertThat(v).containsEntry("LIQUIDATION_RESULTAT_TYPE", "mali").doesNotContainKey("REPARTITION_SOLDE");
        assertThat(txt(v.get("LIQUIDATION_MALI_CHIFFRES"))).isEqualTo("40 000");
        assertThat(txt(v.get("LIQUIDATION_CAPITAL_REMBOURSE_CHIFFRES"))).isEqualTo("60 000");
    }

    @Test
    void solde_nul() {
        Map<String, Object> p = fixture("liquidation.json");
        Map<String, Object> c = bloc(p, "cloture");
        c.put("actifRealise", 260_000);
        c.put("passifRegle", 150_000);
        assertThat(mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p)).containsEntry("LIQUIDATION_RESULTAT_TYPE", "nul");
    }

    @Test
    void remuneration_du_liquidateur_deduite() {
        Map<String, Object> p = fixture("liquidation.json");
        Map<String, Object> l = bloc(p, "liquidateur");
        l.put("remunerationApplicable", "oui");
        l.put("remunerationMontant", 20_000);
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
        refuse("LIQUIDATION_FRAIS_CHIFFRES", p -> bloc(p, "cloture").remove("frais"));
        refuse("LIQUIDATION_ACTIF_REALISE_TOTAL_CHIFFRES", p -> bloc(p, "cloture").remove("actifRealise"));
        refuse("LIQUIDATION_DISPONIBLE_CHIFFRES", p -> bloc(p, "cloture").put("passifRegle", 400_000));
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
            bloc(p, "cloture").put("actifRealise", 300_001);
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

    @Test
    void rapport_final_du_liquidateur() {
        Map<String, Object> v = mapper.map("RAPPORT_LIQUIDATION_DIRECTEUR", fixture("liquidation.json"));
        assertThat(v).containsEntry("RAPPORT_LIQUIDATEUR_NATURE", "final")
                .containsEntry("LIQUIDATEUR_FORME", "personne physique")
                .doesNotContainKey("ASSOCIES");
    }
}
