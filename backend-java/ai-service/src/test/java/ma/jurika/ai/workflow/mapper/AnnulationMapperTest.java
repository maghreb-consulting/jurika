package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4a (E5, RG-TKT-04) : les documents d'annulation, servis par la charge du ticket.
 * Avant : aucun parcours ne les produisait avec les faits du ticket (statut atteint, date,
 * depots) ; la creation les rendait avec ses seules donnees, marques "VALEUR MANQUANTE".
 */
class AnnulationMapperTest {

    private final AnnulationMapper mapper = new AnnulationMapper();

    private static Map<String, Object> charge(List<Map<String, Object>> demarches, Object rang) {
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("DOSSIER_NUMERO", "T-2026-00042");
        variables.put("DOSSIER_DATE_ANNULATION", "2026-10-05");
        variables.put("DEMARCHES_INTERROMPUES", demarches);
        Map<String, Object> p = new java.util.LinkedHashMap<>();
        p.put("variables", variables);
        p.put("societe", Map.of("denomination", "PARACOSME", "formeJuridique", "SARL_AU", "rcNumero", "456789"));
        p.put("gerants", List.of(Map.of("civilite", "Mme", "prenom", "Salma", "nom", "BENNANI")));
        if (rang != null) p.put("retraitIndex", rang);
        return p;
    }

    private static Map<String, Object> demarche(String administration, String reference) {
        return Map.of("DEMARCHE_ADMINISTRATION", administration, "DEMARCHE_DATE_DEPOT", "2026-09-20",
                "DEMARCHE_REFERENCE", reference, "RETRAIT_OBJET", "Retrait d'une demande en cours");
    }

    @Test
    void la_note_reprend_les_faits_du_ticket_et_l_identite_de_la_fiche() {
        Map<String, Object> v = mapper.map("NOTE_ANNULATION_DOSSIER_SOCIETE_EXISTANTE", charge(List.of(), null));
        assertThat(v).containsEntry("DOSSIER_NUMERO", "T-2026-00042")
                .containsEntry("DOSSIER_DATE_ANNULATION", "05/10/2026")
                .containsEntry("DENOMINATION", "PARACOSME")
                .containsEntry("RC_NUMERO", "456789")
                .containsEntry("FORME_JURIDIQUE",
                        "Soci\u00e9t\u00e9 \u00e0 responsabilit\u00e9 limit\u00e9e \u00e0 associ\u00e9 unique")
                .containsEntry("GERANT_ACCORD", "un f\u00e9minin");
    }

    @Test
    void une_lettre_par_demarche_interrompue() {
        List<Map<String, Object>> deux = List.of(demarche("Greffe de Casablanca", "DL-1"), demarche("DGI", "TP-2"));
        Map<String, Object> v = mapper.map("LETTRE_RETRAIT_DEPOT_SOCIETE_EXISTANTE", charge(deux, 1));
        assertThat(v).containsEntry("RETRAIT_ADMINISTRATION_DESIGNATION", "DGI")
                .containsEntry("RETRAIT_DEPOT_REFERENCE", "TP-2")
                .containsEntry("RETRAIT_DEPOT_DATE", "20/09/2026")
                .containsEntry("RETRAIT_OBJET", "Retrait d'une demande en cours");
        // Plusieurs demarches sans rang : aucune lettre choisie a la place de l'employe.
        assertThatThrownBy(() -> mapper.map("LETTRE_RETRAIT_DEPOT", charge(deux, null)))
                .isInstanceOf(GenerationRefuseeException.class);
    }

    @Test
    void sans_demarche_interrompue_pas_de_lettre() {
        assertThatThrownBy(() -> mapper.map("LETTRE_RETRAIT_DEPOT", charge(List.of(), null)))
                .isInstanceOf(GenerationRefuseeException.class)
                .hasMessageContaining("d\u00e9marche");
    }

    @Test
    void la_civilite_inconnue_ne_donne_pas_d_accord() {
        Map<String, Object> p = charge(List.of(), null);
        p.put("gerants", List.of(Map.of("prenom", "Sam", "nom", "IDRISSI")));
        assertThat(mapper.map("NOTE_ANNULATION_DOSSIER", p)).doesNotContainKey("GERANT_ACCORD");
    }

    @Test
    void les_pieces_sont_numerotees_et_totalisees_seulement_si_chaque_quantite_est_saisie() {
        Map<String, Object> p = charge(List.of(), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) p.get("variables");
        variables.put("PIECES_REMISES", List.of(Map.of("PIECE_DESIGNATION", "Certificat", "PIECE_NOMBRE", "2"),
                Map.of("PIECE_DESIGNATION", "Statuts", "PIECE_NOMBRE", "3")));
        Map<String, Object> v = mapper.map("BORDEREAU_REMISE_DOSSIER", p);
        assertThat(v).containsEntry("PIECES_NOMBRE_TOTAL", "5");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pieces = (List<Map<String, Object>>) v.get("PIECES_REMISES");
        assertThat(pieces).extracting(it -> it.get("PIECE_NUMERO")).containsExactly("1", "2");
        variables.put("PIECES_REMISES", List.of(Map.of("PIECE_DESIGNATION", "Certificat")));
        assertThat(mapper.map("BORDEREAU_REMISE_DOSSIER", p)).doesNotContainKey("PIECES_NOMBRE_TOTAL");
    }
}
