package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.GenerationRefuseeException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4d (E11d) : {@link DissolutionMapper} rend le PV de dissolution par le modele du corpus
 * {@code PV_DISSOLUTION_ANTICIPEE_SARL(_AU)} ({@link DissolutionPvCorpusVarsBuilder}).
 * Tests unitaires sans corpus : choix du modele, donnees calculees, donnees pilotes exigees.
 * Le rendu sur le corpus reel est garde par {@link LiquidationCorpusRenderTest}.
 */
class DissolutionMapperTest {

    private final DissolutionMapper mapper = new DissolutionMapper();

    @SuppressWarnings("unchecked")
    static Map<String, Object> fixture(String nom) {
        try (InputStream in = DissolutionMapperTest.class.getResourceAsStream("/workflow-fixtures/" + nom)) {
            return copie(new ObjectMapper().readValue(in, Map.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Copie profonde modifiable. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> copie(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (v instanceof Map<?, ?> mm) out.put(k, copie((Map<String, Object>) mm));
            else if (v instanceof List<?> l) out.put(k, new java.util.ArrayList<>(l.stream()
                    .map(e -> e instanceof Map<?, ?> em ? (Object) copie((Map<String, Object>) em) : e).toList()));
            else out.put(k, v);
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> bloc(Map<String, Object> p, String cle) {
        return (Map<String, Object>) p.computeIfAbsent(cle, k -> new LinkedHashMap<>());
    }

    /** Montants : espace insecable (fine ou non) ramene a l'espace ordinaire. */
    static String txt(Object o) {
        return String.valueOf(o).replace('\u202f', ' ').replace('\u00a0', ' ');
    }

    static Map<String, Object> liquidateurPersonneMorale() {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("forme", "morale");
        l.put("denomination", "FIDUCIAIRE ATLAS");
        l.put("formeJuridique", "SARL");
        l.put("capital", "500000");
        l.put("siegeSocial", "8 RUE IBN BATOUTA, RABAT");
        l.put("rcVille", "Rabat");
        l.put("rcNumero", "98765");
        l.put("ice", "002233445000066");
        l.put("representantNom", "Mme Salma Idrissi");
        l.put("representantQualite", "g\u00e9rante");
        l.put("mandatDateEffet", "2026-05-15");
        l.put("dureeMandat", "trois ans");
        l.put("remunerationApplicable", "oui");
        l.put("remunerationMontant", "20000");
        l.put("remunerationModalites", "payable en fin de mission");
        l.put("redditionPeriodicite", "chaque trimestre");
        l.put("siege", "20 BOULEVARD ZERKTOUNI, CASABLANCA");
        return l;
    }

    @Test
    void codes_demandes_et_modeles_du_corpus() {
        assertThat(mapper.workflowCode()).isEqualTo("DISSOLUTION");
        assertThat(mapper.supportedTemplates()).containsExactlyInAnyOrder("PV_DISSOLUTION_LIQUIDATION_SARL",
                "PV_DISSOLUTION_LIQUIDATION_SARL_AU", "ANNONCE_LEGALE_DISSOLUTION_SARL", "ANNONCE_LEGALE_DISSOLUTION_SARL_AU");
        assertThat(mapper.gabarit("PV_DISSOLUTION_LIQUIDATION_SARL", Map.of())).isEqualTo("PV_DISSOLUTION_ANTICIPEE_SARL");
        assertThat(mapper.gabarit("PV_DISSOLUTION_LIQUIDATION_SARL_AU", Map.of())).isEqualTo("PV_DISSOLUTION_ANTICIPEE_SARL_AU");
        assertThat(mapper.gabarit("ANNONCE_LEGALE_DISSOLUTION_SARL", Map.of())).isEqualTo("ANNONCE_LEGALE_DISSOLUTION_SARL");
    }

    @Test
    void pv_sarl_donnees_calculees_et_saisies() {
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", fixture("dissolution.json"));
        assertThat(v).containsEntry("DATE_SIGNATURE", "15/05/2026")
                .containsEntry("ASSEMBLEE_HEURE", "10 heures")
                .containsEntry("ASSEMBLEE_HEURE_CLOTURE", "12 heures")
                .containsEntry("VALEUR_NOMINALE_PART", "100")
                .containsEntry("QUORUM_ATTEINT", "oui")
                .containsEntry("RESOLUTION_RESULTAT", "\u00e0 l'unanimit\u00e9")
                .containsEntry("DISSOLUTION_MOTIF", "volonte_associes")
                .containsEntry("DISSOLUTION_DATE_EFFET", "15/05/2026")
                .containsEntry("LIQUIDATEUR_FORME", "personne physique")
                .containsEntry("LIQUIDATEUR_NATIONALITE", "marocaine")
                .containsEntry("LIQUIDATEUR_DATE_NAISSANCE", "12/03/1975")
                .containsEntry("LIQUIDATEUR_DUREE_MANDAT", "trois ans")
                .containsEntry("LIQUIDATEUR_REMUNERATION_APPLICABLE", "non")
                .containsEntry("GERANT_DEVIENT_LIQUIDATEUR", "oui")
                .containsEntry("LIQUIDATEUR_EST_ANCIEN_GERANT", "oui")
                .containsEntry("GERANT_NOM", "M. Ahmed Alaoui")
                .containsEntry("GERANT_QUALITE", "g\u00e9rant")
                .containsEntry("GERANT_QUITUS_DONNE", "oui")
                .containsEntry("CAC_EXISTE", "non")
                .containsEntry("SALARIES_DIX_OU_PLUS", "non")
                .containsEntry("LIQUIDATION_SIEGE_IDENTIQUE", "non")
                .containsEntry("LIQUIDATION_SIEGE_ADRESSE", "20 BOULEVARD ZERKTOUNI, CASABLANCA")
                .containsEntry("ANNONCE_LEGALE_JOURNAL", "LE JOURNAL DES ANNONCES")
                .containsEntry("NOMBRE_ORIGINAUX", "quatre")
                .containsEntry("ORDRE_DU_JOUR_COMPLEMENTAIRE_EXISTE", "non")
                .containsEntry("LIQUIDATEUR_POUVOIRS_COMPLEMENTAIRES_EXISTENT", "non");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> associes = (List<Map<String, Object>>) v.get("ASSOCIES");
        // Presence accordee : "Mme Fatima Bennani, ..., presente".
        assertThat(associes.get(1)).containsEntry("ASSOCIE_PRESENCE", "pr\u00e9sente");
        assertThat(v.toString()).doesNotContain("exerc\u00e9es \u00e0 titre gratuit");
    }

    @Test
    void pv_sarl_au_identite_de_l_associe_unique_et_sans_quorum() {
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL_AU", fixture("dissolution.json"));
        assertThat(v).containsEntry("ASSOCIE_UNIQUE", "M. Ahmed Alaoui").doesNotContainKey("QUORUM_ATTEINT");
    }

    @Test
    void liquidateur_personne_morale() {
        Map<String, Object> p = fixture("dissolution.json");
        p.put("liquidateur", liquidateurPersonneMorale());
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p);
        assertThat(v).containsEntry("LIQUIDATEUR_FORME", "personne morale")
                .containsEntry("LIQUIDATEUR_DENOMINATION", "FIDUCIAIRE ATLAS")
                .containsEntry("LIQUIDATEUR_REPRESENTANT_PERMANENT_NOM", "Mme Salma Idrissi")
                .containsEntry("LIQUIDATEUR_EST_ANCIEN_GERANT", "non")
                .containsEntry("GERANT_DEVIENT_LIQUIDATEUR", "non")
                .containsEntry("GERANT_NOM", "M. Ahmed Alaoui")
                .containsEntry("LIQUIDATEUR_REMUNERATION_APPLICABLE", "oui")
                .containsEntry("LIQUIDATEUR_REMUNERATION_MODALITES", "payable en fin de mission");
        assertThat(txt(v.get("LIQUIDATEUR_CAPITAL_CHIFFRES"))).isEqualTo("500 000");
        assertThat(txt(v.get("LIQUIDATEUR_REMUNERATION_MONTANT_CHIFFRES"))).isEqualTo("20 000");
        assertThat(String.valueOf(v.get("LIQUIDATEUR_REMUNERATION_MONTANT_LETTRES"))).isEqualToIgnoringCase("vingt mille");
    }

    @Test
    void motif_article_86_et_ses_precisions() {
        Map<String, Object> p = fixture("dissolution.json");
        Map<String, Object> d = bloc(p, "dissolution");
        d.put("nature", "situation_nette_art86");
        d.put("situationNetteMontant", "20000");
        d.put("approbationComptesDate", "2026-04-20");
        d.put("art86MotifsEcartement", "les associ\u00e9s ne souhaitent pas reconstituer les capitaux propres");
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p);
        assertThat(v).containsEntry("DISSOLUTION_MOTIF", "situation_nette_art86")
                .containsEntry("EXERCICE_CLOS_DATE", "31/12/2025")
                .containsEntry("ASSEMBLEE_APPROBATION_COMPTES_DATE", "20/04/2026");
        assertThat(txt(v.get("SITUATION_NETTE_MONTANT"))).isEqualTo("20 000");
    }

    @Test
    void siege_de_la_liquidation_au_siege_social_comme_l_envoie_l_ecran() {
        // Charge de l'ecran (etape 1) : choix porte par le liquidateur, aucune adresse.
        Map<String, Object> p = fixture("dissolution.json");
        bloc(p, "dissolution").remove("siegeLiquidationIdentique");
        bloc(p, "liquidateur").put("siegeIdentique", "oui");
        bloc(p, "liquidateur").put("siege", "");
        bloc(p, "liquidateur").put("pouvoirsComplementaires", List.of("vendre le fonds de commerce", " "));
        Map<String, Object> v = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p);
        assertThat(v).containsEntry("LIQUIDATION_SIEGE_IDENTIQUE", "oui").doesNotContainKey("LIQUIDATION_SIEGE_ADRESSE")
                .containsEntry("LIQUIDATEUR_POUVOIRS_COMPLEMENTAIRES_EXISTENT", "oui");
        assertThat((List<?>) v.get("LIQUIDATEUR_POUVOIRS_COMPLEMENTAIRES")).hasSize(1);
    }

    @Test
    void heures_d_usage() {
        assertThat(LiquidationCorpusCommun.heure("10:30")).isEqualTo("10 heures 30");
        assertThat(LiquidationCorpusCommun.heure("09H00")).isEqualTo("9 heures");
        assertThat(LiquidationCorpusCommun.heure("")).isNull();
    }

    @Test
    void ancienne_valeur_proposee_gratuite_vaut_non_remuneree() {
        Map<String, Object> p = fixture("dissolution.json");
        Map<String, Object> l = bloc(p, "liquidateur");
        l.remove("remunerationApplicable");
        l.put("remuneration", "exerc\u00e9es \u00e0 titre gratuit");
        assertThat(mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p)).containsEntry("LIQUIDATEUR_REMUNERATION_APPLICABLE", "non");
    }

    private void refuse(String variable, Consumer<Map<String, Object>> modification) {
        Map<String, Object> p = fixture("dissolution.json");
        modification.accept(p);
        assertThatThrownBy(() -> mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", p))
                .isInstanceOf(GenerationRefuseeException.class)
                .satisfies(e -> assertThat(((GenerationRefuseeException) e).donnees())
                        .extracting(GenerationRefuseeException.DonneeManquante::variable).contains(variable));
    }

    @Test
    void donnees_pilotes_jamais_supposees() {
        refuse("QUORUM_ATTEINT", p -> p.remove("quorumAtteint"));
        refuse("DISSOLUTION_MOTIF", p -> bloc(p, "dissolution").remove("nature"));
        refuse("CAC_EXISTE", p -> bloc(p, "mentionsPv").remove("cacExiste"));
        refuse("GERANT_QUITUS_DONNE", p -> bloc(p, "mentionsPv").remove("quitusGerance"));
        refuse("SALARIES_DIX_OU_PLUS", p -> bloc(p, "mentionsPv").remove("salariesDixOuPlus"));
        refuse("LIQUIDATEUR_REMUNERATION_APPLICABLE", p -> bloc(p, "liquidateur").remove("remunerationApplicable"));
        // Liquidateur qui n'est pas le gerant en fonction : a-t-il ete gerant ? saisi.
        refuse("LIQUIDATEUR_EST_ANCIEN_GERANT", p -> p.put("gerants",
                List.of(Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani"))));
        refuse("CAC_MANDAT_LIQUIDATION", p -> {
            bloc(p, "mentionsPv").put("cacExiste", "oui");
            bloc(p, "mentionsPv").put("cacPresent", "non");
        });
        // Deux gerants dont l'un devient liquidateur, quitus donne : une phrase serait fausse.
        refuse("GERANT_NOM", p -> p.put("gerants", List.of(
                Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui"),
                Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani"))));
    }

    @Test
    void annonce_inchangee() {
        Map<String, Object> v = mapper.map("ANNONCE_LEGALE_DISSOLUTION_SARL", fixture("dissolution.json"));
        assertThat(v).containsEntry("DENOMINATION", "PARACOSME").containsKey("LIQUIDATEUR_NOM");
    }
}
